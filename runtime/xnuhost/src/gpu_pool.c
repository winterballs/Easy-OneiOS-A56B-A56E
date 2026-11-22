// SPDX-License-Identifier: MIT
// Host-visible VirtIO response buffers backed by the guest RAM memfd.
#define _GNU_SOURCE
#include "gpu_pool.h"

#include <errno.h>
#include <limits.h>
#include <stdint.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>

int xh_gpu_pool_init(xh_gpu_pool *pool, int fd, uint64_t offset, uint64_t size)
{
    long page = sysconf(_SC_PAGESIZE);
    if (fd < 0 || page <= 0 || !size || size > UINT32_MAX ||
        offset > INT64_MAX - size || offset % page || size % page)
        return -EINVAL;

    *pool = (xh_gpu_pool){
        .fd = fd, .offset = offset, .size = size, .page = (size_t)page
    };
    return 0;
}

int xh_gpu_pool_map(xh_gpu_pool *pool, size_t needed, xh_gpu_pool_mapping *mapping)
{
    if (!pool->page || !needed || needed > pool->size)
        return -EINVAL;
    uint64_t length = ((uint64_t)needed + pool->page - 1) / pool->page * pool->page;
    uint64_t offset = 0;
    xh_gpu_pool_mapping **slot = &pool->mappings;
    while (*slot) {
        if (length <= (*slot)->offset - offset)
            break;
        offset = (*slot)->offset + (*slot)->length;
        slot = &(*slot)->next;
    }
    if (length > pool->size - offset)
        return -ENOSPC;

    void *address = mmap(NULL, length, PROT_READ | PROT_WRITE, MAP_SHARED,
                         pool->fd, (off_t)(pool->offset + offset));
    if (address == MAP_FAILED)
        return -errno;

    memset(address, 0, length);
    *mapping = (xh_gpu_pool_mapping){
        .offset = (uint32_t)offset, .length = (size_t)length,
        .address = address, .next = *slot
    };
    *slot = mapping;
    return 0;
}

int xh_gpu_pool_unmap(xh_gpu_pool *pool, xh_gpu_pool_mapping *mapping)
{
    xh_gpu_pool_mapping **slot = &pool->mappings;
    while (*slot && *slot != mapping)
        slot = &(*slot)->next;
    if (!*slot)
        return -ENOENT;
    if (munmap(mapping->address, mapping->length) != 0)
        return -errno;
    *slot = mapping->next;
    *mapping = (xh_gpu_pool_mapping){0};
    return 0;
}

