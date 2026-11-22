// SPDX-License-Identifier: MIT
#ifndef XNUHOST_GPU_POOL_H
#define XNUHOST_GPU_POOL_H

#include <stddef.h>
#include <stdint.h>

typedef struct xh_gpu_pool_mapping {
    uint32_t offset;  // Relative to drm-host-pool, as returned by MAP_BLOB.
    size_t length;
    void *address;
    struct xh_gpu_pool_mapping *next;
} xh_gpu_pool_mapping;

typedef struct xh_gpu_pool {
    int fd;           // Borrowed guest RAM memfd.
    uint64_t offset;  // Offset of drm-host-pool in the memfd.
    uint64_t size;
    size_t page;
    xh_gpu_pool_mapping *mappings;
} xh_gpu_pool;

// Call from the virtio-gpu control thread. Each mapping must stay alive until unmap.
int xh_gpu_pool_init(xh_gpu_pool *pool, int fd, uint64_t offset, uint64_t size);
int xh_gpu_pool_map(xh_gpu_pool *pool, size_t needed, xh_gpu_pool_mapping *mapping);
int xh_gpu_pool_unmap(xh_gpu_pool *pool, xh_gpu_pool_mapping *mapping);

#endif

