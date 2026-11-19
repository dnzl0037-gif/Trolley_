import { useEffect, useState } from 'react';
import { supabase } from './supabase';

type ProviderProfile = {
  id: string;
  business_name: string;
  description: string | null;
};

export function useProviderProfile(userId: string | undefined, open: boolean) {
  const [result, setResult] = useState<{
    userId: string;
    profile: ProviderProfile | null;
    error: string;
  } | null>(null);
  const [revision, setRevision] = useState(0);
  const [settledRevision, setSettledRevision] = useState(-1);

  useEffect(() => {
    if (!supabase || !userId || !open) {
      setResult(null);
      setSettledRevision(-1);
      return;
    }
    let active = true;
    setSettledRevision(-1);
    void supabase.from('provider_profiles')
      .select('id,business_name,description')
      .eq('user_id', userId)
      .maybeSingle()
      .then(({ data, error }) => {
        if (!active) return;
        setResult({ userId, profile: error ? null : data, error: error?.message ?? '' });
        setSettledRevision(revision);
      }, () => {
        if (!active) return;
        setResult({ userId, profile: null, error: 'Unable to load your saved provider profile. Please retry.' });
        setSettledRevision(revision);
      });
    return () => { active = false; };
  }, [userId, open, revision]);

  const current = result?.userId === userId && open ? result : null;
  return {
    profile: current?.profile ?? null,
    error: current?.error ?? '',
    loading: Boolean(userId && open && settledRevision !== revision),
    refresh: () => setRevision(value => value + 1),
  };
}
