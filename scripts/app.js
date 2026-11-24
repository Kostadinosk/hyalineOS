/* hyalineOS Interactive Demo & Theme Studio — Main Application Controller */

import { enhanceDialogs, closeTopLayer } from './polyfills.js';
import {
  OS1_STYLES,
  OS2_STYLES,
  WALLPAPERS,
  ACCENT_SWATCHES,
  EXTRA_TILE_CATALOG,
  OS2_FREEZER_APPS,
  createDefaultConfig,
  loadConfig,
  saveConfig,
  normalizeConfig,
  exportPortableJson,
} from './state.js';

let config = loadConfig();

const ui = {
  activeScreen: 'os1', // 'os1' | 'os1-drawer' | 'os1-app' | 'os2' | 'decoy-guest'
  selectedTileId: config.os1.tiles[0]?.id ?? null,
  activeMicroAppId: null,
  drawerLetterFilter: 'ALL',
  oobeStep: 1,
  pinBuffer: '',
  freezerState: OS2_FREEZER_APPS.map((a) => ({ ...a })),
  shellLog: [
    '$ shizuku --status -> binder OK (API 35)',
    '$ pm list packages --user 0 -> isolated 4 OS 2 packages',
  ],
  windows: [
    { id: 'win-term', title: 'hyaline Terminal (Shizuku)', x: 10, y: 12, z: 2, body: 'uid=2000(shell)\n$ pm suspend com.work.suite\n-> Package suspended cleanly' },
    { id: 'win-vault', title: 'hyaline Vault (AES-256)', x: 64, y: 118, z: 3, body: 'keys_backup.kdbx  [ENC]\npassport_scan.pdf [ENC]\nAuto-wipe on screen-off: ON' },
  ],
  topWindowZ: 4,
  noteDraft: 'hyalineOS v1 Checklist:\n- 4 OS 1 Metro x Aero styles\n- 4 OS 2 Root/Shizuku styles\n- Wire PulseMesh module\n- Standalone Phone & SMS APKs',
};

const els = {
  phoneScreen: document.getElementById('phone-screen'),
  tabOs1: document.getElementById('tab-os1'),
  tabOs2: document.getElementById('tab-os2'),
  os2Dot: document.getElementById('os2-dot'),
  os1PresetGrid: document.getElementById('os1-preset-grid'),
  os2PresetGrid: document.getElementById('os2-preset-grid'),
  accentSwatches: document.getElementById('accent-swatches'),
  wallpaperPills: document.getElementById('wallpaper-pills'),
  tileInspector: document.getElementById('tile-inspector'),
  privSimGroup: document.getElementById('priv-sim-group'),
  engineModeGroup: document.getElementById('engine-mode-group'),
  doorTriggerGroup: document.getElementById('door-trigger-group'),
  rangeOpacity: document.getElementById('range-opacity'),
  rangeBlur: document.getElementById('range-blur'),
  rangeRadius: document.getElementById('range-radius'),
  valAccent: document.getElementById('val-accent'),
  valOpacity: document.getElementById('val-opacity'),
  valBlur: document.getElementById('val-blur'),
  valRadius: document.getElementById('val-radius'),
  valWallpaper: document.getElementById('val-wallpaper'),
  oobeDialog: document.getElementById('oobe-dialog'),
  oobeSubtitle: document.getElementById('oobe-subtitle'),
  oobePills: document.getElementById('oobe-pills'),
  oobeBody: document.getElementById('oobe-body'),
  oobePrevBtn: document.getElementById('oobe-prev-btn'),
  oobeNextBtn: document.getElementById('oobe-next-btn'),
  doorDialog: document.getElementById('door-dialog'),
  doorStatusMsg: document.getElementById('door-status-msg'),
  pinDisplay: document.getElementById('pin-display'),
  pinPad: document.getElementById('pin-pad'),
  configDialog: document.getElementById('config-dialog'),
  configTextarea: document.getElementById('config-json-textarea'),
  configFeedback: document.getElementById('config-feedback'),
  toggleStudioThemeBtn: document.getElementById('toggle-studio-theme-btn'),
};

/**
 * View Transition wrapper adhering to modern-web-guidance:
 * - Graceful fallback when document.startViewTransition is unavailable
 * - Mandatory programmatic focus routing to keep keyboard & AT users oriented
 */
function runViewTransition(updateFn, focusTarget = null) {
  if (!document.startViewTransition) {
    updateFn();
    if (focusTarget) focusTarget.focus({ preventScroll: true });
    return;
  }
  const transition = document.startViewTransition(() => {
    updateFn();
  });
  if (focusTarget) {
    transition.finished.finally(() => {
      focusTarget.focus({ preventScroll: true });
    });
  }
}

function getFormattedTime() {
  const now = new Date();
  return now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function syncCssTokens() {
  const root = document.documentElement;
  root.style.setProperty('--os1-accent', config.os1.accentHex);
  root.style.setProperty('--os1-glass-opacity', String(config.os1.glassOpacity));
  root.style.setProperty('--os1-glass-blur', `${config.os1.glassBlurPx}px`);
  root.style.setProperty('--os1-tile-radius', `${config.os1.tileRadiusPx}px`);
  root.style.setProperty('--os2-accent', config.os2.accentHex);
  root.style.setProperty('--os2-glass-opacity', String(config.os2.glassOpacity));

  els.phoneScreen.dataset.os1Style = config.os1.style;
  els.phoneScreen.dataset.os2Style = config.os2.style;
  els.phoneScreen.dataset.wallpaper = config.os1.wallpaperPreset;

  els.valAccent.textContent = config.os1.accentHex;
  els.valOpacity.textContent = `${Math.round(config.os1.glassOpacity * 100)}%`;
  els.valBlur.textContent = `${config.os1.glassBlurPx}px`;
  els.valRadius.textContent = `${config.os1.tileRadiusPx}px`;
  els.valWallpaper.textContent = config.os1.wallpaperPreset;

  els.rangeOpacity.value = String(Math.round(config.os1.glassOpacity * 100));
  els.rangeBlur.value = String(config.os1.glassBlurPx);
  els.rangeRadius.value = String(config.os1.tileRadiusPx);
}

function renderStatusBar(isOs2) {
  const privLabel =
    config.os2.privilegeSimulated === 'root'
      ? 'ROOT'
      : config.os2.privilegeSimulated === 'shizuku'
        ? 'SHIZUKU'
        : 'LOCKED';

  return `
    <div class="phone-statusbar">
      <span>${getFormattedTime()}</span>
      <span class="phone-camera-pill" aria-hidden="true"></span>
      <span class="status-icons">
        ${isOs2 ? `<span class="priv-badge">${privLabel}</span>` : ''}
        <span>5G</span>
        <span>84%</span>
      </span>
    </div>
  `;
}

function renderPhoneScreen() {
  const isOs2 = ui.activeScreen === 'os2';
  els.phoneScreen.classList.toggle('os1-surface', !isOs2);
  els.phoneScreen.classList.toggle('os2-surface', isOs2);

  els.tabOs1.setAttribute('aria-pressed', String(!isOs2));
  els.tabOs2.setAttribute('aria-pressed', String(isOs2));
  els.os2Dot.className = `status-dot ${config.os2.privilegeSimulated === 'none' ? 'locked' : 'active-os2'}`;

  if (ui.activeScreen === 'os1') {
    renderOs1Home();
  } else if (ui.activeScreen === 'os1-drawer') {
    renderOs1Drawer();
  } else if (ui.activeScreen === 'os1-app') {
    renderMicroApp();
  } else if (ui.activeScreen === 'decoy-guest') {
    renderDecoyGuest();
  } else if (ui.activeScreen === 'os2') {
    renderOs2Workspace();
  }
}

function renderOs1Home() {
  const tilesHtml = config.os1.tiles
    .map((tile) => {
      const tileBg = tile.accentOverride || config.os1.accentHex;
      const isSelected = tile.id === ui.selectedTileId;
      return `
        <button
          type="button"
          class="metro-tile dynamic-contrast-fg ${isSelected ? 'is-selected' : ''}"
          data-tile-id="${tile.id}"
          data-size="${tile.size}"
          style="view-transition-name: tile-${tile.id}; --tile-bg: ${tileBg};"
          aria-label="${tile.label}, size ${tile.size}"
        >
          <span class="tile-head">
            <span class="tile-icon" aria-hidden="true">${tile.icon}</span>
            ${tile.badge ? `<span class="tile-badge">${tile.badge}</span>` : ''}
          </span>
          <span class="tile-live-body">
            <span class="tile-live-headline">${tile.liveHeadline || tile.label}</span>
            <span class="tile-live-sub">${tile.liveSub || ''}</span>
            ${tile.wideExtra ? `<span class="tile-wide-extra">${tile.wideExtra}</span>` : ''}
          </span>
          <span class="tile-footer">
            <span class="tile-label">${tile.label}</span>
          </span>
        </button>
      `;
    })
    .join('');

  const showVisibleDoor =
    config.os2.doorTrigger === 'visible-dock-pill' || config.os1.style === 'metro-aero-gadgets';

  els.phoneScreen.innerHTML = `
    ${renderStatusBar(false)}
    <div class="os1-body">
      <div class="os1-topbar">
        <button
          type="button"
          class="os1-clock-trigger"
          id="phone-clock-door"
          title="Tap to trigger OS 2 Configurable Door"
        >${getFormattedTime()}</button>
        <button type="button" class="os1-drawer-btn" id="open-drawer-btn">
          A&ndash;Z &rarr;
        </button>
      </div>

      <div class="aero-gadgets-strip" aria-label="Frutiger Aero Gadgets">
        <div class="aero-gadget">
          <div class="aero-gadget-label">Weather Orb</div>
          <div class="aero-gadget-val">21&deg; Clear</div>
        </div>
        <div class="aero-gadget">
          <div class="aero-gadget-label">System Pulse</div>
          <div class="aero-gadget-val">4.1 GB Free</div>
        </div>
      </div>

      <div class="metro-grid" id="metro-grid">
        ${tilesHtml}
      </div>
    </div>

    ${
      showVisibleDoor
        ? `
      <div class="os1-dock">
        <button type="button" class="dock-chip" data-launch-app="phone">&#128222; Phone</button>
        <button type="button" class="dock-chip" data-launch-app="messages">&#128172; SMS</button>
        <button type="button" class="dock-chip" id="dock-os2-door">&#128272; OS 2</button>
      </div>
    `
        : ''
    }
  `;

  els.phoneScreen.querySelector('#phone-clock-door')?.addEventListener('click', openDoorDialog);
  els.phoneScreen.querySelector('#dock-os2-door')?.addEventListener('click', openDoorDialog);
  els.phoneScreen.querySelector('#open-drawer-btn')?.addEventListener('click', () => {
    runViewTransition(() => {
      ui.activeScreen = 'os1-drawer';
      renderPhoneScreen();
    }, els.phoneScreen);
  });

  els.phoneScreen.querySelectorAll('[data-launch-app]').forEach((btn) => {
    btn.addEventListener('click', () => openMicroApp(btn.dataset.launchApp));
  });

  els.phoneScreen.querySelectorAll('.metro-tile').forEach((btn) => {
    btn.addEventListener('click', () => {
      ui.selectedTileId = btn.dataset.tileId;
      renderPhoneScreen();
      renderTileInspector();
    });
    btn.addEventListener('dblclick', () => openMicroApp(btn.dataset.tileId));
  });
}

function renderOs1Drawer() {
  const allApps = [...config.os1.tiles].sort((a, b) => a.label.localeCompare(b.label));
  const letters = ['ALL', ...new Set(allApps.map((a) => a.label[0].toUpperCase()))];
  const filtered =
    ui.drawerLetterFilter === 'ALL'
      ? allApps
      : allApps.filter((a) => a.label[0].toUpperCase() === ui.drawerLetterFilter);

  els.phoneScreen.innerHTML = `
    ${renderStatusBar(false)}
    <div class="az-drawer">
      <div class="os1-topbar" style="padding-bottom: 0.25rem;">
        <button type="button" class="os1-drawer-btn" id="back-home-btn">&larr; Start Tiles</button>
        <span style="font-size: 0.78rem; font-weight: 700;">A&ndash;Z App List</span>
      </div>

      ${
        config.os2.doorTrigger === 'secret-drawer-code'
          ? `
        <button type="button" class="az-row" id="secret-code-trigger" style="border-style: dashed;">
          <span class="az-row-icon">&#128273;</span>
          <span>Tap to enter secret OS 2 code (#1337)</span>
        </button>
      `
          : ''
      }

      <div class="az-jump-bar" role="group" aria-label="Jump by letter">
        ${letters
          .map(
            (l) => `
          <button type="button" class="az-letter-btn ${ui.drawerLetterFilter === l ? 'active' : ''}" data-letter="${l}">${l}</button>
        `,
          )
          .join('')}
      </div>

      <div class="az-list">
        ${filtered
          .map(
            (app) => `
          <button type="button" class="az-row" data-open-app="${app.id}">
            <span class="az-row-icon">${app.icon}</span>
            <span>${app.label}</span>
            <span class="az-row-sub">${app.category || 'App'}</span>
          </button>
        `,
          )
          .join('')}
      </div>
    </div>
  `;

  els.phoneScreen.querySelector('#back-home-btn')?.addEventListener('click', () => {
    runViewTransition(() => {
      ui.activeScreen = 'os1';
      renderPhoneScreen();
    }, els.phoneScreen);
  });

  els.phoneScreen.querySelector('#secret-code-trigger')?.addEventListener('click', openDoorDialog);

  els.phoneScreen.querySelectorAll('[data-letter]').forEach((btn) => {
    btn.addEventListener('click', () => {
      ui.drawerLetterFilter = btn.dataset.letter;
      renderOs1Drawer();
    });
  });

  els.phoneScreen.querySelectorAll('[data-open-app]').forEach((btn) => {
    btn.addEventListener('click', () => openMicroApp(btn.dataset.openApp));
  });
}

function openMicroApp(tileId) {
  runViewTransition(() => {
    ui.activeMicroAppId = tileId;
    ui.activeScreen = 'os1-app';
    renderPhoneScreen();
  }, els.phoneScreen);
}

function renderMicroApp() {
  const tile = config.os1.tiles.find((t) => t.id === ui.activeMicroAppId) || config.os1.tiles[0];
  let bodyContent = `<p style="font-size:0.78rem;">Previewing <strong>${tile.label}</strong> (<code>${tile.packageName}</code>). Styled in active hyalineOS Metro &times; Frutiger Aero tokens.</p>`;

  if (tile.id === 'notes') {
    bodyContent = `
      <label for="micro-notes-area" style="font-size:0.74rem; opacity:0.9;">Pinned Live Tile Markdown Pad</label>
      <textarea id="micro-notes-area" class="micro-note-input">${ui.noteDraft}</textarea>
    `;
  } else if (tile.id === 'phone') {
    bodyContent = `
      <div class="freezer-card" style="background: rgba(255,255,255,0.16); border-color: rgba(255,255,255,0.34);">
        <strong>Standalone APK (ROLE_DIALER)</strong>
        <div class="freezer-row"><span>Alex (Mobile)</span><span>12m ago</span></div>
        <div class="freezer-row"><span>Design Review</span><span>Yesterday</span></div>
        <div class="freezer-row"><span>Voicemail</span><span>2d ago</span></div>
      </div>
    `;
  } else if (tile.id === 'messages') {
    bodyContent = `
      <div class="freezer-card" style="background: rgba(255,255,255,0.16); border-color: rgba(255,255,255,0.34);">
        <strong>Standalone APK (ROLE_SMS)</strong>
        <div class="freezer-row"><span>Bank Alert</span><span>OTP 482910</span></div>
        <div class="freezer-row"><span>Alex</span><span>Testing hyalineOS!</span></div>
      </div>
    `;
  } else if (tile.id === 'pulsemesh') {
    bodyContent = `
      <div class="freezer-card" style="background: rgba(124,58,237,0.32); border-color: rgba(216,180,254,0.45);">
        <strong>PulseMesh Integration Slot</strong>
        <p style="font-size:0.75rem; margin: 0.4rem 0 0;">
          Dedicated <code>:feature-pulsemesh</code> Gradle module ready to bundle your existing PulseMesh project with shared hyalineOS glass styling &amp; Live Tile updates.
        </p>
      </div>
    `;
  }

  els.phoneScreen.innerHTML = `
    ${renderStatusBar(false)}
    <div class="micro-app-view">
      <div class="micro-app-bar">
        <strong>${tile.icon} ${tile.label}</strong>
        <button type="button" class="os1-drawer-btn" id="close-micro-app">&larr; Start</button>
      </div>
      ${bodyContent}
    </div>
  `;

  els.phoneScreen.querySelector('#close-micro-app')?.addEventListener('click', () => {
    runViewTransition(() => {
      ui.activeScreen = 'os1';
      renderPhoneScreen();
    }, els.phoneScreen);
  });

  els.phoneScreen.querySelector('#micro-notes-area')?.addEventListener('input', (e) => {
    ui.noteDraft = e.target.value;
    const notesTile = config.os1.tiles.find((t) => t.id === 'notes');
    if (notesTile) {
      notesTile.liveSub = ui.noteDraft.split('\n')[0].slice(0, 36);
    }
  });
}

function renderDecoyGuest() {
  els.phoneScreen.innerHTML = `
    ${renderStatusBar(false)}
    <div class="os1-body" style="justify-content: center; gap: 0.85rem;">
      <div class="strict-gate-box" style="background: rgba(10, 42, 74, 0.72); border-color: rgba(125, 211, 252, 0.5);">
        <h3 style="color: #bae6fd;">Decoy Guest Space Active</h3>
        <p>Entered via Decoy PIN (<code>0000</code>). OS 2 remains completely invisible, all OS 2 packages stay frozen, and only harmless guest utilities appear.</p>
        <button type="button" class="btn btn-sm btn-primary" id="exit-decoy-btn">Return to OS 1</button>
      </div>
    </div>
  `;

  els.phoneScreen.querySelector('#exit-decoy-btn')?.addEventListener('click', () => {
    runViewTransition(() => {
      ui.activeScreen = 'os1';
      renderPhoneScreen();
    }, els.phoneScreen);
  });
}

function renderOs2Workspace() {
  if (config.os2.privilegeSimulated === 'none') {
    els.phoneScreen.innerHTML = `
      ${renderStatusBar(true)}
      <div class="os2-shell">
        <div class="strict-gate-box">
          <h3>&#128274; OS 2 Strictly Locked</h3>
          <p>
            No Root (<code>su</code>) or Shizuku binder detected. Per your architecture policy, OS 2 stays completely locked &amp; hidden with zero non-privileged fallback.
          </p>
          <div style="display:flex; gap:0.45rem; justify-content:center; flex-wrap:wrap; margin-top:0.25rem;">
            <button type="button" class="btn btn-sm btn-primary" id="grant-shizuku-now">Simulate Shizuku Grant</button>
            <button type="button" class="btn btn-sm btn-dark" id="back-to-os1-locked">Back to OS 1</button>
          </div>
        </div>
      </div>
    `;

    els.phoneScreen.querySelector('#grant-shizuku-now')?.addEventListener('click', () => {
      config.os2.privilegeSimulated = 'shizuku';
      saveConfig(config);
      renderStudioControls();
      runViewTransition(() => renderPhoneScreen(), els.phoneScreen);
    });
    els.phoneScreen.querySelector('#back-to-os1-locked')?.addEventListener('click', () => {
      runViewTransition(() => {
        ui.activeScreen = 'os1';
        renderPhoneScreen();
      }, els.phoneScreen);
    });
    return;
  }

  const isChameleon = config.os2.style === 'chameleon-persona-spaces';
  const personas = ['Private Vault', 'Work', 'Travel', 'Guest'];
  const styleMeta = OS2_STYLES.find((s) => s.id === config.os2.style);

  const engineHtml =
    config.os2.engineMode === 'app-freezing'
      ? `
        <div class="freezer-card">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:0.35rem;">
            <strong style="font-size:0.74rem;">&#10052;&#65039; App Freezer &amp; Hiding (${config.os2.privilegeSimulated.toUpperCase()})</strong>
            <span style="font-size:0.64rem; color:#7dd3fc;">pm suspend / hide</span>
          </div>
          ${ui.freezerState
            .map(
              (app) => `
            <div class="freezer-row">
              <div>
                <div style="font-weight:600;">${app.name}</div>
                <div style="font-size:0.62rem; opacity:0.7; font-family:var(--font-mono);">${app.pkg}</div>
              </div>
              <button type="button" class="freeze-btn ${app.frozenInOs1 ? 'frozen' : ''}" data-toggle-freeze="${app.id}">
                ${app.frozenInOs1 ? 'Frozen in OS 1' : 'Shared'}
              </button>
            </div>
          `,
            )
            .join('')}
        </div>
      `
      : `
        <div class="freeform-stage" id="freeform-stage" aria-label="Freeform Floating Windows Canvas">
          ${ui.windows
            .map(
              (win) => `
            <div class="aero-window" data-win-id="${win.id}" style="left:${win.x}px; top:${win.y}px; z-index:${win.z};">
              <div class="aero-win-bar" data-drag-win="${win.id}">
                <span>${win.title}</span>
                <span aria-hidden="true">&#9633; &times;</span>
              </div>
              <div class="aero-win-body">${win.body.replace(/\n/g, '<br>')}</div>
            </div>
          `,
            )
            .join('')}
        </div>
      `;

  els.phoneScreen.innerHTML = `
    ${renderStatusBar(true)}
    <div class="os2-shell">
      <div class="os2-header">
        <span class="os2-title">OS 2 &bull; ${styleMeta?.name || 'Deep OS'}</span>
        <button type="button" class="taskbar-pill" id="sim-screen-off-btn" title="Simulate screen-off auto-lock to OS 1">
          &#128274; Screen-Off Lock
        </button>
      </div>

      <div class="os2-workspace">
        ${
          isChameleon
            ? `
          <div class="os2-persona-strip" role="group" aria-label="Chameleon Persona Spaces">
            ${personas
              .map(
                (p) => `
              <button type="button" class="persona-pill ${config.os2.activePersona === p ? 'active' : ''}" data-persona="${p}">${p}</button>
            `,
              )
              .join('')}
          </div>
        `
            : ''
        }

        ${engineHtml}

        <div class="shell-log" aria-label="Privilege Execution Log">
          ${ui.shellLog.slice(-3).map((line) => `<div>${line}</div>`).join('')}
        </div>
      </div>

      <div class="os2-taskbar">
        <button type="button" class="start-orb-btn" id="os2-start-orb" title="Switch back to OS 1" aria-label="OS 2 Start Orb — Lock and return to OS 1"></button>
        <button type="button" class="taskbar-pill" id="os2-switch-engine">
          ${config.os2.engineMode === 'app-freezing' ? 'Try Freeform' : 'Try Freezer'}
        </button>
        <button type="button" class="taskbar-pill" id="os2-lock-btn">Return OS 1</button>
      </div>
    </div>
  `;

  const lockToOs1 = () => {
    runViewTransition(() => {
      ui.activeScreen = 'os1';
      renderPhoneScreen();
    }, els.phoneScreen);
  };

  els.phoneScreen.querySelector('#sim-screen-off-btn')?.addEventListener('click', lockToOs1);
  els.phoneScreen.querySelector('#os2-lock-btn')?.addEventListener('click', lockToOs1);
  els.phoneScreen.querySelector('#os2-start-orb')?.addEventListener('click', lockToOs1);

  els.phoneScreen.querySelector('#os2-switch-engine')?.addEventListener('click', () => {
    config.os2.engineMode = config.os2.engineMode === 'app-freezing' ? 'freeform-windows' : 'app-freezing';
    saveConfig(config);
    renderStudioControls();
    runViewTransition(() => renderPhoneScreen(), els.phoneScreen);
  });

  els.phoneScreen.querySelectorAll('[data-persona]').forEach((btn) => {
    btn.addEventListener('click', () => {
      config.os2.activePersona = btn.dataset.persona;
      ui.shellLog.push(`$ hyaline-space --activate "${btn.dataset.persona}"`);
      saveConfig(config);
      renderOs2Workspace();
    });
  });

  els.phoneScreen.querySelectorAll('[data-toggle-freeze]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const target = ui.freezerState.find((a) => a.id === btn.dataset.toggleFreeze);
      if (!target) return;
      target.frozenInOs1 = !target.frozenInOs1;
      const cmd = target.frozenInOs1 ? 'pm disable-user --user 0' : 'pm enable';
      ui.shellLog.push(`$ ${config.os2.privilegeSimulated} ${cmd} ${target.pkg}`);
      renderOs2Workspace();
    });
  });

  // Draggable freeform windows inside the phone stage
  els.phoneScreen.querySelectorAll('[data-drag-win]').forEach((bar) => {
    bar.addEventListener('pointerdown', (e) => {
      const winId = bar.dataset.dragWin;
      const winObj = ui.windows.find((w) => w.id === winId);
      const winEl = els.phoneScreen.querySelector(`[data-win-id="${winId}"]`);
      const stageEl = els.phoneScreen.querySelector('#freeform-stage');
      if (!winObj || !winEl || !stageEl) return;

      winObj.z = ++ui.topWindowZ;
      winEl.style.zIndex = String(winObj.z);
      const startX = e.clientX - winObj.x;
      const startY = e.clientY - winObj.y;
      bar.setPointerCapture(e.pointerId);

      const onMove = (ev) => {
        const maxX = Math.max(0, stageEl.clientWidth - winEl.offsetWidth);
        const maxY = Math.max(0, stageEl.clientHeight - winEl.offsetHeight);
        winObj.x = Math.min(maxX, Math.max(0, ev.clientX - startX));
        winObj.y = Math.min(maxY, Math.max(0, ev.clientY - startY));
        winEl.style.left = `${winObj.x}px`;
        winEl.style.top = `${winObj.y}px`;
      };

      const onUp = () => {
        bar.removeEventListener('pointermove', onMove);
        bar.removeEventListener('pointerup', onUp);
      };

      bar.addEventListener('pointermove', onMove);
      bar.addEventListener('pointerup', onUp);
    });
  });
}

function renderStudioControls() {
  // OS 1 Preset Cards
  els.os1PresetGrid.innerHTML = OS1_STYLES.map(
    (s) => `
    <button type="button" class="preset-card" data-os1-preset="${s.id}" aria-pressed="${config.os1.style === s.id}">
      <span class="preset-swatch" style="background: ${s.swatch};" aria-hidden="true"></span>
      <span class="preset-name">${s.name}</span>
      <span class="preset-meta">${s.desc}</span>
    </button>
  `,
  ).join('');

  els.os1PresetGrid.querySelectorAll('[data-os1-preset]').forEach((btn) => {
    btn.addEventListener('click', () => {
      config.os1.style = btn.dataset.os1Preset;
      saveConfig(config);
      syncCssTokens();
      renderStudioControls();
      runViewTransition(() => {
        ui.activeScreen = 'os1';
        renderPhoneScreen();
      });
    });
  });

  // OS 2 Preset Cards
  els.os2PresetGrid.innerHTML = OS2_STYLES.map(
    (s) => `
    <button type="button" class="preset-card" data-os2-preset="${s.id}" aria-pressed="${config.os2.style === s.id}">
      <span class="preset-swatch" style="background: ${s.swatch};" aria-hidden="true"></span>
      <span class="preset-name">${s.name}</span>
      <span class="preset-meta">${s.desc}</span>
    </button>
  `,
  ).join('');

  els.os2PresetGrid.querySelectorAll('[data-os2-preset]').forEach((btn) => {
    btn.addEventListener('click', () => {
      config.os2.style = btn.dataset.os2Preset;
      saveConfig(config);
      syncCssTokens();
      renderStudioControls();
      runViewTransition(() => {
        ui.activeScreen = 'os2';
        renderPhoneScreen();
      });
    });
  });

  // Accent Swatches + Custom Color Input
  els.accentSwatches.innerHTML = `
    ${ACCENT_SWATCHES.map(
      (hex) => `
      <button
        type="button"
        class="color-dot"
        data-accent="${hex}"
        style="background: ${hex};"
        aria-label="Set accent color ${hex}"
        aria-pressed="${config.os1.accentHex.toLowerCase() === hex.toLowerCase()}"
      ></button>
    `,
    ).join('')}
    <label class="color-picker-wrap">
      <input type="color" id="custom-accent-input" value="${config.os1.accentHex}" aria-label="Pick custom Aero accent color" />
      <span>Custom</span>
    </label>
  `;

  els.accentSwatches.querySelectorAll('[data-accent]').forEach((btn) => {
    btn.addEventListener('click', () => {
      config.os1.accentHex = btn.dataset.accent;
      saveConfig(config);
      syncCssTokens();
      renderStudioControls();
      renderPhoneScreen();
    });
  });

  els.accentSwatches.querySelector('#custom-accent-input')?.addEventListener('input', (e) => {
    config.os1.accentHex = e.target.value;
    saveConfig(config);
    syncCssTokens();
    renderPhoneScreen();
  });

  // Wallpaper Pills
  els.wallpaperPills.innerHTML = WALLPAPERS.map(
    (w) => `
    <button type="button" class="seg-btn" data-wp="${w.id}" aria-pressed="${config.os1.wallpaperPreset === w.id}">
      ${w.label}
    </button>
  `,
  ).join('');

  els.wallpaperPills.querySelectorAll('[data-wp]').forEach((btn) => {
    btn.addEventListener('click', () => {
      config.os1.wallpaperPreset = btn.dataset.wp;
      saveConfig(config);
      syncCssTokens();
      renderStudioControls();
    });
  });

  // Privilege Simulation Pills
  const privOpts = [
    { id: 'shizuku', label: 'Shizuku Active' },
    { id: 'root', label: 'Root (su)' },
    { id: 'none', label: 'None (Locked)' },
  ];
  els.privSimGroup.innerHTML = privOpts
    .map(
      (p) => `
    <button type="button" class="seg-btn" data-priv="${p.id}" aria-pressed="${config.os2.privilegeSimulated === p.id}">
      ${p.label}
    </button>
  `,
    )
    .join('');

  els.privSimGroup.querySelectorAll('[data-priv]').forEach((btn) => {
    btn.addEventListener('click', () => {
      config.os2.privilegeSimulated = btn.dataset.priv;
      saveConfig(config);
      renderStudioControls();
      renderPhoneScreen();
    });
  });

  // Engine Mode & Door Trigger buttons
  els.engineModeGroup.querySelectorAll('[data-engine]').forEach((btn) => {
    btn.setAttribute('aria-pressed', String(config.os2.engineMode === btn.dataset.engine));
  });

  els.doorTriggerGroup.querySelectorAll('[data-door]').forEach((btn) => {
    btn.setAttribute('aria-pressed', String(config.os2.doorTrigger === btn.dataset.door));
  });

  renderTileInspector();
}

function renderTileInspector() {
  const tile = config.os1.tiles.find((t) => t.id === ui.selectedTileId);
  if (!tile) {
    els.tileInspector.innerHTML = `<span class="inspector-title">Click any tile on the phone to resize (1&times;1, 2&times;2, 4&times;2) or customize its Aero tint.</span>`;
    return;
  }

  els.tileInspector.innerHTML = `
    <span class="inspector-title">${tile.icon} ${tile.label}</span>
    <div class="seg-group" role="group" aria-label="Resize selected tile">
      ${['1x1', '2x2', '4x2']
        .map(
          (sz) => `
        <button type="button" class="seg-btn" data-resize="${sz}" aria-pressed="${tile.size === sz}">${sz}</button>
      `,
        )
        .join('')}
    </div>
    <label class="color-picker-wrap">
      <input type="color" id="tile-color-input" value="${tile.accentOverride || config.os1.accentHex}" aria-label="Tile custom tint" />
      <span>Tile Tint</span>
    </label>
    <button type="button" class="btn btn-sm" id="inspector-open-app">Preview App</button>
    <button type="button" class="btn btn-sm btn-ghost" id="inspector-remove-tile">Remove</button>
  `;

  els.tileInspector.querySelectorAll('[data-resize]').forEach((btn) => {
    btn.addEventListener('click', () => {
      tile.size = btn.dataset.resize;
      saveConfig(config);
      runViewTransition(() => {
        renderPhoneScreen();
        renderTileInspector();
      });
    });
  });

  els.tileInspector.querySelector('#tile-color-input')?.addEventListener('input', (e) => {
    tile.accentOverride = e.target.value;
    saveConfig(config);
    renderPhoneScreen();
  });

  els.tileInspector.querySelector('#inspector-open-app')?.addEventListener('click', () => {
    openMicroApp(tile.id);
  });

  els.tileInspector.querySelector('#inspector-remove-tile')?.addEventListener('click', () => {
    if (config.os1.tiles.length <= 2) return;
    runViewTransition(() => {
      config.os1.tiles = config.os1.tiles.filter((t) => t.id !== tile.id);
      ui.selectedTileId = config.os1.tiles[0]?.id ?? null;
      saveConfig(config);
      renderPhoneScreen();
      renderTileInspector();
    });
  });
}

/* First-Boot OOBE 4-Step Wizard */
function renderOobeStep() {
  const pills = els.oobePills.querySelectorAll('.oobe-pill');
  pills.forEach((p, idx) => p.classList.toggle('done', idx < ui.oobeStep));
  els.oobePrevBtn.disabled = ui.oobeStep === 1;
  els.oobeNextBtn.textContent = ui.oobeStep === 4 ? 'Finish & Launch hyalineOS' : 'Next Step →';

  if (ui.oobeStep === 1) {
    els.oobeSubtitle.textContent = 'Step 1 of 4 • Choose your OS 1 Metro × Frutiger Aero Lite style';
    els.oobeBody.innerHTML = `
      <div class="preset-grid">
        ${OS1_STYLES.map(
          (s) => `
          <button type="button" class="preset-card" data-oobe-os1="${s.id}" aria-pressed="${config.os1.style === s.id}">
            <span class="preset-swatch" style="background:${s.swatch};"></span>
            <span class="preset-name">${s.name}</span>
            <span class="preset-meta">${s.desc}</span>
          </button>
        `,
        ).join('')}
      </div>
    `;
    els.oobeBody.querySelectorAll('[data-oobe-os1]').forEach((btn) => {
      btn.addEventListener('click', () => {
        config.os1.style = btn.dataset.oobeOs1;
        saveConfig(config);
        syncCssTokens();
        renderStudioControls();
        renderPhoneScreen();
        renderOobeStep();
      });
    });
  } else if (ui.oobeStep === 2) {
    els.oobeSubtitle.textContent = 'Step 2 of 4 • Privilege Check (Strict Root / Shizuku Gate for OS 2)';
    const granted = config.os2.privilegeSimulated !== 'none';
    els.oobeBody.innerHTML = `
      <div class="notice-banner ${granted ? 'ok' : ''}">
        ${
          granted
            ? `<strong>${config.os2.privilegeSimulated.toUpperCase()} detected!</strong> OS 2 Deep OS setup is unlocked.`
            : '<strong>No Root or Shizuku detected.</strong> Under the strict privilege gate, OS 2 will remain completely locked &amp; hidden until granted.'
        }
      </div>
      <p class="card-desc">Simulate your Android device privilege level:</p>
      <div class="seg-group">
        <button type="button" class="seg-btn" data-oobe-priv="shizuku" aria-pressed="${config.os2.privilegeSimulated === 'shizuku'}">Shizuku Granted</button>
        <button type="button" class="seg-btn" data-oobe-priv="root" aria-pressed="${config.os2.privilegeSimulated === 'root'}">Root (su) Granted</button>
        <button type="button" class="seg-btn" data-oobe-priv="none" aria-pressed="${config.os2.privilegeSimulated === 'none'}">No Privileges (OS 1 Lite Only)</button>
      </div>
    `;
    els.oobeBody.querySelectorAll('[data-oobe-priv]').forEach((btn) => {
      btn.addEventListener('click', () => {
        config.os2.privilegeSimulated = btn.dataset.oobePriv;
        saveConfig(config);
        renderStudioControls();
        renderPhoneScreen();
        renderOobeStep();
      });
    });
  } else if (ui.oobeStep === 3) {
    els.oobeSubtitle.textContent = 'Step 3 of 4 • Choose OS 2 Deep Style & Primary Engine Mode';
    els.oobeBody.innerHTML = `
      <div class="preset-grid">
        ${OS2_STYLES.map(
          (s) => `
          <button type="button" class="preset-card" data-oobe-os2="${s.id}" aria-pressed="${config.os2.style === s.id}">
            <span class="preset-swatch" style="background:${s.swatch};"></span>
            <span class="preset-name">${s.name}</span>
            <span class="preset-meta">${s.desc}</span>
          </button>
        `,
        ).join('')}
      </div>
      <div style="margin-top:0.85rem;">
        <p class="control-label" style="margin-bottom:0.4rem;">Choose OS 2 Engine Mode:</p>
        <div class="seg-group">
          <button type="button" class="seg-btn" data-oobe-eng="app-freezing" aria-pressed="${config.os2.engineMode === 'app-freezing'}">&#10052;&#65039; App Freezing &amp; Hiding</button>
          <button type="button" class="seg-btn" data-oobe-eng="freeform-windows" aria-pressed="${config.os2.engineMode === 'freeform-windows'}">&#129695; Freeform Floating Windows</button>
        </div>
      </div>
    `;
    els.oobeBody.querySelectorAll('[data-oobe-os2]').forEach((btn) => {
      btn.addEventListener('click', () => {
        config.os2.style = btn.dataset.oobeOs2;
        saveConfig(config);
        syncCssTokens();
        renderStudioControls();
        renderOobeStep();
      });
    });
    els.oobeBody.querySelectorAll('[data-oobe-eng]').forEach((btn) => {
      btn.addEventListener('click', () => {
        config.os2.engineMode = btn.dataset.oobeEng;
        saveConfig(config);
        renderStudioControls();
        renderOobeStep();
      });
    });
  } else {
    els.oobeSubtitle.textContent = 'Step 4 of 4 • Configurable Door + Smart Lock Ready';
    els.oobeBody.innerHTML = `
      <div class="notice-banner ok">
        <strong>hyalineOS is configured!</strong><br />
        &bull; OS 1: <code>${config.os1.style}</code><br />
        &bull; OS 2: <code>${config.os2.style}</code> (${config.os2.engineMode})<br />
        &bull; Smart Lock: PIN <code>1337</code> opens OS 2 &bull; Decoy PIN <code>0000</code> opens Guest Space.
      </div>
    `;
  }
}

/* OS 2 Configurable Door Dialog Logic */
function openDoorDialog() {
  ui.pinBuffer = '';
  updatePinDots();
  els.doorStatusMsg.hidden = true;
  els.doorDialog.showModal();
}

function updatePinDots() {
  const dots = els.pinDisplay.querySelectorAll('.pin-dot');
  dots.forEach((dot, i) => dot.classList.toggle('filled', i < ui.pinBuffer.length));
}

function evaluatePin() {
  if (ui.pinBuffer === '1337') {
    closeTopLayer(els.doorDialog);
    runViewTransition(() => {
      ui.activeScreen = 'os2';
      renderPhoneScreen();
    }, els.phoneScreen);
  } else if (ui.pinBuffer === '0000') {
    closeTopLayer(els.doorDialog);
    runViewTransition(() => {
      ui.activeScreen = 'decoy-guest';
      renderPhoneScreen();
    }, els.phoneScreen);
  } else {
    els.doorStatusMsg.hidden = false;
    els.doorStatusMsg.textContent = 'Incorrect PIN. Demo hint: 1337 opens OS 2, 0000 opens Decoy Guest Space.';
    ui.pinBuffer = '';
    updatePinDots();
  }
}

function wireEvents() {
  els.tabOs1.addEventListener('click', () => {
    runViewTransition(() => {
      ui.activeScreen = 'os1';
      renderPhoneScreen();
    }, els.phoneScreen);
  });

  els.tabOs2.addEventListener('click', () => {
    if (config.os2.privilegeSimulated === 'none') {
      runViewTransition(() => {
        ui.activeScreen = 'os2';
        renderPhoneScreen();
      }, els.phoneScreen);
      return;
    }
    openDoorDialog();
  });

  els.rangeOpacity.addEventListener('input', (e) => {
    config.os1.glassOpacity = Number(e.target.value) / 100;
    saveConfig(config);
    syncCssTokens();
  });

  els.rangeBlur.addEventListener('input', (e) => {
    config.os1.glassBlurPx = Number(e.target.value);
    saveConfig(config);
    syncCssTokens();
  });

  els.rangeRadius.addEventListener('input', (e) => {
    config.os1.tileRadiusPx = Number(e.target.value);
    saveConfig(config);
    syncCssTokens();
  });

  els.engineModeGroup.querySelectorAll('[data-engine]').forEach((btn) => {
    btn.addEventListener('click', () => {
      config.os2.engineMode = btn.dataset.engine;
      saveConfig(config);
      renderStudioControls();
      if (ui.activeScreen === 'os2') {
        runViewTransition(() => renderPhoneScreen(), els.phoneScreen);
      }
    });
  });

  els.doorTriggerGroup.querySelectorAll('[data-door]').forEach((btn) => {
    btn.addEventListener('click', () => {
      config.os2.doorTrigger = btn.dataset.door;
      saveConfig(config);
      renderStudioControls();
      renderPhoneScreen();
    });
  });

  document.getElementById('add-tile-btn')?.addEventListener('click', () => {
    const existingIds = new Set(config.os1.tiles.map((t) => t.id));
    const candidate = EXTRA_TILE_CATALOG.find((c) => !existingIds.has(c.id)) || {
      id: `custom-${Date.now().toString(36)}`,
      label: 'Web Shortcut',
      packageName: 'os.hyaline.shortcut',
      icon: '🌐',
      size: '2x2',
      accentOverride: config.os1.accentHex,
      liveHeadline: 'Pinned Shortcut',
      liveSub: 'Custom Aero tile',
      badge: '',
      category: 'Custom Tile',
    };
    runViewTransition(() => {
      config.os1.tiles.push({ ...candidate });
      ui.selectedTileId = candidate.id;
      saveConfig(config);
      ui.activeScreen = 'os1';
      renderPhoneScreen();
      renderTileInspector();
    });
  });

  document.getElementById('reset-config-btn')?.addEventListener('click', () => {
    config = createDefaultConfig();
    saveConfig(config);
    ui.selectedTileId = config.os1.tiles[0]?.id ?? null;
    syncCssTokens();
    renderStudioControls();
    runViewTransition(() => {
      ui.activeScreen = 'os1';
      renderPhoneScreen();
    }, els.phoneScreen);
  });

  // OOBE wizard events
  els.oobeDialog.addEventListener('toggle', () => {});
  document.querySelector('[commandfor="oobe-dialog"]')?.addEventListener('click', () => {
    ui.oobeStep = 1;
    renderOobeStep();
  });

  els.oobePrevBtn.addEventListener('click', () => {
    if (ui.oobeStep > 1) {
      ui.oobeStep -= 1;
      renderOobeStep();
    }
  });

  els.oobeNextBtn.addEventListener('click', () => {
    if (ui.oobeStep < 4) {
      ui.oobeStep += 1;
      renderOobeStep();
    } else {
      closeTopLayer(els.oobeDialog);
      runViewTransition(() => {
        ui.activeScreen = 'os1';
        renderPhoneScreen();
      }, els.phoneScreen);
    }
  });

  // PIN keypad events
  els.pinPad.addEventListener('click', (e) => {
    const btn = e.target.closest('button');
    if (!btn) return;
    if (btn.dataset.digit) {
      if (ui.pinBuffer.length < 4) {
        ui.pinBuffer += btn.dataset.digit;
        updatePinDots();
        if (ui.pinBuffer.length === 4) {
          setTimeout(evaluatePin, 120);
        }
      }
    } else if (btn.dataset.action === 'clear') {
      ui.pinBuffer = '';
      updatePinDots();
    } else if (btn.dataset.action === 'bio') {
      ui.pinBuffer = '1337';
      updatePinDots();
      setTimeout(evaluatePin, 100);
    }
  });

  document.getElementById('quick-pin-os2')?.addEventListener('click', () => {
    ui.pinBuffer = '1337';
    updatePinDots();
    setTimeout(evaluatePin, 100);
  });

  document.getElementById('quick-pin-decoy')?.addEventListener('click', () => {
    ui.pinBuffer = '0000';
    updatePinDots();
    setTimeout(evaluatePin, 100);
  });

  // Config Export / Import Dialog
  document.getElementById('open-config-btn')?.addEventListener('click', () => {
    els.configTextarea.value = exportPortableJson(config);
    els.configFeedback.hidden = true;
  });

  document.getElementById('copy-json-btn')?.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(els.configTextarea.value);
      els.configFeedback.hidden = false;
      els.configFeedback.textContent = 'Copied hyalineos-config.json to clipboard!';
    } catch {
      els.configTextarea.select();
    }
  });

  document.getElementById('download-json-btn')?.addEventListener('click', () => {
    const blob = new Blob([els.configTextarea.value], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'hyalineos-config.json';
    a.click();
    URL.revokeObjectURL(url);
    els.configFeedback.hidden = false;
    els.configFeedback.textContent = 'Downloaded hyalineos-config.json — ready to import on Android!';
  });

  document.getElementById('apply-json-btn')?.addEventListener('click', () => {
    try {
      const parsed = JSON.parse(els.configTextarea.value);
      config = normalizeConfig(parsed);
      saveConfig(config);
      syncCssTokens();
      renderStudioControls();
      renderPhoneScreen();
      els.configFeedback.hidden = false;
      els.configFeedback.textContent = 'Applied imported config live!';
    } catch (err) {
      els.configFeedback.hidden = false;
      els.configFeedback.textContent = `Invalid JSON: ${err.message}`;
    }
  });

  // Studio Light/Dark Override Toggle (independent of OS 1 / OS 2 component color-schemes!)
  els.toggleStudioThemeBtn.addEventListener('click', () => {
    const root = document.documentElement;
    const current = root.dataset.studioTheme;
    const next = current === 'dark' ? 'light' : 'dark';
    root.dataset.studioTheme = next;
    els.toggleStudioThemeBtn.setAttribute('aria-pressed', String(next === 'dark'));
    els.toggleStudioThemeBtn.textContent = next === 'dark' ? '🌙 Studio Dark' : '☀️ Studio Light';
  });

  // Popover focus restoration & ARIA sync (per declarative-dialog-popover-control)
  const doorPopover = document.getElementById('door-help-popover');
  const doorPopoverTrigger = document.querySelector('[commandfor="door-help-popover"][command="toggle-popover"]');
  if (doorPopover && doorPopoverTrigger) {
    doorPopoverTrigger.setAttribute('aria-expanded', 'false');
    doorPopover.addEventListener('toggle', (event) => {
      const isOpen = event.newState === 'open';
      doorPopoverTrigger.setAttribute('aria-expanded', String(isOpen));
      if (!isOpen) {
        doorPopoverTrigger.focus({ preventScroll: true });
      }
    });
  }

  // Keep live clock ticking every 15s
  setInterval(() => {
    if (ui.activeScreen === 'os1') {
      const clock = els.phoneScreen.querySelector('#phone-clock-door');
      if (clock) clock.textContent = getFormattedTime();
    }
  }, 15000);
}

// Initialize
enhanceDialogs();
syncCssTokens();
renderStudioControls();
renderPhoneScreen();
renderOobeStep();
wireEvents();
