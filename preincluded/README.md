# Pre-Included Apps (`preincluded/`)

**hyalineOS** ships with **PulseMesh** (`com.KostadinosK.pulsemesh`) pre-included out of the box rather than requiring separate integration code:

- **Repository**: https://github.com/Kostadinosk/PulseMesh
- **Package ID**: `com.KostadinosK.pulsemesh`
- **Role in hyalineOS**: Pre-pinned 4×2 Live Tile on OS 1 Surface Lite (off-grid E2EE mesh messenger & full-duplex voice over BLE `0xCAFE/0xCAFF`, Wi-Fi Direct, Acoustic AFSK, and offline Web Mercator GPS maps) and optionally isolated/launched in Freeform mode inside OS 2.

## Bundling PulseMesh into Your Build

### Option A — Stock Android Multi-APK Suite Install (Today)
Pull the latest release APK (or add as a git submodule) and install alongside `hyalineOS`, `hyaline Phone`, and `hyaline Messages`:

```sh
git submodule add https://github.com/Kostadinosk/PulseMesh.git preincluded/PulseMesh
# Build or grab PulseMesh full-flavor APK, then install the 4-APK suite:
adb install-multiple app-launcher-release.apk app-phone-release.apk app-messages-release.apk pulsemesh-full-release.apk
```

### Option B — AOSP / LineageOS Custom ROM Pre-Inclusion (Future Flashable OS)
Drop the 4 signed APKs into `packages/apps/hyalineOS/` with an `Android.bp` `android_app_import` targeting `/system/priv-app` (for `hyalineOS` + `hyaline Phone` + `hyaline Messages`) and `/system/app` (for `PulseMesh`), and add them to `PRODUCT_PACKAGES`:

```makefile
PRODUCT_PACKAGES += \
    hyalineOS \
    hyalinePhone \
    hyalineMessages \
    PulseMesh
```
