// SPDX-License-Identifier: MIT
// Xclipse native submission smoke test, using the same DMA-BUF exporter as VirtIO.
#define _GNU_SOURCE
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <time.h>
#include <unistd.h>
#include <drm/amdgpu_drm.h>
#include "gpu3d.h"
#include "gpu_pool.h"

#define TRY(call) do { if ((call) < 0) { perror(#call); goto done; } } while (0)

int xh_gpu3d_xclipse_selftest(void)
{
    const unsigned page_size = 4096;
    const uint64_t physical = 0x2000000000;
    int result = 1, ram = -1, dma = -1, fd = -1;
    uint32_t *words = MAP_FAILED;
    xh_gpu_pool pool = {0};
    xh_gpu_pool_mapping response = {0};
    ram = memfd_create("oneios-amdgpu-ib", MFD_CLOEXEC | MFD_ALLOW_SEALING);
    TRY(ram);
    TRY(ftruncate(ram, 2 * page_size));
    TRY(fcntl(ram, F_ADD_SEALS, F_SEAL_SHRINK));
    TRY(xh_gpu_pool_init(&pool, ram, page_size, page_size));
    TRY(xh_gpu_pool_map(&pool, page_size, &response));
    uint32_t sequence = 0x540;
    *(uint32_t *)response.address = sequence;
    uint32_t guest_sequence;
    off_t response_offset = page_size + response.offset;
    if (pread(ram, &guest_sequence, sizeof guest_sequence, response_offset) !=
        sizeof guest_sequence || guest_sequence != sequence) {
        fputs("VirtIO response is not visible in guest RAM\n", stderr);
        goto done;
    }
    TRY(xh_gpu_pool_unmap(&pool, &response));
    TRY(xh_gpu_pool_map(&pool, page_size, &response));
    if (response.offset != 0 || *(uint32_t *)response.address != 0) {
        fputs("Reused VirtIO response range was not cleared\n", stderr);
        goto done;
    }
    puts("PASS VirtIO response pool shares guest RAM and clears recycled ranges");
    words = mmap(NULL, page_size, PROT_READ | PROT_WRITE, MAP_SHARED, ram, 0);
    if (words == MAP_FAILED) { perror("mmap"); goto done; }
    /* GFX10 PACKET2_NOP, eight dwords to meet IB alignment. */
    for (unsigned i = 0; i < 8; ++i) words[i] = 0x80000000;
    const xh_gpu_entry entry = {.addr = physical, .length = page_size};
    dma = xh_gpu3d_udmabuf(ram, physical, page_size, &entry, 1);
    TRY(dma);
    fd = open("/dev/dri/renderD128", O_RDWR | O_CLOEXEC);
    TRY(fd);
    struct drm_prime_handle imported = {.fd = dma};
    TRY(ioctl(fd, DRM_IOCTL_PRIME_FD_TO_HANDLE, &imported));
    struct drm_amdgpu_info_device device = {0};
    struct drm_amdgpu_info query = {
        .query = AMDGPU_INFO_DEV_INFO, .return_pointer = (uintptr_t)&device, .return_size = sizeof device
    };
    TRY(ioctl(fd, DRM_IOCTL_AMDGPU_INFO, &query));
    uint64_t va = device.virtual_address_offset;
    if (!va || device.virtual_address_max < page_size || va > device.virtual_address_max - page_size) goto done;
    struct drm_amdgpu_gem_va mapping = {
        .handle = imported.handle, .operation = AMDGPU_VA_OP_MAP,
        .flags = AMDGPU_VM_PAGE_READABLE | AMDGPU_VM_PAGE_EXECUTABLE,
        .va_address = va, .map_size = page_size
    };
    TRY(ioctl(fd, DRM_IOCTL_AMDGPU_GEM_VA, &mapping));
    puts("PASS mapped imported guest memory into Xclipse GPU VA");
    union drm_amdgpu_ctx context = {.in = {.op = AMDGPU_CTX_OP_ALLOC_CTX}};
    TRY(ioctl(fd, DRM_IOCTL_AMDGPU_CTX, &context));
    uint32_t ctx = context.out.alloc.ctx_id;
    struct drm_amdgpu_bo_list_entry entry_bo = {.bo_handle = imported.handle};
    union drm_amdgpu_bo_list list = {.in = {.operation = AMDGPU_BO_LIST_OP_CREATE,
        .bo_number = 1, .bo_info_size = sizeof entry_bo, .bo_info_ptr = (uintptr_t)&entry_bo}};
    TRY(ioctl(fd, DRM_IOCTL_AMDGPU_BO_LIST, &list));
    struct drm_amdgpu_cs_chunk_ib ib = {.va_start = va, .ib_bytes = 32, .ip_type = AMDGPU_HW_IP_GFX};
    struct drm_amdgpu_cs_chunk chunk = {.chunk_id = AMDGPU_CHUNK_ID_IB,
        .length_dw = sizeof ib / 4, .chunk_data = (uintptr_t)&ib};
    uint64_t chunk_ptr = (uintptr_t)&chunk;
    union drm_amdgpu_cs submit = {.in = {.ctx_id = ctx, .bo_list_handle = list.out.list_handle,
        .num_chunks = 1, .chunks = (uintptr_t)&chunk_ptr}};
    TRY(ioctl(fd, DRM_IOCTL_AMDGPU_CS, &submit));
    printf("PASS submitted native AMDGPU command buffer, sequence %llu\n", (unsigned long long)submit.out.handle);
    struct timespec now;
    TRY(clock_gettime(CLOCK_MONOTONIC, &now));
    uint64_t deadline = (uint64_t)now.tv_sec * 1000000000 + now.tv_nsec + 1000000000;
    union drm_amdgpu_wait_cs wait = {.in = {.handle = submit.out.handle, .ctx_id = ctx,
        .ip_type = AMDGPU_HW_IP_GFX, .timeout = deadline}};
    TRY(ioctl(fd, DRM_IOCTL_AMDGPU_WAIT_CS, &wait));
    if (wait.out.status) { fputs("GPU fence timed out\n", stderr); goto done; }
    puts("PASS native AMDGPU fence completed");
    result = 0;
done:
    if (response.address) xh_gpu_pool_unmap(&pool, &response);
    if (fd >= 0) close(fd);
    if (dma >= 0) close(dma);
    if (words != MAP_FAILED) munmap(words, page_size);
    if (ram >= 0) close(ram);
    return result;
}
