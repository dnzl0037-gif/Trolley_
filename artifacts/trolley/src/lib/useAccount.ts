import { useEffect, useState } from 'react';
import type { User } from '@supabase/supabase-js';
import { hasRecoveryCredentials, hasRecoveryError, isRecoveryRedirect, supabase } from './supabase';

type RecoveryState = 'none' | 'checking' | 'ready' | 'invalid';
const recoveryStorageKey = 'trolley-password-recovery-user';

function cleanRecoveryUrl() {
  const url = new URL(window.location.href);
  url.searchParams.delete('auth');
  url.searchParams.delete('code');
  url.searchParams.delete('error');
  url.searchParams.delete('error_code');
  url.searchParams.delete('error_description');
  url.hash = '';
  window.history.replaceState(null, '', url.pathname + url.search);
}

export function useAccount() {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(Boolean(supabase));
  const [error, setError] = useState('');
  const [recovery, setRecovery] = useState<RecoveryState>(isRecoveryRedirect ? 'checking' : 'none');

  function finishRecovery() {
    sessionStorage.removeItem(recoveryStorageKey);
    cleanRecoveryUrl();
    setRecovery('none');
  }

  useEffect(() => {
    if (!supabase) {
      if (isRecoveryRedirect) {
        cleanRecoveryUrl();
        setRecovery('invalid');
      }
      return;
    }
    let active = true;
    let authEventReceived = false;
    let receivedRecoveryEvent = false;
    const { data: { subscription } } = supabase.auth.onAuthStateChange((event, session) => {
      authEventReceived = true;
      if (active) {
        setUser(session?.user ?? null);
        setLoading(false);
        setError('');
        if (event === 'PASSWORD_RECOVERY' && session) {
          receivedRecoveryEvent = true;
          sessionStorage.setItem(recoveryStorageKey, session.user.id);
          setRecovery('ready');
        }
      }
    });
    void supabase.auth.getSession().then(({ data, error: sessionError }) => {
      if (active && isRecoveryRedirect) {
        // A normal existing login must not make an expired/reset URL valid.
        const valid = !hasRecoveryError && (!hasRecoveryCredentials || receivedRecoveryEvent)
          && !sessionError && data.session
          && sessionStorage.getItem(recoveryStorageKey) === data.session.user.id;
        setRecovery(valid ? 'ready' : 'invalid');
        // Do not leave tokens or Supabase error details in browser history.
        const url = new URL(window.location.href);
        url.hash = '';
        url.searchParams.set('auth', 'recovery');
        for (const key of ['code', 'error', 'error_code', 'error_description']) url.searchParams.delete(key);
        window.history.replaceState(null, '', url.pathname + url.search);
      }
      if (!active || authEventReceived) return;
      setUser(data.session?.user ?? null);
      setError(sessionError?.message ?? '');
      setLoading(false);
    }).catch(() => {
      if (active && isRecoveryRedirect) {
        setRecovery('invalid');
        cleanRecoveryUrl();
      }
      if (!active || authEventReceived) return;
      setError('Unable to restore your session. Please try signing in again.');
      setLoading(false);
    });
    return () => { active = false; subscription.unsubscribe(); };
  }, []);

  return { user, loading, error, recovery, finishRecovery };
}
