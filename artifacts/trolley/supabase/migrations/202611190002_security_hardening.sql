-- Trolley security hardening. Apply after 202611190001_initial_schema.sql.
-- Public directory visibility is granted only after an administrator approves a provider.
drop policy if exists "public read complete provider profiles" on public.provider_profiles;
create policy "public read approved provider profiles" on public.provider_profiles
for select using ((is_complete and verification_status='approved') or user_id=auth.uid() or public.is_admin());

drop policy if exists "public provider locations" on public.provider_locations;
create policy "public locations for approved providers" on public.provider_locations
for select using (
  exists(select 1 from public.provider_profiles p where p.id=provider_id and p.is_complete and p.verification_status='approved')
  or provider_id=public.current_provider_id() or public.is_admin()
);

drop policy if exists "published services public" on public.services;
create policy "published services from approved providers" on public.services
for select using (
  (status='published' and exists(select 1 from public.provider_profiles p where p.id=provider_id and p.is_complete and p.verification_status='approved'))
  or provider_id=public.current_provider_id() or public.is_admin()
);

-- Provider profiles are created only by the validated SECURITY DEFINER onboarding RPC.
-- A direct INSERT would otherwise let a browser submit is_complete=true and approved status.
drop policy if exists "provider create own profile" on public.provider_profiles;

-- A provider may edit their own business details, but cannot approve or publish themselves.
create or replace function public.guard_provider_profile_changes()
returns trigger language plpgsql security definer set search_path=public as $$
begin
  if auth.uid() is not null and not public.is_admin() then
    if new.user_id <> old.user_id
      or new.verification_status <> old.verification_status
      or new.is_complete <> old.is_complete then
      raise exception 'Verification and publication fields are controlled by Trolley administrators';
    end if;
  end if;
  return new;
end; $$;
drop trigger if exists provider_profile_guard on public.provider_profiles;
create trigger provider_profile_guard before update on public.provider_profiles
for each row execute function public.guard_provider_profile_changes();

-- Providers can save drafts and submit for review, but only admins can publish listings.
create or replace function public.guard_service_publication()
returns trigger language plpgsql security definer set search_path=public as $$
begin
  if auth.uid() is not null and not public.is_admin() then
    if TG_OP='UPDATE' and new.provider_id <> old.provider_id then raise exception 'A service cannot be transferred to another provider'; end if;
    if new.status not in ('draft','pending_review') then
      raise exception 'Only Trolley administrators can publish, reject, suspend or archive a service';
    end if;
  end if;
  return new;
end; $$;
drop trigger if exists service_publication_guard on public.services;
create trigger service_publication_guard before insert or update on public.services
for each row execute function public.guard_service_publication();

-- A customer/provider cannot rewrite the identity or ownership of an enquiry after creation.
create or replace function public.guard_inquiry_identity()
returns trigger language plpgsql set search_path=public as $$
begin
  if new.customer_id <> old.customer_id or new.provider_id <> old.provider_id or new.service_id is distinct from old.service_id then
    raise exception 'Enquiry participants and service cannot be changed';
  end if;
  return new;
end; $$;
drop trigger if exists inquiry_identity_guard on public.inquiries;
create trigger inquiry_identity_guard before update on public.inquiries
for each row execute function public.guard_inquiry_identity();

-- Conversation membership cannot be self-assigned. Membership is created only through this validated RPC.
drop policy if exists "participant add self" on public.conversation_participants;
create or replace function public.start_inquiry_conversation(p_inquiry_id uuid)
returns uuid language plpgsql security definer set search_path=public as $$
declare v_customer uuid; v_provider_user uuid; v_provider_profile uuid; v_conversation uuid;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  select i.customer_id, p.user_id, p.id into v_customer, v_provider_user, v_provider_profile
  from public.inquiries i join public.provider_profiles p on p.id=i.provider_id
  where i.id=p_inquiry_id;
  if v_customer is null then raise exception 'Enquiry not found'; end if;
  if auth.uid() not in (v_customer,v_provider_user) and not public.is_admin() then raise exception 'Not authorised'; end if;
  select id into v_conversation from public.conversations where inquiry_id=p_inquiry_id limit 1;
  if v_conversation is null then
    insert into public.conversations(inquiry_id) values(p_inquiry_id) returning id into v_conversation;
    insert into public.conversation_participants(conversation_id,user_id) values(v_conversation,v_customer),(v_conversation,v_provider_user) on conflict do nothing;
  end if;
  return v_conversation;
end; $$;
revoke all on function public.start_inquiry_conversation(uuid) from public;
grant execute on function public.start_inquiry_conversation(uuid) to authenticated;

-- Secure provider onboarding: create profile, role, location and a draft service in one transaction.
create or replace function public.create_provider_profile(
  p_business_name text, p_description text, p_city text, p_country_code text, p_category_slug text
) returns uuid language plpgsql security definer set search_path=public as $$
declare v_provider_id uuid; v_category_id uuid;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if length(trim(coalesce(p_business_name,''))) < 2 or length(trim(coalesce(p_business_name,''))) > 120 then raise exception 'Business name must be 2 to 120 characters'; end if;
  if length(trim(coalesce(p_city,''))) < 2 or length(trim(coalesce(p_city,''))) > 100 then raise exception 'Enter a valid city'; end if;
  if p_country_code !~ '^[A-Z]{2}$' then raise exception 'Use a two-letter country code'; end if;
  select id into v_category_id from public.categories where slug=p_category_slug and parent_id is not null and is_active;
  if v_category_id is null then raise exception 'Choose an active service category'; end if;
  if exists(select 1 from public.provider_profiles where user_id=auth.uid()) then raise exception 'A provider profile already exists for this account'; end if;
  insert into public.provider_profiles(user_id,business_name,description)
  values(auth.uid(),trim(p_business_name),nullif(trim(coalesce(p_description,'')),'')) returning id into v_provider_id;
  insert into public.user_roles(user_id,role) values(auth.uid(),'provider') on conflict do nothing;
  insert into public.provider_locations(provider_id,country_code,city) values(v_provider_id,p_country_code,trim(p_city));
  insert into public.services(provider_id,category_id,title,description,status)
  values(v_provider_id,v_category_id,trim(p_business_name),nullif(trim(coalesce(p_description,'')),''),'draft');
  return v_provider_id;
end; $$;
revoke all on function public.create_provider_profile(text,text,text,text,text) from public;
grant execute on function public.create_provider_profile(text,text,text,text,text) to authenticated;

-- Administrative review and moderation are server-side role-gated.
create policy "admin manage provider profiles" on public.provider_profiles for all using (public.is_admin()) with check (public.is_admin());
create policy "admin manage services" on public.services for all using (public.is_admin()) with check (public.is_admin());
create policy "admin manage verification requests" on public.verification_requests for all using (public.is_admin()) with check (public.is_admin());
create policy "admin manage subscriptions plans" on public.subscription_plans for all using (public.is_admin()) with check (public.is_admin());
create policy "admin manage subscriptions" on public.subscriptions for all using (public.is_admin()) with check (public.is_admin());
create policy "admin manage reports" on public.reports for all using (public.is_admin()) with check (public.is_admin());
create policy "admin manage inquiries" on public.inquiries for all using (public.is_admin()) with check (public.is_admin());
create policy "admin manage reviews" on public.reviews for all using (public.is_admin()) with check (public.is_admin());
create policy "admin manage notifications" on public.notifications for all using (public.is_admin()) with check (public.is_admin());

-- Do not allow a review owner to edit rating/body/status after submission; provider may only set a response.
create or replace function public.guard_review_changes()
returns trigger language plpgsql set search_path=public as $$
begin
  if auth.uid() is not null and not public.is_admin() then
    if new.customer_id <> old.customer_id or new.provider_id <> old.provider_id or new.inquiry_id <> old.inquiry_id
      or new.rating <> old.rating or new.body is distinct from old.body or new.status <> old.status then
      raise exception 'Review content and moderation status cannot be changed through this action';
    end if;
  end if;
  return new;
end; $$;
drop trigger if exists review_changes_guard on public.reviews;
create trigger review_changes_guard before update on public.reviews for each row execute function public.guard_review_changes();

-- Portfolio images are public only when their provider has an approved profile and the service is published.
drop policy if exists "public images for published service" on public.service_images;
create policy "public images for approved published services" on public.service_images for select using (
  exists(select 1 from public.services s join public.provider_profiles p on p.id=s.provider_id where s.id=service_id and s.status='published' and p.is_complete and p.verification_status='approved')
  or exists(select 1 from public.services s where s.id=service_id and s.provider_id=public.current_provider_id()) or public.is_admin()
);

-- An enquiry must target a currently published service owned by the selected provider.
create or replace function public.validate_inquiry_target()
returns trigger language plpgsql security definer set search_path=public as $$
begin
  if new.service_id is not null and not exists(
    select 1 from public.services s join public.provider_profiles p on p.id=s.provider_id
    where s.id=new.service_id and s.provider_id=new.provider_id and s.status='published' and p.is_complete and p.verification_status='approved'
  ) then raise exception 'The selected service is not available for enquiries'; end if;
  return new;
end; $$;
drop trigger if exists inquiry_target_guard on public.inquiries;
create trigger inquiry_target_guard before insert on public.inquiries for each row execute function public.validate_inquiry_target();
