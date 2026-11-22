$env:Path="C:\msys64\mingw64\bin;C:\msys64\usr\bin;$env:Path"
$ndk=("$env:LOCALAPPDATA/Android/Sdk/ndk/27.2.12479018" -replace '\\','/')
$base=((Resolve-Path work).Path -replace '\\','/')
mingw32-make -j6 -C work/OneiOS/runtime/xnuhost SHELL=C:/msys64/usr/bin/bash.exe NDK_HOST=windows-x86_64 "NDK=$ndk" "ANDROID_CC=$ndk/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe --target=aarch64-linux-android30" "VIRGL=$base/virgl-android" "AMDGPU_RENDERER_LIBS=$base/liboneios-amdgpu.a $base/libdrm-build/amdgpu/libdrm_amdgpu.a $base/libdrm-build/libdrm.a" "AMDGPU_RENDERER_CPPFLAGS=-I$base/libdrm-2.4.133/amdgpu -I$base/virgl-android/src/drm/drm-uapi" build/android/xnuhost *> work/amdgpu-full-build.log
$rc=$LASTEXITCODE
Get-Content work/amdgpu-full-build.log -Tail 15
exit $rc
