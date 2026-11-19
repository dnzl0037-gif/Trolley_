import { Capacitor } from '@capacitor/core';
import { App } from '@capacitor/app';
import { Browser } from '@capacitor/browser';
import { authRedirectUrl, supabase } from './supabase';

const scheme = 'com.trolley.marketplace://';

// Opens the app's own page when an email or Google link returns to the app.
export function setupDeepLinks() {
  if (!Capacitor.isNativePlatform()) return;
  void App.addListener('appUrlOpen', ({ url }) => {
    try {
      if (!url.startsWith(scheme)) return;
      const u = new URL(url);
      const search = u.search ? `${u.search}&` : '?';
      void Browser.close().catch(() => undefined);
      window.location.href = `/${search}dl=${Date.now()}${u.hash}`;
    } catch { /* ignore malformed links */ }
  });
}

export async function startGoogleSignIn() {
  if (!supabase) throw new Error('Supabase is not connected.');
  const native = Capacitor.isNativePlatform();
  const { data, error } = await supabase.auth.signInWithOAuth({
    provider: 'google',
    options: { redirectTo: authRedirectUrl(), skipBrowserRedirect: native },
  });
  if (error) throw error;
  if (native && data?.url) await Browser.open({ url: data.url });
}
