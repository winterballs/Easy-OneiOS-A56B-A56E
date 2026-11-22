/* SPDX-License-Identifier: MIT */
#include "darwin_amdgpu.h"
#include "amdgpu_drm.h"
#include <inttypes.h>
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>

int main(void)
{
    int console = open("/dev/console", O_WRONLY);
    if (console >= 0) {
        dup2(console, STDOUT_FILENO);
        dup2(console, STDERR_FILENO);
        if (console > STDERR_FILENO)
            close(console);
    }
    struct oneios_amdgpu *gpu;
    int error = oneios_amdgpu_open(&gpu);
    if (error) {
        fprintf(stderr, "AMDGPU VirtIO open: %d\n", error);
        return 1;
    }

    struct drm_amdgpu_info_device device = {0};
    error = oneios_amdgpu_query(gpu, AMDGPU_INFO_DEV_INFO, NULL, &device, sizeof(device));
    if (!error) {
        struct drm_amdgpu_info_hw_ip gfx = {0};
        const uint32_t args[4] = {AMDGPU_HW_IP_GFX, 0, 0, 0};
        error = oneios_amdgpu_query(gpu, AMDGPU_INFO_HW_IP_INFO, args, &gfx, sizeof(gfx));
        if (!error && gfx.available_rings && device.virtual_address_max > device.virtual_address_offset) {
            printf("device=%04x gfx=%u.%u rings=%#x va=%#" PRIx64 "..%#" PRIx64 "\n",
                   device.device_id, gfx.hw_ip_version_major, gfx.hw_ip_version_minor,
                   gfx.available_rings, (uint64_t)device.virtual_address_offset,
                   (uint64_t)device.virtual_address_max);
        } else if (!error) {
            fprintf(stderr, "AMDGPU reported no GFX rings or usable virtual address range\n");
            error = -1;
        }
    }
    oneios_amdgpu_close(gpu);
    if (error)
        fprintf(stderr, "AMDGPU device discovery: %d\n", error);
    return error != 0;
}
