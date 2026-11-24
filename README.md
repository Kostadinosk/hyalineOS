# hyalineOS

A unified **Dual-OS Android Suite** combining a zero-root **Windows Metro × Frutiger Aero** everyday launcher (**OS 1 — Surface Lite**) with a strictly privilege-gated **Root / Shizuku** deep operating environment (**OS 2 — Deep OS**), standalone theme-synced **Phone** and **Messages** apps, pre-included **PulseMesh**, and a live **Interactive Demo & Theme Studio**.

- **Live Web Demo & Theme Studio**: https://kostadinosk.github.io/hyalineOS/ (served from [`docs/`](./docs/))
- **Kernel / OS Backbone**: Android / AOSP Linux Kernel backbone today (runs on any Android 8–16 phone with zero driver breakage), designed so the exact same APKs drop straight into `/system/priv-app` of a custom AOSP / LineageOS ROM fork later.

---

## Repository Structure

| Path | Description |
| :--- | :--- |
| [`app-launcher/`](./app-launcher) | **`hyalineOS` (`os.hyaline.launcher`)** — `CATEGORY_HOME` Dual-OS Launcher, First-Boot OOBE (4 OS 1 styles + 4 OS 2 styles), NotificationListener Live Tiles, screen-off auto-lock, and `hyalineos-config.json` import |
| [`app-phone/`](./app-phone) | **`hyaline Phone` (`os.hyaline.phone`)** — Standalone theme-synced `ROLE_DIALER` / `InCallService` Telco dialer APK |
| [`app-messages/`](./app-messages) | **`hyaline Messages` (`os.hyaline.messages`)** — Standalone theme-synced `ROLE_SMS` messaging APK |
| [`core-theme/`](./core-theme) | Shared Metro × Frutiger Aero glass surfaces & portable `hyalineos-config.v1` JSON schema |
| [`core-privilege/`](./core-privilege) | Strict Root (`libsu`) / Shizuku (`rikka.shizuku`) privilege gate & OS 2 engine (**App Freezing & Hiding** + **Freeform Floating Windows**) |
| [`preincluded/PulseMesh/`](./preincluded) | Pre-included [**PulseMesh**](https://github.com/Kostadinosk/PulseMesh) (`com.KostadinosK.pulsemesh`) — off-grid E2EE mesh messenger & full-duplex voice (BLE, Wi-Fi Direct, Acoustic AFSK & offline GPS maps) |
| [`docs/`](./docs) | GitHub Pages **Interactive Demo & Theme Studio** (`hyalineos-config.json` builder & exporter) |

## Cloning with Pre-Included PulseMesh

```sh
git clone --recurse-submodules https://github.com/Kostadinosk/hyalineOS.git
```
