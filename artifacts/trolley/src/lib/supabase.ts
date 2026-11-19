import { createClient } from '@supabase/supabase-js';

const url = import.meta.env.VITE_SUPABASE_URL as string | undefined;
const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined;
export const isSupabaseConfigured = Boolean(url && anonKey && !url.includes('YOUR_PROJECT'));
export const supabase = isSupabaseConfigured ? createClient(url!, anonKey!, {
  auth: { autoRefreshToken: true, persistSession: true, detectSessionInUrl: true },
}) : null;

// Keep confirmation and OAuth callbacks inside this artifact's mounted path.
export function authRedirectUrl() {
  return new URL(import.meta.env.BASE_URL, window.location.origin).href;
}

export function recoveryRedirectUrl() {
  const redirect = new URL(authRedirectUrl());
  redirect.searchParams.set('auth', 'recovery');
  return redirect.href;
}

// Capture before the SDK consumes the URL fragment during initialization.
export const isRecoveryRedirect = typeof window !== 'undefined' && (
  new URLSearchParams(window.location.search).get('auth') === 'recovery'
  || new URLSearchParams(window.location.hash.slice(1)).get('type') === 'recovery'
);

export const hasRecoveryCredentials = typeof window !== 'undefined' && (
  new URLSearchParams(window.location.hash.slice(1)).has('access_token')
  || new URLSearchParams(window.location.search).has('code')
);
export const hasRecoveryError = typeof window !== 'undefined' && (
  new URLSearchParams(window.location.hash.slice(1)).has('error')
  || new URLSearchParams(window.location.search).has('error')
);
