-- Atomic owner-only edits of the single service/location created by onboarding.
-- SECURITY INVOKER deliberately retains all existing RLS and approval guards.
create or replace function public.update_provider_draft(
  p_provider_id uuid, p_business_name text, p_description text,
  p_city text, p_country_code text, p_category_slug text, p_submit boolean default false
) returns uuid language plpgsql security invoker set search_path=public as $$
declare
  v_category_id uuid;
  v_service_id uuid;
  v_location_id uuid;
  v_status public.listing_status;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  perform id from public.provider_profiles
    where id=p_provider_id and user_id=auth.uid() for update;
  if not found then raise exception 'Your provider profile was not found or is not owned by you'; end if;
  if length(trim(coalesce(p_business_name,''))) not between 2 and 120 then
    raise exception 'Business name must be 2 to 120 characters';
  end if;
  if length(coalesce(p_description,'')) > 1200 then raise exception 'Description must be at most 1200 characters'; end if;
  if length(trim(coalesce(p_city,''))) not between 2 and 100 then raise exception 'Enter a valid city'; end if;
  if p_country_code is null or p_country_code !~ '^[A-Z]{2}$' then raise exception 'Use a two-letter country code'; end if;
  select id into v_category_id from public.categories
    where slug=p_category_slug and parent_id is not null and is_active;
  if v_category_id is null then raise exception 'Choose an active service category'; end if;
  -- Never silently select one record from a multi-service/location business.
  if (select count(*) from public.services where provider_id=p_provider_id) <> 1
    or (select count(*) from public.provider_locations where provider_id=p_provider_id) <> 1 then
    raise exception 'Editing requires exactly one service and one location';
  end if;
  select id,status into v_service_id,v_status from public.services
    where provider_id=p_provider_id for update;
  if v_status not in ('draft','pending_review') then raise exception 'Only drafts and pending-review listings can be edited here'; end if;
  select id into v_location_id from public.provider_locations
    where provider_id=p_provider_id for update;
  update public.provider_profiles
    set business_name=trim(p_business_name), description=nullif(trim(coalesce(p_description,'')),'')
    where id=p_provider_id and user_id=auth.uid();
  update public.provider_locations set city=trim(p_city),country_code=p_country_code
    where id=v_location_id and provider_id=p_provider_id;
  update public.services set category_id=v_category_id,title=trim(p_business_name),
    description=nullif(trim(coalesce(p_description,'')),''),
    status=case when p_submit is true then 'pending_review'::public.listing_status else v_status end,
    submitted_at=case when p_submit is true and v_status='draft' then now() else submitted_at end
    where id=v_service_id and provider_id=p_provider_id;
  return p_provider_id;
end; $$;
revoke all on function public.update_provider_draft(uuid,text,text,text,text,text,boolean) from public;
grant execute on function public.update_provider_draft(uuid,text,text,text,text,text,boolean) to authenticated;
