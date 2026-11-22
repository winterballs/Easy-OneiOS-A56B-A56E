$n="$env:LOCALAPPDATA\Android\Sdk\ndk\27.2.12479018\toolchains\llvm\prebuilt\windows-x86_64\bin"
$v=(Resolve-Path work/virgl-android).Path
$d=(Resolve-Path work/libdrm-2.4.133).Path
$r=(Resolve-Path work/OneiOS/runtime/xnuhost).Path
& "$n/clang.exe" --target=aarch64-linux-android30 -std=gnu11 -O2 -D_GNU_SOURCE -DHAVE_CONFIG_H=1 -DENABLE_DRM_AMDGPU=1 -imacros "$r/drm2kgsl/config.h" "-I$r/src" "-I$r/drm2kgsl" "-I$v/src" "-I$v/src/drm" "-I$v/src/drm/drm-uapi" "-I$v/src/mesa" "-I$v/src/mesa/pipe" "-I$v/src/mesa/util" "-I$v/src/mesa/compat" "-I$v/src/gallium/include" "-I$v/src/gallium/auxiliary" "-I$v/src/gallium/auxiliary/util" "-I$d" "-I$d/amdgpu" "-I$d/include/drm" -c "$v/src/drm/amdgpu/amdgpu_renderer.c" -o work/amdgpu_renderer.o
if($LASTEXITCODE -eq 0){& "$n/llvm-ar.exe" rcs work/liboneios-amdgpu.a work/amdgpu_renderer.o}
