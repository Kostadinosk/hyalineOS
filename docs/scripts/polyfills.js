/* hyalineOS Studio — Feature-Detected Polyfills & Top-Layer Fallbacks
 * Follows modern-web-guidance:
 * - declarative-dialog-popover-control (conditional invokers-polyfill & popover-polyfill)
 * - light-dismiss-a-dialog (closedBy fallback for Safari/older browsers)
 * - animate-to-from-top-layer (lazy canTransitionDisplay probe + data-closing exit helper)
 */

// 1. Conditionally load Invoker Commands polyfill ONLY when native support is missing
if (!('commandForElement' in HTMLButtonElement.prototype)) {
  import('https://esm.run/invokers-polyfill').catch(() => {});
}

// 2. Conditionally load Popover polyfill ONLY when native support is missing
if (!Object.hasOwn(HTMLElement.prototype, 'popover')) {
  import('https://unpkg.com/@oddbird/popover-polyfill@latest/dist/popover.min.js').catch(() => {});
}

// 3. Lazy probe for discrete display transitions (guards against Firefox bug 1882408)
let supportsDisplayTransition;

export function canTransitionDisplay() {
  if (supportsDisplayTransition !== undefined) return supportsDisplayTransition;
  if (!window.CSS?.supports?.('transition-behavior', 'allow-discrete') || !document.body) {
    return false;
  }
  const probe = document.createElement('div');
  probe.style.cssText = 'transition: display 1s allow-discrete !important; display: block;';
  document.body.appendChild(probe);
  getComputedStyle(probe).display;
  probe.style.display = 'none';
  supportsDisplayTransition = getComputedStyle(probe).display === 'block';
  probe.remove();
  return supportsDisplayTransition;
}

export function supportsTopLayerExit() {
  return Boolean(window.CSS?.supports?.('overlay', 'auto')) && canTransitionDisplay();
}

export async function closeTopLayer(element) {
  if (!element) return;
  const isDialog = element instanceof HTMLDialogElement;
  if (isDialog && !element.open) return;

  if (!supportsTopLayerExit()) {
    element.setAttribute('data-closing', '');
    const animations = element.getAnimations({ subtree: true });
    if (animations.length > 0) {
      await Promise.race([
        Promise.allSettled(animations.map((a) => a.finished)),
        new Promise((resolve) => setTimeout(resolve, 2000)),
      ]);
    }
    if (!element.hasAttribute('data-closing')) return;
    element.removeAttribute('data-closing');
  }

  if (isDialog) {
    element.close();
  } else if (typeof element.hidePopover === 'function') {
    element.hidePopover();
  }
}

/**
 * Attach mandatory light-dismiss fallback (when closedBy is unsupported)
 * and animated cancel/exit routing for all <dialog closedby="any"> elements.
 */
export function enhanceDialogs() {
  const dialogs = document.querySelectorAll('dialog');

  dialogs.forEach((dialog) => {
    // Route native close requests (Esc / closedby light dismiss) through animated helper
    dialog.addEventListener('cancel', (event) => {
      if (!supportsTopLayerExit()) {
        event.preventDefault();
        closeTopLayer(dialog);
      }
    });

    // Mandatory fallback for browsers without closedby="any" support (e.g. Safari)
    if (!('closedBy' in HTMLDialogElement.prototype) && dialog.getAttribute('closedby') === 'any') {
      dialog.addEventListener('click', (event) => {
        if (event.target !== dialog) return;
        const rect = dialog.getBoundingClientRect();
        const isDialogContent =
          rect.top <= event.clientY &&
          event.clientY <= rect.top + rect.height &&
          rect.left <= event.clientX &&
          event.clientX <= rect.left + rect.width;
        if (isDialogContent) return;
        closeTopLayer(dialog);
      });
    }
  });
}
