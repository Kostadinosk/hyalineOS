/* hyalineOS Studio — Portable State & hyalineos-config.v1 Schema */

export const OS1_STYLES = [
  {
    id: 'aero-glass-metro',
    name: 'Aero-Glass Metro',
    desc: 'WP8 Live Tile grid in frosted Aero glass with top specular gleam over luminous sky.',
    swatch: 'linear-gradient(135deg, rgba(125,232,255,0.9), rgba(14,165,233,0.75))',
  },
  {
    id: 'glossy-neo-metro',
    name: 'Glossy Neo-Metro',
    desc: 'Vibrant Metro color-blocked tiles infused with liquid Frutiger Aero inner highlights.',
    swatch: 'linear-gradient(145deg, #38bdf8, #0284c7 52%, #0369a1)',
  },
  {
    id: 'minimal-frosted-aero',
    name: 'Minimal Frosted Aero',
    desc: 'Ultra-lite airy translucent glass tiles, clean Metro typography, and zero clutter.',
    swatch: 'linear-gradient(135deg, rgba(255,255,255,0.65), rgba(186,230,253,0.55))',
  },
  {
    id: 'metro-aero-gadgets',
    name: 'Metro + Aero Gadgets',
    desc: 'Live Tiles paired with classic Vista/7 floating glass gadgets and frosted bottom dock.',
    swatch: 'linear-gradient(135deg, #34d399, #0ea5e9 60%, #1d4ed8)',
  },
];

export const OS2_STYLES = [
  {
    id: 'smoked-aero-desktop',
    name: 'Smoked Aero Desktop',
    desc: 'Dark frosted Windows 7 pocket desktop with taskbar, Start orb, and floating windows.',
    swatch: 'linear-gradient(135deg, #0f2d47, #07131f)',
  },
  {
    id: 'stealth-obsidian-vault',
    name: 'Stealth Obsidian Vault',
    desc: 'Tactical low-profile obsidian glass UI built for zero-distraction privacy vaulting.',
    swatch: 'linear-gradient(135deg, #18181b, #050507)',
  },
  {
    id: 'chameleon-persona-spaces',
    name: 'Chameleon Personas',
    desc: 'Isolated Work, Private Vault, Travel, and Guest sub-spaces with adaptive themes.',
    swatch: 'linear-gradient(135deg, #3b1c6e, #0d1630)',
  },
  {
    id: 'metro-aero-pro',
    name: 'Metro-Aero Pro',
    desc: 'Deep midnight sapphire mirror of OS 1 with hidden/vaulted tools and power controls.',
    swatch: 'linear-gradient(135deg, #0c356a, #040d1d)',
  },
];

export const WALLPAPERS = [
  { id: 'aurora-sky', label: 'Aurora Sky' },
  { id: 'aqua-bokeh', label: 'Aqua Bokeh' },
  { id: 'emerald-meadow', label: 'Emerald Meadow' },
  { id: 'sunset-prism', label: 'Sunset Prism' },
  { id: 'midnight-glass', label: 'Midnight Glass' },
];

export const ACCENT_SWATCHES = [
  '#00a8e8',
  '#10b981',
  '#0ea5e9',
  '#8b5cf6',
  '#f43f5e',
  '#f59e0b',
  '#14b8a6',
];

export const EXTRA_TILE_CATALOG = [
  {
    id: 'calc',
    label: 'hyaline Calc',
    packageName: 'os.hyaline.tools.calc',
    icon: '🧮',
    size: '1x1',
    accentOverride: '#0284c7',
    liveHeadline: 'Unit & Currency',
    liveSub: 'Instant Metro calc',
    badge: '',
    category: 'Bundled OS 1 Tool',
  },
  {
    id: 'qr',
    label: 'QR & Voice',
    packageName: 'os.hyaline.tools.capture',
    icon: '🎙️',
    size: '2x2',
    accentOverride: '#14b8a6',
    liveHeadline: 'Quick Capture',
    liveSub: 'QR scanner • Voice memo',
    badge: 'READY',
    category: 'Pocket Utility',
  },
  {
    id: 'clipboard',
    label: 'Clip Vault',
    packageName: 'os.hyaline.tools.clipboard',
    icon: '📋',
    size: '1x1',
    accentOverride: '#6366f1',
    liveHeadline: '12 clips saved',
    liveSub: 'Tap to paste',
    badge: '12',
    category: 'Pocket Utility',
  },
  {
    id: 'focus',
    label: 'Clock & Focus',
    packageName: 'os.hyaline.tools.clock',
    icon: '⏱️',
    size: '2x2',
    accentOverride: '#0ea5e9',
    liveHeadline: '25:00 Focus',
    liveSub: 'Pomodoro • World Clock',
    badge: '',
    category: 'Bundled OS 1 Tool',
  },
];

export function createDefaultConfig() {
  return {
    schemaVersion: 1,
    osName: 'hyalineOS',
    os1: {
      style: 'aero-glass-metro',
      accentHex: '#00a8e8',
      glassOpacity: 0.44,
      glassBlurPx: 18,
      wallpaperPreset: 'aurora-sky',
      tileRadiusPx: 10,
      tiles: [
        {
          id: 'phone',
          label: 'hyaline Phone',
          packageName: 'os.hyaline.phone',
          icon: '📞',
          size: '2x2',
          accentOverride: '#10b981',
          liveHeadline: '1 missed call',
          liveSub: 'Alex • 12m ago (Telco)',
          wideExtra: 'Standalone ROLE_DIALER • Theme-Synced',
          badge: '1',
          category: 'Standalone APK',
        },
        {
          id: 'messages',
          label: 'hyaline Messages',
          packageName: 'os.hyaline.messages',
          icon: '💬',
          size: '2x2',
          accentOverride: '#0096c7',
          liveHeadline: '2 unread SMS',
          liveSub: '“OTP 482910 — tap to copy”',
          wideExtra: 'Standalone ROLE_SMS • Live Tile feed',
          badge: '2',
          category: 'Standalone APK',
        },
        {
          id: 'pulsemesh',
          label: 'PulseMesh',
          packageName: 'os.hyaline.feature.pulsemesh',
          icon: '📡',
          size: '4x2',
          accentOverride: '#7c3aed',
          liveHeadline: 'PulseMesh Bundled Module',
          liveSub: 'Ready to wire your existing PulseMesh project',
          wideExtra: 'Live Tile status • Shared hyalineOS glass tokens',
          badge: 'MESH',
          category: 'Bundled Feature Module',
        },
        {
          id: 'notes',
          label: 'hyaline Notes',
          packageName: 'os.hyaline.tools.notes',
          icon: '📝',
          size: '2x2',
          accentOverride: '#0ea5e9',
          liveHeadline: 'Build hyalineOS v1',
          liveSub: 'OOBE + Live Tiles + Shizuku gate',
          wideExtra: 'Pinned Markdown scratchpad',
          badge: '',
          category: 'Bundled OS 1 Tool',
        },
        {
          id: 'weather',
          label: 'Weather & Pulse',
          packageName: 'os.hyaline.tools.pulse',
          icon: '🌤️',
          size: '2x2',
          accentOverride: '#0284c7',
          liveHeadline: '21° Clear Sky',
          liveSub: 'Battery 84% • RAM 4.1 GB free',
          wideExtra: 'Zero-root Lite telemetry tile',
          badge: '21°',
          category: 'Bundled OS 1 Tool',
        },
        {
          id: 'files',
          label: 'hyaline Files',
          packageName: 'os.hyaline.tools.files',
          icon: '📁',
          size: '1x1',
          accentOverride: '#f59e0b',
          liveHeadline: 'Dual-Mode Files',
          liveSub: 'Root unlocks in OS 2',
          wideExtra: 'Local + Root dual-pane explorer',
          badge: '',
          category: 'Unified Dual-Mode Tool',
        },
        {
          id: 'calc',
          label: 'hyaline Calc',
          packageName: 'os.hyaline.tools.calc',
          icon: '🧮',
          size: '1x1',
          accentOverride: '#06b6d4',
          liveHeadline: 'Calc & Convert',
          liveSub: 'Units • Currency',
          wideExtra: 'Instant Metro calculator',
          badge: '',
          category: 'Bundled OS 1 Tool',
        },
        {
          id: 'qr',
          label: 'QR & Voice',
          packageName: 'os.hyaline.tools.capture',
          icon: '🎙️',
          size: '2x2',
          accentOverride: '#14b8a6',
          liveHeadline: 'Quick Capture',
          liveSub: 'QR Scanner • Voice Memo • Clips',
          wideExtra: 'Pocket utility strip',
          badge: '',
          category: 'Pocket Utility',
        },
      ],
    },
    os2: {
      requiresPrivilege: 'root-or-shizuku',
      privilegeSimulated: 'shizuku', // 'none' | 'shizuku' | 'root'
      style: 'smoked-aero-desktop',
      engineMode: 'app-freezing', // 'app-freezing' | 'freeform-windows'
      doorTrigger: 'long-press-clock', // 'long-press-clock' | 'visible-dock-pill' | 'secret-drawer-code'
      autoLockOnScreenOff: true,
      decoyPinEnabled: true,
      accentHex: '#38bdf8',
      glassOpacity: 0.62,
      activePersona: 'Private Vault',
    },
  };
}

export const OS2_FREEZER_APPS = [
  { id: 'vault', name: 'hyaline Vault (AES-256)', pkg: 'os.hyaline.os2.vault', frozenInOs1: true },
  { id: 'terminal', name: 'hyaline Shell Terminal', pkg: 'os.hyaline.os2.shell', frozenInOs1: true },
  { id: 'browser', name: 'Ephemeral Browser', pkg: 'os.hyaline.os2.web', frozenInOs1: true },
  { id: 'work-mail', name: 'Work Mail & Slack', pkg: 'com.work.suite', frozenInOs1: true },
  { id: 'pulsemesh-priv', name: 'PulseMesh (Deep Node)', pkg: 'os.hyaline.feature.pulsemesh', frozenInOs1: false },
];

const STORAGE_KEY = 'hyalineos_studio_config_v1';

export function loadConfig() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return createDefaultConfig();
    const parsed = JSON.parse(raw);
    return normalizeConfig(parsed);
  } catch {
    return createDefaultConfig();
  }
}

export function saveConfig(config) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(config));
  } catch {
    // Ignore storage quota errors in private browsing
  }
}

export function normalizeConfig(input) {
  const base = createDefaultConfig();
  if (!input || typeof input !== 'object') return base;

  const os1 = {
    ...base.os1,
    ...(input.os1 && typeof input.os1 === 'object' ? input.os1 : {}),
  };
  if (!Array.isArray(os1.tiles) || os1.tiles.length === 0) {
    os1.tiles = base.os1.tiles;
  }

  const os2 = {
    ...base.os2,
    ...(input.os2 && typeof input.os2 === 'object' ? input.os2 : {}),
    requiresPrivilege: 'root-or-shizuku',
  };

  return {
    schemaVersion: 1,
    osName: 'hyalineOS',
    os1,
    os2,
  };
}

export function exportPortableJson(config) {
  return JSON.stringify(
    {
      $schema: 'https://kostadinosk.github.io/hyalineos/schemas/config-v1.json',
      schemaVersion: 1,
      osName: 'hyalineOS',
      exportedAt: new Date().toISOString(),
      os1: {
        style: config.os1.style,
        accentHex: config.os1.accentHex,
        glassOpacity: config.os1.glassOpacity,
        glassBlurPx: config.os1.glassBlurPx,
        wallpaperPreset: config.os1.wallpaperPreset,
        tileRadiusPx: config.os1.tileRadiusPx,
        tiles: config.os1.tiles.map((t) => ({
          id: t.id,
          label: t.label,
          packageName: t.packageName,
          icon: t.icon,
          size: t.size,
          accentOverride: t.accentOverride ?? null,
        })),
      },
      os2: {
        requiresPrivilege: 'root-or-shizuku',
        style: config.os2.style,
        engineMode: config.os2.engineMode,
        doorTrigger: config.os2.doorTrigger,
        autoLockOnScreenOff: config.os2.autoLockOnScreenOff,
        decoyPinEnabled: config.os2.decoyPinEnabled,
        accentHex: config.os2.accentHex,
        glassOpacity: config.os2.glassOpacity,
      },
    },
    null,
    2,
  );
}
