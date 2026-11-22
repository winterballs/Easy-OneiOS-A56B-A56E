# AMDGPU renderer for the Galaxy A56's Xclipse GPU

The Galaxy A56's GPU is a Samsung Xclipse (AMD RDNA, Linux `amdgpu` driver), not an Adreno, so drm2kgsl
(`docs/gpu.md` section 2) does not apply to it. This folder holds the host side of an AMDGPU path. It was written in
a Codex session on 2026-11-13/14 outside this repository and brought in on 2026-11-21. It is the renderer in the
phone's xnuhost of 2026-11-16 (sha256 `1aa5b6e9…`, the one with `--selftest-xclipse`).

| Path | What |
|---|---|
| `src/amdgpu_renderer.c`, `.h`, `amdgpu_virtio_proto.h` | a virglrenderer DRM native context for `amdgpu` (the `VIRTGPU_DRM_CONTEXT_AMDGPU` wire protocol): device open from the render node (`ONEIOS_AMDGPU_RENDER_NODE`), BO alloc/import/VA map, `amdgpu_cs_ctx_create2`, command submission, fences. Uses libdrm 2.4.133's `amdgpu` |
| `patches/OneiOS-AMDGPU-renderer-Android.patch` | the virglrenderer changes for Android: `src/drm/drm_context.c/.h` and the renderer |
| `patches/OneiOS-AMDGPU-response-pool.patch` | the response pool shared with `../src/gpu_pool.c` |
| `build-amdgpu-android.ps1` | how the archive was built on Windows (NDK 27.2, `aarch64-linux-android30`): compiles `amdgpu_renderer.c` against a virgl-android checkout and libdrm 2.4.133 into `liboneios-amdgpu.a`. Kept as the record of the build; paths are the Codex session's (`work/virgl-android`, `work/libdrm-2.4.133`). The virgl-android checkout and libdrm are not vendored here |
| `build-xnuhost-amdgpu.ps1` | the matching xnuhost build (Windows) |

In xnuhost (`runtime/xnuhost`):

- `src/gpu3d_xclipse.c`: `xh_gpu3d_xclipse_selftest()`, `xnuhost --selftest-xclipse`: a native submission
  smoke test that exports guest-like memory through the same DMA-BUF path as virtio and runs it on the GPU.
- `src/gpu_pool.c`, `.h`: the guest-visible response pool.
- `Makefile`: the archive is linked only when `AMDGPU_RENDERER_LIBS` (and `AMDGPU_RENDERER_CPPFLAGS`) are given;
  then `gpu3d_kgsl.o` is built with `-DXH_AMDGPU=1` and virgl with `-DENABLE_DRM_AMDGPU=1`. Without them the build
  is the usual drm2kgsl/2D one.

Guest side: `userland/metal/amdgpu/` (`darwin_amdgpu.c`, `probe.c`, `build-probe.sh`): an arm64e probe that opens
the virtio GPU user client from iOS and submits through this renderer.

## How far it got (Codex logs of 2026-11-13/14, not re-run since)

| Test | Result |
|---|---|
| host: native AMDGPU command buffer, fence, guest memory mapped into the Xclipse GPU VA | PASS |
| guest kernel → virtio → renderer: AMDGPU GFX NOP, fence completed | PASS |
| iOS 16.5 UI with `vgpu-3d=1` | black screen |

Not done: a Vulkan driver for iOS (Darwin) that speaks this protocol, the part Turnip plays for the Adreno path
(`docs/gpu.md` section 7), and Metal on top of it. `radv-xclipse` from the same session is RADV for **Android**, not
Darwin, and is not here.

Not checked: whether the files here still build. The phone's 2026-11-21 sessions use `--gpu 2d`.
