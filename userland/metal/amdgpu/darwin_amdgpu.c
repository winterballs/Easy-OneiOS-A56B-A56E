/* SPDX-License-Identifier: MIT */
#include "darwin_amdgpu.h"
#include "DroidVMVirtgpuABI.h"

#include <IOKit/IOKitLib.h>
#include <errno.h>
#include <pthread.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

enum {
    TunnelMagic = 0x4e544e4c, DrmCapset = 6, AmdgpuContext = 2,
    QueryInfo = 1, ShmemSize = 16384,
};

struct oneios_amdgpu {
    io_connect_t connection;
    uint32_t selector, seqno;
    volatile uint32_t *shmem;
    pthread_mutex_t lock;
    int unusable;
};

struct query_packet {
    struct droidvm_virtgpu_execbuffer exec;
    uint32_t command, length, seqno, response_offset;
    uint64_t return_pointer;
    uint32_t return_size, query, arguments[4];
};

_Static_assert(sizeof(struct query_packet) == 64, "AMDGPU QUERY_INFO ABI");
_Static_assert(offsetof(struct query_packet, return_pointer) == 32, "AMDGPU info alignment");

static int
call(struct oneios_amdgpu *gpu, uint32_t op, const void *input, size_t length,
     void *output, size_t capacity)
{
    uint32_t packet[2 + sizeof(struct query_packet) / 4] = {
        TunnelMagic, DROIDVM_VIRTGPU_METHOD + op,
    };
    if (length > sizeof(packet) - 8)
        return -EINVAL;
    memcpy(packet + 2, input, length);
    size_t written = capacity;
    kern_return_t status = IOConnectCallStructMethod(gpu->connection, gpu->selector,
        packet, 8 + length, output, &written);
    if (status != KERN_SUCCESS || written != capacity)
        return -EIO;
    return 0;
}

void
oneios_amdgpu_close(struct oneios_amdgpu *gpu)
{
    if (!gpu)
        return;
    if (gpu->connection)
        IOServiceClose(gpu->connection);
    pthread_mutex_destroy(&gpu->lock);
    free(gpu);
}

int
oneios_amdgpu_open(struct oneios_amdgpu **out)
{
    if (!out)
        return -EINVAL;
    *out = NULL;
    struct oneios_amdgpu *gpu = calloc(1, sizeof(*gpu));
    if (!gpu)
        return -ENOMEM;
    int error = pthread_mutex_init(&gpu->lock, NULL);
    if (error) {
        free(gpu);
        return -error;
    }

    error = -ENODEV;
    io_service_t service = IOServiceGetMatchingService(kIOMainPortDefault,
        IOServiceMatching("IOAcceleratorES"));
    if (!service)
        goto fail;
    kern_return_t status = IOServiceOpen(service, mach_task_self(),
        DROIDVM_VIRTGPU_OPEN_TYPE, &gpu->connection);
    IOObjectRelease(service);
    if (status != KERN_SUCCESS)
        goto fail;

    const uint32_t probe[2] = {TunnelMagic, UINT32_MAX};
    for (; gpu->selector < 256; ++gpu->selector) {
        if (IOConnectCallStructMethod(gpu->connection, gpu->selector,
                probe, sizeof(probe), NULL, NULL) == KERN_SUCCESS)
            break;
    }
    if (gpu->selector == 256)
        goto fail;

    const struct droidvm_virtgpu_get_caps caps = {DrmCapset, 0};
    uint32_t capset[5];
    error = call(gpu, DROIDVM_VIRTGPU_GET_CAPS, &caps, sizeof(caps),
        capset, sizeof(capset));
    if (error)
        goto fail;
    if (capset[0] != 0 || capset[4] != AmdgpuContext) {
        error = -ENOTSUP;
        goto fail;
    }

    const struct droidvm_virtgpu_context_init context = {DrmCapset, 64};
    struct droidvm_virtgpu_context_init_reply ctx;
    error = call(gpu, DROIDVM_VIRTGPU_CONTEXT_INIT, &context, sizeof(context),
        &ctx, sizeof(ctx));
    if (error)
        goto fail;
    const struct droidvm_virtgpu_create_blob blob = {
        DROIDVM_VIRTGPU_BLOB_MEM_HOST3D, DROIDVM_VIRTGPU_BLOB_FLAG_MAPPABLE,
        ShmemSize, 0, 0, 0,
    };
    struct droidvm_virtgpu_create_blob_reply resource;
    error = call(gpu, DROIDVM_VIRTGPU_CREATE_BLOB, &blob, sizeof(blob),
        &resource, sizeof(resource));
    if (error)
        goto fail;
    const struct droidvm_virtgpu_handle handle = {resource.handle, 0};
    struct droidvm_virtgpu_map_reply map;
    error = call(gpu, DROIDVM_VIRTGPU_MAP, &handle, sizeof(handle), &map, sizeof(map));
    if (error)
        goto fail;
    if (!map.address || (map.address & 3) || map.size < ShmemSize) {
        error = -EPROTO;
        goto fail;
    }
    gpu->shmem = (volatile uint32_t *)(uintptr_t)map.address;
    *out = gpu;
    return 0;

fail:
    oneios_amdgpu_close(gpu);
    return error;
}

int
oneios_amdgpu_query(struct oneios_amdgpu *gpu, uint32_t query,
                   const uint32_t args[4], void *result, uint32_t size)
{
    if (!gpu || !result || !size || size > ShmemSize - 16)
        return -EINVAL;

    pthread_mutex_lock(&gpu->lock);
    int error = -EIO;
    if (gpu->unusable)
        goto done;

    const uint32_t offset = gpu->shmem[1];
    const uint32_t response_size = 8 + size;
    if (offset < 8 || (offset & 3) || offset > ShmemSize - response_size) {
        error = -EPROTO;
        goto done;
    }

    if (gpu->seqno == UINT32_MAX) {
        error = -EOVERFLOW;
        goto done;
    }
    const uint32_t seqno = ++gpu->seqno;
    struct query_packet request = {
        .exec.size = sizeof(request) - sizeof(request.exec),
        .command = QueryInfo,
        .length = sizeof(request) - sizeof(request.exec),
        .seqno = seqno,
        .return_size = size,
        .query = query,
    };
    if (args)
        memcpy(request.arguments, args, sizeof(request.arguments));

    volatile uint32_t *response = gpu->shmem + offset / 4;
    response[0] = response_size;
    response[1] = (uint32_t)-EIO;
    __atomic_thread_fence(__ATOMIC_RELEASE);
    struct droidvm_virtgpu_execbuffer_reply reply;
    error = call(gpu, DROIDVM_VIRTGPU_EXECBUFFER, &request, sizeof(request),
        &reply, sizeof(reply));
    if (error) {
        gpu->unusable = 1;
        goto done;
    }

    for (unsigned wait = 0; gpu->shmem[0] != seqno; ++wait) {
        if (wait == 1000) {
            gpu->unusable = 1;
            error = -ETIMEDOUT;
            goto done;
        }
        usleep(1000);
    }
    __atomic_thread_fence(__ATOMIC_ACQUIRE);
    if (response[0] != response_size) {
        error = -EPROTO;
        goto done;
    }
    error = (int32_t)response[1];
    if (!error) {
        const volatile uint8_t *payload = (const volatile uint8_t *)(response + 2);
        for (uint32_t i = 0; i < size; ++i)
            ((uint8_t *)result)[i] = payload[i];
    }

done:
    pthread_mutex_unlock(&gpu->lock);
    return error;
}
