import { useEffect, useState } from 'react';
import { supabase } from './supabase';
import { z } from 'zod';

const providerSchema = z.object({
  id: z.string(),
  business_name: z.string(),
  description: z.string().nullable(),
  verification_status: z.string(),
  is_complete: z.boolean(),
  services: z.array(z.object({
    id: z.string(), status: z.string(),
    categories: z.object({ slug: z.string() }).nullable(),
  })),
  provider_locations: z.array(z.object({
    id: z.string(), city: z.string(), country_code: z.string(),
  })),
});
export type ProviderProfile = z.infer<typeof providerSchema>;

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
      .select('id,business_name,description,verification_status,is_complete,services(id,status,categories(slug)),provider_locations(id,city,country_code)')
      .eq('user_id', userId)
      .maybeSingle()
      .then(({ data, error }) => {
        if (!active) return;
        const parsed = data === null ? null : providerSchema.safeParse(data);
        setResult({
          userId,
          profile: !error && parsed?.success ? parsed.data : null,
          error: error?.message ?? (parsed && !parsed.success ? 'The saved profile response was incomplete. Please retry.' : ''),
        });
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
