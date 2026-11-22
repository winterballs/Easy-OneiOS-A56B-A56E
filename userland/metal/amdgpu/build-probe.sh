#!/usr/bin/env bash
# Build a standalone arm64e probe for a guest with the VirtIO user client enabled.
set -euo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo=$(cd "$here/../../.." && pwd)
IOS_DIR=$repo/scripts/ios
. "$IOS_DIR/env.sh"
out=${1:-$repo/out/userland/oneios-amdgpu-probe}
mkdir -p "$(dirname "$out")"
"$repo/scripts/ios/ios-clang" -std=c11 -D_DARWIN_C_SOURCE -O2 -Wall -Wextra -Werror \
    -I "$repo/external/NativeAdreno" -I "$repo/external/mesa-droidvm/include/drm-uapi" \
    "$here/darwin_amdgpu.c" "$here/probe.c" -framework IOKit -o "$out"
python3 "$repo/scripts/ios/ios-sign.py" "$out"
