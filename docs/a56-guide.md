# OneiOS on Samsung Galaxy A56 5G (SM-A566E / a56x)

Complete technical documentation for running optimized iOS 16.5 userland on top of the Samsung Galaxy A56 5G (Exynos 1580 / AMD RDNA3).

---

## 1. Hardware Architecture

| Component | Galaxy A56 5G (`SM-A566E`) | Notes |
| :--- | :--- | :--- |
| **SoC** | Samsung Exynos 1580 (`s5e8855` / `erd8855`) | 4nm EUV FinFET |
| **CPU Cluster** | 1x Cortex-X4 @ 2.91 GHz (policy7)<br>3x Cortex-A720 @ 2.60 GHz (policy4)<br>4x Cortex-A520 @ 1.95 GHz (policy0) | 8 vCPUs mapped to XNU |
| **GPU** | Samsung Xclipse 540 (AMD RDNA3) | Exposed via `/dev/dri/card0` and `/dev/dri/renderD128` |
| **Display** | 1080×2340 @ 120Hz Super AMOLED | iOS 16.5: guest frame 1080×2340 `@3x` (360×780 points). At that width UIKit has no status bar visual provider and shows "Please see rdar://45025538". iPhone X's 1125×2436 (375×812 points) was tried on 2026-11-18: SpringBoard aborted in a loop (SIGABRT every ~11 s, Apple logo), cause open. Its unified log has no abort text; each SpringBoard lives about 0.3 s, and the last lines are CoreGraphics `invalid context 0x0` errors (CGContextSaveGState, FillRects, ScaleCTM, ...) while the cover sheet and its wallpaper are presented, after a UIKit assert "Unable to find coordinate space for scene". Guess, not checked: a bitmap context (or its IOSurface) at the new size fails to be created. `oneios_switch_a56.sh --inner` passes `--display shm --gpu 2d --width 1080 --height 2340 --hz 60 --scale 3` for 20F66 (60 Hz since 2026-11-21; 720x1560 @2x was tried that day and panicked the kernel in the display probe); `ONEIOS_FRAME=WxH` or `ONEIOS_FRAME=panel` overrides the frame |
| **Touchscreen** | `/dev/input/event4` (`sec_touchscreen`) | Event driver via `oneios-hid` |
| **Knox State** | 0x0 (`verifiedbootstate: green`, `warranty_bit: 0`) | **Zero Knox tripping** |

---

## 2. Key Differences from Snapdragon (S24 Ultra)

1. **GPU Driver Path:**
   - **Snapdragon:** Uses Qualcomm proprietary `/dev/kgsl-3d0` node and Droid-VM `drm2kgsl` with Turnip.
   - **Exynos 1580 (A56):** Uses Linux standard Direct Rendering Manager (DRM) `amdgpu` driver (v3.49.0) over `/dev/dri/renderD128`.
   - PRIME dma-buf buffer sharing is supported (`DRM_CAP_PRIME: 0x3` - import & export).
   - Test tool: `tools/test_a56_gpu` validates udmabuf import into GEM handles.
   - Built-in bridge: `xnuhost` compiled with `XH_AMDGPU=1` dynamically targets `/dev/dri/renderD128` via `ONEIOS_AMDGPU_RENDER_NODE`.
   - **iOS 16.5 draws on the CPU for now.** 20F66's stock Metal opens `AGXAccelerator` as an IOGPU device client (type 1), which the NativeAdreno driver does not serve (`AGXAccelerator type 0x1 -> 0xe00002c7` in the guest log); with the accelerator published, `backboardd` had a Metal device that could not draw and the panel stayed black. For an image whose `image.conf` says `build=20F66`, `oneios_switch.sh --inner` therefore starts xnuhost with `--boot-args "<the image's boot-args> oneios-gpu-metal=0"`: no accelerator is published and CoreAnimation composites on the CPU (`docs/gpu.md`). The guest log says `oneios-adreno: no Metal accelerator` and the session log `oneios inner: Metal accelerator: oneios-gpu-metal=0`. `ONEIOS_GPU_METAL=1` (or `0`) in the switch's environment forces either way. New images get the same from roottweak (`userland/roottweak/cli.py`, 20F66's `boot-args`).
   - **That alone does not light the panel (phone, 2026-11-17).** A session without the accelerator (`oneios-gpu-metal=0`, `--gpu 2d`) with `oneios-iouc=backboardd oneios-display-report=15`: backboardd opens the framebuffer and calls `swap_begin` (IOMobileFramebufferUserClient selector 4, `0x0`, over 4096 calls) but every `swap_submit` (selector 5) answers `0xe00002c2` (bad argument), so `swaps validated 0` and the panel stays black. **Cause:** the image's `kernel.kc` is the hosted XNU prelinked with the kexts of the iOS 27 kernelcache (24A446, `kernel/prelink/build.sh`), while the userland is 20F66. 20F66's `IOMobileFramebuffer.framework` submits with `IOConnectCallStructMethod(conn, 5, &swap, 0x46c, NULL, NULL)` (1132 bytes; `0x1af1c0530` in its arm64 cache), and 24A446's `IOMobileFramebufferUserClient` dispatch table (`0xfffffe000832fd70`, `IOExternalMethodDispatch2022`, 59 selectors) requires a 0x6e0-byte (1760) structure for selector 5. Lighting the panel needs the 20F66 swap record translated to 24A446's layout in the kernel (or IOMobileGraphicsFamily of 20F66 in the collection).
   - **`0072-iomfb-ios16-swap-record.patch` (phone, 2026-11-17, kernel.kc `6771f0f4`, built in the WSL tree `~/oneios-build` with `HOSTED_OUT=a56`, then `kernel/prelink/build.sh`):** the record is rebuilt (`oneios-iomfb: an iOS 16 swap record (0x46c bytes) rebuilt as iOS 27's (0x6e0 bytes) for swap 1`) and `swap_submit` answers `0x0` (struct 1760). The display backend validated, accepted and retired 3 swaps (`swapstat validated 3 ... no-head 3 blank 3`), all blank frames, and then no more: the panel is still black and `session/ready` was not created. The vsync tick never had a client (`vsync ... sent=0 arms=0`): 20F66's backboardd never calls 24A446's vsync registration (selector 72), and several other 20F66 selectors fail on 24A446's table (12, 22, 27, 29, 33, 50, 55, 57, 68, 79, 84: `0xe00002c7`; 15: `0xe00002bc`; 74: `0xe00002c2`; `IOSurfaceRootUserClient` selector 13: `0xe00002c2`). Next: map 20F66's IOMobileFramebuffer selectors (vsync first) to 24A446's.
   - **The selector map (2026-11-17, read from 20F66's IOMobileFramebuffer.framework and IOSurface.framework, 20F66's own kernelcache `iPhone10,3_6`, and 24A446's dispatch tables).** The numbers are the same in both: 24A446's IOMobileFramebufferUserClient table has 101 entries (`0xfffffe000832fd70`, `dispatchExternalMethod` count `0x65`), and 20F66's vsync registration is selector 72 with 4 scalars, as in 24A446. backboardd never reaches it: its first swaps carry no surface (`blank`), because IOSurface fails first. 20F66's IOSurface.framework opens IOSurfaceRoot and calls selector 13 (the root's limits) before anything else, and closes the root when that fails. Size differences between 20F66's and 24A446's IOSurfaceRootUserClient tables (20F66 at file `0x292eff8` of its kernelcache, 24A446 at `0xfffffe000837fa10`/`0xfffffe0008380370`): 0/2/4/6/7/34 surface info out `0xa68` vs `0xc68`; 13 out `0x18` vs `0x28`; 27/28 `0x88` vs `0xa0`; 36 scalars in 0 vs 1; 38 scalars out 3 vs 4. IOMobileFramebufferUserClient 74 scalars out 1 vs 2. `0072` now rewrites IOMobileFramebufferUserClient 5 and 74 and IOSurfaceRootUserClient 13 (built and checked to apply; this version not yet built into a kernel or run).
   - **First frame (phone, 2026-11-17, kernel.kc `710789fd`, `0072` with the swap, SupportedFrameInfo, IOSurface limits and surface info rewrites; session `--gpu 2d`, `oneios-gpu-metal=0`):** IOSurfaceRootUserClient 13, 0 and 34 answer `0x0`, the compositor swaps a `BGRA` 1080x2340 surface (`taken`), the backend copies it (`swapstat validated 3 ... copy 3`), xnuhost logs `guest ready: first frame` and the panel shows the Apple boot logo drawn by backboardd (the black screen is gone). The boot stays on the logo: backboardd submitted 3 swaps in the first minutes and SpringBoard drew nothing yet. Still failing: IOSurfaceRootUserClient 27 (bulk attachments, 136 bytes in vs 0xa0) and 9 (`0xe00002c2`, 56 bytes in); no vsync registration (IOMobileFramebufferUserClient 72) seen.
   - **Why the boot stays on the logo (open, 2026-11-17).** After the first frame backboardd swaps 3 times in the first second and then nothing: touches reach the guest (`oneios-hid: ... HID events queued`, xnuhost `no guest frame after the touch-down`), SpringBoard is alive (`oneios-keystore: selector 35 from SpringBoard`), the backend's vsync never has a client (`vsync ... sent=0 arms=0`). 20F66's `IOMobileFramebufferEnableVSyncNotifications` is a plain synchronous `IOConnectCallScalarMethod(conn, 72, 4 scalars)` (impl `0x1af1c4afc`), so the trace would show it: backboardd never asks for vsync. Tooling note: reading the whole 20F66 cache through `apfs-fuse` in the WSL VM (3.8 GB) restarts the VM; copy the subcache needed to the Linux disk first. QuartzCore's calls into IOMobileFramebuffer go through its `__stubs` (from `0x188549170`, GOT pointers in the `.dylddata` subcaches, low 36 bits) and branch islands (`0x18dbb8000`); its `__text` alone (`0x18827d598`..`0x188546a4c`) shows none.
   - **Vsync: found and fixed (phone, 2026-11-17).** QuartzCore enables vsync in one place (`0x188373218`): with bit 1 of the display's byte `+0x30d` it calls `IOMobileFramebufferEnableVSyncNotifications` (selector 72), otherwise it uses a newer mechanism the display backend does not offer. That bit is a global set once (`0x188364614`): on when `CA_FORCE_TIMER` is set in the environment, or when MobileGestalt's `ChipID` is 0x7000, 0x7001, 0x8000..0x8004 or 0x8010; off otherwise. Fix: a text fixup that makes the check answer yes (`cset w8, cc` -> `mov w8, #1` at `0x188364684`, file offset `0x15e4684` of subcache `.03`): `text-fixup=dyld_shared_cache_arm64.03,15e4684,e8279f1a,28008052`, passed by `oneios_switch.sh --inner` for 20F66 (with keybagd's, since a command-line fixup replaces the image's) and written by roottweak into new 20F66 images. On the phone: `text-fixup: dyld_shared_cache_arm64.03+0x15e4684 (4 bytes) applied`, `IOMobileFramebufferUserClient selector 72 -> 0x0`, the backend sent 115 vsyncs and went idle (`sent=115 arms=1 idle=1`): CoreAnimation had nothing more to draw.
   - **Now the boot stops in SpringBoard, not in the display.** With `oneios-iouc=backboardd,SpringBoard`, SpringBoard's only IOKit calls in the whole session are two AppleKeyStore calls (selectors 35 and 7): it never opens IOSurface or the framebuffer, so it is blocked early in its own start, before any UI. Next: where SpringBoard waits (its threads' backtraces with `oneios-ubt=SpringBoard`, symbolicated against the 20F66 cache).
   - **Where SpringBoard waits (phone, 2026-11-17/18).** `oneios-ubt=SpringBoard` samples, symbolicated against the 20F66 cache (slide from `_mach_msg2_trap+8`, static `0x1c1852ff0`; image bases from the cache header's image table, names from `ipsw dyld symaddr --image`): first in ControlCenterServices -> CoreServices behind a LaunchServices XPC call, then for good in `MediaExperience _FigSystemControllerRemoteCreate` -> `CoreMedia _FigXPCRemoteClientCreate` -> `xpc_connection_send_message_with_reply_sync`, waiting for `com.apple.coremedia.systemcontroller.xpc`. In 20F66 that service is mediaserverd's (`/System/Library/xpc/launchd.plist` on the SystemOS volume: `KeepAlive`, `UserName mobile`, the service conditional on the feature flag `CoreAudioServices/audiomxd` being off, which it is). mediaserverd is on the OS volume and not in `disabled.plist`, but never ran (`oneios-ubt=mediaserverd` saw no process, `sheng-exc-name=mediaserverd` and `oneios-iokit=mediaserverd` logged nothing). launchd's own log (the Data volume, `log/com.apple.xpc.launchd/launchd.log`; `data.img` compressed on the phone with `gzip -1`, pulled with `adb pull`, written back sparse, `apfs-fuse -o ro,vol=2`): every 5 s `xpcproxy exited due to OS_REASON_EXEC` with `exit reason (namespace: 9 code: 0x8)`, `Service only ran for 0 seconds`. Code 8 is `EXEC_EXIT_REASON_SECURITY_POLICY`; with nothing from AMFI on the console it is `process_signature()` seeing `ip_mac_return`, a MAC policy refusing `mac_cred_label_update_execve`. Kernel patch `0073-mac-exec-label-report.patch` logs which policy (`oneios-mac-exec: NAME: policy P refused the credential update (error E)`) and, with `oneios-mac-exec-skip=NAME[,NAME...]`, ignores a Sandbox refusal for those executables.
   - **iOS 16.5 reaches its UI (phone, 2026-11-18, kernel.kc `c873424e` with 0072 and 0073, the QuartzCore vsync fixup, `oneios-mac-exec-skip=mediaserverd`).** The log: `oneios-mac-exec: mediaserverd: policy Sandbox refused the credential update (error 1): ignored (oneios-mac-exec-skip)`, and the same refusal for `TVRemoteConnectionService`. mediaserverd then runs, SpringBoard gets past `FigSystemControllerRemoteCreate`, and the owner saw iOS start on the panel. `oneios_switch.sh --inner` now passes `oneios-mac-exec-skip=mediaserverd,TVRemoteConnectionService` for 20F66. **Touch does not work yet:** the kernel queues the touches for backboardd (`oneios-hid: touch screen: 2065 HID events queued for the service's client(s), 0 dropped`, 96 contacts), backboardd turns vsync on and off as it needs it (selector 72, 629 vsyncs sent), but only 17 frames were drawn in the session and the first after a touch-down came 2.25 s later: the touches do not become gestures. backboardd's IOHIDEventServiceUserClient selectors 2 (2 scalars, 60-byte struct in and out) and 3 (3 scalars) answer `0xe00002c7`; a 20F66/24A446 difference in IOHIDFamily (those methods, or the serialized IOHIDEvent format in the shared queue) is the first suspect, not checked yet. adb drops off USB repeatedly during these sessions (it comes back), so screenshots often fail.
   - **The surface info map (in `0072`, `hosted_ios16_info_27_to_16`).** 20F66's info is a 0x68-byte header and 20 planes of 0x80 (`0x68 + 20*0x80 = 0xa68`; IOSurface.framework's client constructor `0x1a392fbc8` copies `0x68 + planes*0x80`); 24A446's is a 0x88-byte header and 20 planes of 0x98 (its fill function `0xfffffe000a33ca30` caps the planes at 20). 24A446 widened sizes, bytes per row and offsets to 64 bits (its IOSurface object: width +0x58, height +0x60, bytes per row +0x68, offset +0x78, alloc size +0x90, all u64; 20F66's: +0x90/+0x94/+0x98/+0xa0, u32; read from both kernels' property parsers). Read from both kernels' fill functions (20F66 `0xfffffff005a57648`, 24A446 `0xfffffe000a33ca30`) and 20F66's accessors (`IOSurfaceClientGet*`):

     | 20F66 header | field | 24A446 |
     |---|---|---|
     | 0x00..0x1c | shared page, timestamp, ID (from the user client) | 0x00..0x1c |
     | 0x1c, 0x34 (u32) | alloc size | 0x20 (u64) |
     | 0x20 / 0x24 / 0x28 / 0x2c (u32) | width / height / bytes per row / offset | 0x28 / 0x30 / 0x38 / 0x40 (u64) |
     | 0x30 | pixel format | 0x48 |
     | 0x38..0x68 | plane count, bytes per element and element size, cache mode, subsampling, use counts, parent ID, slice count, trace ID, protection | 0x58..0x88 |

     | 20F66 plane (0x68 + p*0x80) | field | 24A446 plane (0x88 + p*0x98) |
     |---|---|---|
     | 0x00..0x18 (6 x u32) | width, height, (unnamed), offset, bytes per row, size | 0x00..0x30 (6 x u64) |
     | 0x18 | bytes per element, element width, element height | 0x30 |
     | 0x1c..0x34 | component count, flag, component tables | 0x34..0x4c |
     | 0x34..0x70 | compression | 0x4c..0x88 |
     | 0x70..0x80 | (16 bytes) | 0x88..0x98 | The kernel before 0072 is kept on the phone in `/data/local/tmp/oneios/backup-kernel-40027f79/` (with the `image.sha256`/`image.ids` that match it).

2. **CPU Clock Pinning:**
   - In One UI, the Energy-Aware Scheduler (EAS) throttles clocks when traps occur.
   - `scripts/phone/oneios_switch_a56.sh` automatically forces `performance` governor and locks all 8 cores at their peak frequencies during the iOS session:
     - Policy 0: 1.95 GHz
     - Policy 4: 2.60 GHz
     - Policy 7: 2.91 GHz
   - Sets `renice -n -20` on `xnuhost` for real-time priority.

3. **Optimized iOS 16.5 Footprint:**
   - Rather than heavy experimental iOS 27 builds (>16 GB images causing zRAM thrashing on 8 GB RAM), the iOS 16.5 (20F66) profile uses only 2.4 GB root APFS, zero swap, and runs completely stutter-free at 60–120 FPS.
   - Daemons like `com.apple.analyticsd`, `rapportd`, and `parsecd` are cleanly handled.

---

## 3. Installation & Usage

### Prerequisites
1. **Samsung Galaxy A56 5G (`SM-A566E`)** running One UI.
2. Root access via **Dirtyfrag** (rootless softmod) or **KernelSU** (without tripping Knox 0x0).
3. The **OneiOS-A56 Manager APK** installed.

### Option 1: Direct In-App Installation (Recommended)
1. Open the **OneiOS Manager** app on your Galaxy A56.
2. Tap **📥 BAIXAR / IMPORTAR IMAGEM IOS 16.5**.
3. Choose **Baixar direto do GitHub Releases** to automatically download, unpack, and stage the system, or **Importar arquivo baixado** if you transferred the `.tar` / `.tar.gz` / `.zip` package (or its `.part00`, `.part01`, ... pieces) to `/sdcard/Download/` or a folder directly under it.
4. Tap **INICIAR SESSÃO IOS** once the badge says **SISTEMA PRONTO** (`oneios_root.sh status` answered `@@RESULT ready 1`).

What the app does, step by step:
- **On every start** it compares the SHA-256 of its `assets/bootstrap.tar.gz` with `/data/local/tmp/oneios/.bootstrap` (as root: the app cannot see inside `/data/local/tmp`). When they differ, and no iOS session of this boot is open, it replaces `bin/`, `runtime/`, `runtime.conf`, `runtime.sha256` and the three scripts (`oneios_switch.sh`, `oneios_return.sh`, `stage_ios16.sh`) with the bundled ones. The image set and the session data are never touched. It refuses a bundle whose `bin/oneios_root.sh` does not match the SHA-256 pinned in `Manager.java`.
- **Download:** the part list comes from the GitHub API (`releases/tags/v1.0.0`, every asset named `ios16.5-a56-image.tar.gz.partNN`; the two names `part00`/`part01` when the API is unreachable). Parts go to the app's own folder (`/sdcard/Android/data/local.oneios.manager/files/download/`, no storage permission needed); an interrupted part resumes with an HTTP `Range` request and a complete one is skipped. The parts are deleted once the image is staged.
- **Import:** the list of packages is read as root, because with scoped storage the app cannot see files a browser or a PC put in `Download/`.
- **Extraction:** the parts are streamed through `cat | tar -xz` (no merged copy on disk) into `image.new/`; a package whose files sit in one top folder is moved up a level; only when that worked does it replace `image/`. Then `stage_ios16.sh` writes `image.conf`, `image.sha256` and `image.ids` (its full output goes to `/data/local/tmp/oneios/stage_ios16.log`).

Building the APK: `python app/build.py` (javac, d8, aapt, zipalign, apksigner from the Android SDK; no Gradle). Step 0 refuses an `assets/bootstrap.tar.gz` that lacks one of the scripts or the runtime, whose `bin/oneios_root.sh` differs from the pin, or whose shell scripts carry a UTF-8 BOM or CRLF line endings. Tested: the new `Manager.java` compiles against `android-35/android.jar`; the bootstrap install and the extraction commands, run on a Linux stand-in of `/data/local/tmp/oneios` with a split test package, end with `oneios_switch.sh --check` (test root) passing the stage checks and `oneios_root.sh status` answering `@@RESULT ready 1`. Not yet tested on the phone itself.

### Option 2: Manual Deploy via ADB
Place the iOS 16.5 runtime and image in `/data/local/tmp/oneios`:
```sh
adb push runtime/ /data/local/tmp/oneios/runtime/
adb push image/ /data/local/tmp/oneios/image/
adb push tools/stage_ios16.sh /data/local/tmp/oneios/stage_ios16.sh
adb shell su -c "/data/local/tmp/oneios/stage_ios16.sh"
```

### Step 3: Run Preflight Check
```sh
su -c '/data/local/tmp/oneios/oneios_switch.sh --check'
# Output must be: PREFLIGHT OK
```

### Step 4: Start Session
Open the OneiOS Manager app on your Galaxy A56 and tap **INICIAR SESSÃO IOS**.

### Safety & Returning to One UI
To exit the iOS session and immediately return to Samsung One UI:
* **Hold Volume Up + Volume Down together for 3 seconds.**
* Or hold **Power + Volume Down** for 7 seconds to force reboot.
