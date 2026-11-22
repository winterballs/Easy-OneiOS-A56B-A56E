/*
 * Copyright 2023 Advanced Micro Devices, Inc
 * SPDX-License-Identifier: MIT
 */

#ifndef AMDGPU_RENDERER_H_
#define AMDGPU_RENDERER_H_

#include "config.h"

#include <inttypes.h>
#include <stddef.h>
#include <stdint.h>
#include <time.h>

#include "pipe/p_defines.h"

#include "amdgpu_drm.h"
#include "drm_hw.h"

struct virgl_context;
struct xh_gpu_pool;
int amdgpu_renderer_set_response_pool(struct virgl_context *vctx, struct xh_gpu_pool *pool);

int amdgpu_renderer_probe(int fd, struct virgl_renderer_capset_drm *capset);

struct virgl_context *amdgpu_renderer_create(int fd, size_t debug_len, const char *debug_name);

#endif /* AMDGPU_RENDERER_H_ */
