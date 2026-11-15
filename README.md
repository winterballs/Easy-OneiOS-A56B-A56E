**Русский** | [English](README_EN.md)

# Easy OneiOS for Samsung Galaxy A56 5G (SM-A566B / SM-A566E / a56x)

[![Device: Galaxy A56 5G](https://img.shields.io/badge/Device-Samsung%20Galaxy%20A56%205G-blue.svg)](https://github.com/winterballs/Easy-OneiOS-A56B-A56E)
[![SoC: Exynos 1580](https://img.shields.io/badge/SoC-Exynos%201580%20(4nm)-orange.svg)](https://github.com/winterballs/Easy-OneiOS-A56B-A56E)
[![GPU: AMD Xclipse 540](https://img.shields.io/badge/GPU-Samsung%20Xclipse%20540%20(RDNA3)-red.svg)](https://github.com/winterballs/Easy-OneiOS-A56B-A56E)
[![Knox: 0x0 Safe](https://img.shields.io/badge/Knox-0x0%20Intact-brightgreen.svg)](https://github.com/winterballs/Easy-OneiOS-A56B-A56E)

**Easy OneiOS** — это форк проекта OneiOS, который приносит аутентичный, ультра-оптимизированный iOS userland (Darwin Kernel 22.5.0 / iOS 16.5, XNU, `launchd`, `backboardd` и `SpringBoard`) на **Samsung Galaxy A56 5G (`SM-A566B` / `SM-A566E`)** с возможностью работы параллельно с Samsung One UI.

<p align="center">
  <img src="docs/screenshots/manager-main.jpg" alt="OneiOS Manager" width="320">
</p>

В отличие от традиционных ROM или замены загрузчика, **Easy OneiOS не прошивает разделы и не стирает пользовательские данные**. iOS userland работает поверх ядра Linux от One UI через специализированный XNU-хост (`xnuhost`), сохраняя ваш **Knox warranty bit на 0x0**.

Главное отличие от оригинала — **полностью автоматическая установка** через наше Material You 3 приложение, которое само патчит скрипты под SM-A566B, создает конфиги, хэши и проверяет систему до статуса `PREFLIGHT OK`.

---

## Почему iOS 16.5 на Galaxy A56 5G? (Zero Stutter / 60–120 FPS)

В то время как экспериментальные сборки iOS 27 сталкиваются с сильным zRAM-свопингом (>16 ГБ дисковых образов, забивающих 8 ГБ физической RAM A56, с `PosterBoard` и расширениями обоев, нагружающими ядра CPU до 450%), профиль **iOS 16.5 (20F66)** настроен специально под архитектуру Exynos 1580 / Xclipse 540:

1. **Ультра-легкий вес (2.4 ГБ против 16.5 ГБ):** Полностью помещается в физическую RAM, оставляя более 3.5 ГБ свободными для хостовых процессов, устраняя все всплески zRAM-свопа и микрозадержки.
2. **Чистый композитор SpringBoard:** Полностью свободен от fallback демонов обоев, `intelligenceflowd` и циклов CPU `ExtensionFoundation`.
3. **Двойной RAMDisk (`RAMDisk` + `RAMDisk1`):** `xnuhost` мапит оба устройства `/dev/md0` и `/dev/md1` в выбранной карте памяти device tree, позволяя `stage0` монтировать `root.apfs` прямо на `/redwood` с полной оркестрацией LaunchDaemon.
4. **Аппаратное ускорение AMDGPU:** Напрямую использует `/dev/dri/renderD128` (Samsung Xclipse 540 / AMD RDNA3) с нативным импортом буферов PRIME `dma-buf`.

---

## Ключевые особенности на Galaxy A56 5G

- **Zero Knox Tripping:** Работает в userland с `ro.boot.warranty_bit: 0` и `verifiedbootstate: green`. Samsung Pay/Wallet и Knox Secure Folder остаются нетронутыми.
- **Разгон CPU Exynos 1580 (4nm):** Автоматически фиксирует все 8 ядер на пиковых заводских частотах во время сессий:
  - 1x Cortex-X4 @ **2.91 GHz** (Prime)
  - 3x Cortex-A720 @ **2.60 GHz** (Performance)
  - 4x Cortex-A520 @ **1.95 GHz** (Efficiency)
- **Ускорение GPU AMD RDNA3 (Xclipse 540):** Поддержка драйвера Direct Rendering Manager (DRM) `amdgpu` через `/dev/dri/renderD128` с импортом памяти PRIME `dma-buf` и управлением пулом ответов.
- **Гибридный менеджер One UI / iOS (APK):** Наше приложение на Kotlin + Jetpack Compose с Material You 3, русским интерфейсом, автоматической установкой и встроенным терминальным логом.
- **Failsafe Return Chord:** Зажмите **Volume Up + Volume Down на 3 секунды** в любой момент, чтобы мгновенно вернуться в One UI.

---

## Быстрый старт и установка

### Вариант A: Прямая установка через приложение (Самый простой / Без ПК)

1. Скачайте и установите APK из [Releases](https://github.com/winterballs/Easy-OneiOS-A56B-A56E/releases) (или соберите сами).
2. Предоставьте root-доступ через **KernelSU** (Knox 0x0 сохраняется).
3. Откройте приложение **OneiOS** и нажмите **Скачать образ iOS 16.5** (скачает ~3 ГБ с GitHub Releases).
4. Нажмите **Установить iOS (автоматически)**. Скрипт сам распакует образ, скачает `xnuhost`, создаст конфиги, пропатчит `oneios_switch.sh` под `SM-A566B`, посчитает хэши и проверит систему.
5. Когда статус сменится на **СИСТЕМА ГОТОВА**, нажмите **Запустить iOS**.

### Вариант B: Ручная установка через ADB

1. Скачайте и установите APK.
2. Разверните runtime и образ в `/data/local/tmp/oneios/`:
   ```bash
   adb push runtime/ /data/local/tmp/oneios/runtime/
   adb push image/ /data/local/tmp/oneios/image/
   adb push tools/stage_ios16.sh /data/local/tmp/oneios/stage_ios16.sh
   adb shell su -c "/data/local/tmp/oneios/stage_ios16.sh"
