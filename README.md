# hyalineOS — Interactive Demo & Theme Studio

Live web showcase and Theme Studio for **hyalineOS**, a unified Android Dual-OS suite:
- **OS 1 (Surface Lite)**: Zero-root Windows Metro &times; Frutiger Aero launcher (4 selectable styles, resizable Live Tiles, WP8 A&ndash;Z drawer).
- **OS 2 (Deep OS)**: Strict Root/Shizuku privilege-gated environment (4 selectable styles, App Freezing &amp; Hiding vs. Freeform Floating Windows, Configurable Door + Decoy PIN).
- **Modular Suite**: Bundled first-party tools, **PulseMesh** integration slot, and standalone theme-synced **hyaline Phone** (`ROLE_DIALER`) and **hyaline Messages** (`ROLE_SMS`) APKs.

## Deploying to `KOSTADINOSk.github.io`

This project uses zero build steps and strictly relative asset paths (`./styles/...`, `./scripts/...`), so it deploys cleanly anywhere on GitHub Pages:

1. **As a GitHub Pages Project Site (`https://kostadinosk.github.io/hyalineos/`)**:
   - Served directly from the `main` branch root (`/`) with `.nojekyll` enabled (zero build steps required).
2. **As a Custom Subdomain (e.g., `https://hyalineos.yourdomain.com`)**:
   - Note that GitHub does not allow arbitrary subdomains of `*.github.io` (only `KOSTADINOSk.github.io/<repo>`), *unless* you attach a custom domain via DNS `CNAME` &rarr; `kostadinosk.github.io` and add a `CNAME` file in this repository root.
