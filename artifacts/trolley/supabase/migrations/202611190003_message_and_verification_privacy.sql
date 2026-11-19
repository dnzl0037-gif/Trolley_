-- Apply after 202611190002_security_hardening.sql.
-- Correlate membership to the OUTER message, not to the membership row itself.
-- Without qualification, PostgreSQL resolves conversation_id to cp.conversation_id.
drop policy if exists "participants read messages" on public.messages;
create policy "participants read messages" on public.messages for select using (
  exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id=messages.conversation_id and cp.user_id=auth.uid()
  ) or public.is_admin()
);

drop policy if exists "participants send messages" on public.messages;
create policy "participants send messages" on public.messages for insert with check (
  sender_id=auth.uid() and exists (
    select 1 from public.conversation_participants cp
    where cp.conversation_id=messages.conversation_id and cp.user_id=auth.uid()
  )
);

-- Providers may submit evidence, never a decision or fabricated reviewer fields.
-- The separate admin policy still permits trusted administrative review.
drop policy if exists "provider submit verification" on public.verification_requests;
create policy "provider submit verification" on public.verification_requests
for insert with check (
  status='pending'
  and reviewer_id is null
  and reviewer_note is null
  and reviewed_at is null
  and exists (
    select 1 from public.provider_profiles p
    where p.id=verification_requests.provider_id and p.user_id=auth.uid()
  )
);
