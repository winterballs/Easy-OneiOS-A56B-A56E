/* SPDX-License-Identifier: MIT */
#ifndef ONEIOS_DARWIN_AMDGPU_H
#define ONEIOS_DARWIN_AMDGPU_H

#include <stdint.h>

struct oneios_amdgpu;

/* Owns one IOKit connection and one VirtIO DRM response buffer. */
int oneios_amdgpu_open(struct oneios_amdgpu **out);
void oneios_amdgpu_close(struct oneios_amdgpu *gpu);

/* Linux AMDGPU_INFO query ID and its four optional argument words.
 * Returns zero or a negative errno, including the host's ioctl error. */
int oneios_amdgpu_query(struct oneios_amdgpu *gpu, uint32_t query,
                        const uint32_t args[4], void *result, uint32_t size);
#endif
