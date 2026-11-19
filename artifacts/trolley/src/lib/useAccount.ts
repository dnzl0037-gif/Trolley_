import { useEffect, useState } from 'react';
import type { User } from '@supabase/supabase-js';
import { supabase } from './supabase';

export function useAccount() {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(Boolean(supabase));
  const [error, setError] = useState('');

  useEffect(() => {
    if (!supabase) return;
    let active = true;
    let authEventReceived = false;
    const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
      authEventReceived = true;
      if (active) {
        setUser(session?.user ?? null);
        setLoading(false);
        setError('');
      }
    });
    void supabase.auth.getSession().then(({ data, error: sessionError }) => {
      if (!active || authEventReceived) return;
      setUser(data.session?.user ?? null);
      setError(sessionError?.message ?? '');
      setLoading(false);
    }).catch(() => {
      if (!active || authEventReceived) return;
      setError('Unable to restore your session. Please try signing in again.');
      setLoading(false);
    });
    return () => { active = false; subscription.unsubscribe(); };
  }, []);

  return { user, loading, error };
}
