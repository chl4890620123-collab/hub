import { useEffect, type ReactNode } from 'react';
import { useAppStore } from '@/stores/appStore';

const THEME_STORAGE_KEY = 'hub.theme';

/**
 * Dark-mode variable overrides, applied as inline styles on <html> instead of a
 * [data-theme="dark"] CSS rule in globals.css: a plain top-level rule redefining these same
 * variables compiled correctly but was silently dropped from the live stylesheet by this
 * Tailwind v4 setup, for reasons not fully root-caused. Inline styles always win the cascade
 * regardless of that, and index.html mirrors this same map for the pre-mount FOUC guard.
 */
const DARK_OVERRIDES: Record<string, string> = {
  '--color-ink-50': '#010102',
  '--color-ink-100': '#0f1011',
  '--color-ink-200': '#1c1d1f',
  '--color-ink-300': '#2a2b2e',
  '--color-ink-400': '#55585f',
  '--color-ink-500': '#797d85',
  '--color-ink-600': '#9a9ea6',
  '--color-ink-700': '#bcbfc5',
  '--color-ink-800': '#dcdee1',
  '--color-ink-900': '#f7f8f8',
  '--color-accent-50': '#1b1e35',
  '--color-accent-600': '#828fff',
  '--color-accent-700': '#a6b0e8',
  '--color-danger-500': '#f87171',
  '--color-warning-500': '#fbbf24',
  '--color-red-50': '#2a1215',
  '--color-red-100': '#3f1a1e',
  '--color-red-200': '#7f1d1d',
  '--color-red-500': '#f87171',
  '--color-red-600': '#f87171',
  '--color-red-700': '#fca5a5',
  '--color-amber-50': '#2a1f0a',
  '--color-amber-100': '#3f2e10',
  '--color-amber-700': '#fbbf24',
};

function applyTheme(isDark: boolean) {
  const root = document.documentElement;
  if (isDark) {
    root.setAttribute('data-theme', 'dark');
    for (const [prop, value] of Object.entries(DARK_OVERRIDES)) root.style.setProperty(prop, value);
  } else {
    root.removeAttribute('data-theme');
    for (const prop of Object.keys(DARK_OVERRIDES)) root.style.removeProperty(prop);
  }
}

/**
 * Mirrors themeMode onto <html data-theme> and localStorage. Kept separate from the zustand
 * persist blob (see index.html's inline script) so the pre-mount FOUC guard can read a plain
 * string instead of parsing the store's JSON shape.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const themeMode = useAppStore((state) => state.themeMode);

  useEffect(() => {
    localStorage.setItem(THEME_STORAGE_KEY, themeMode);

    if (themeMode !== 'system') {
      applyTheme(themeMode === 'dark');
      return;
    }

    const media = window.matchMedia('(prefers-color-scheme: dark)');
    applyTheme(media.matches);
    const onChange = (e: MediaQueryListEvent) => applyTheme(e.matches);
    media.addEventListener('change', onChange);
    return () => media.removeEventListener('change', onChange);
  }, [themeMode]);

  return children;
}
