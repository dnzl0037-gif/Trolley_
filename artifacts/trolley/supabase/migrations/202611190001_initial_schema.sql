-- Trolley initial schema. Apply with Supabase CLI or SQL editor.
create extension if not exists pgcrypto;
create extension if not exists citext;

create type public.account_role as enum ('customer','provider','admin');
create type public.listing_status as enum ('draft','pending_review','published','rejected','suspended','archived');
create type public.verification_status as enum ('not_submitted','pending','needs_information','approved','rejected','suspended','expired');

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null default '',
  avatar_url text,
  date_of_birth date,
  country_code char(2),
  city text,
  phone text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint profile_age_min check (date_of_birth is null or date_of_birth <= current_date - interval '13 years')
);
create table public.user_roles (
  user_id uuid not null references auth.users(id) on delete cascade,
  role public.account_role not null,
  assigned_at timestamptz not null default now(),
  assigned_by uuid references auth.users(id),
  primary key (user_id, role)
);
create table public.provider_profiles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique references auth.users(id) on delete cascade,
  business_name text not null,
  slug citext unique,
  description text,
  logo_path text,
  website_url text,
  public_email citext,
  public_phone text,
  verification_status public.verification_status not null default 'not_submitted',
  is_complete boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create table public.categories (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  name text not null,
  parent_id uuid references public.categories(id) on delete restrict,
  description text,
  keywords text[] not null default '{}',
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  unique(parent_id, name)
);
create index categories_parent_idx on public.categories(parent_id) where is_active;
create index categories_name_search_idx on public.categories using gin (to_tsvector('simple', name || ' ' || coalesce(description,'')));
create table public.services (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.provider_profiles(id) on delete cascade,
  category_id uuid not null references public.categories(id) on delete restrict,
  title text not null,
  description text,
  price_from numeric(12,2),
  currency char(3),
  status public.listing_status not null default 'draft',
  submitted_at timestamptz,
  published_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint services_price_nonnegative check (price_from is null or price_from >= 0)
);
create index services_public_idx on public.services(category_id, created_at desc) where status='published';
create table public.provider_locations (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.provider_profiles(id) on delete cascade,
  country_code char(2) not null,
  region text,
  city text not null,
  locality text,
  address_public text,
  is_remote boolean not null default false,
  created_at timestamptz not null default now()
);
create index provider_locations_city_idx on public.provider_locations(country_code, city);
create table public.service_images (
  id uuid primary key default gen_random_uuid(),
  service_id uuid not null references public.services(id) on delete cascade,
  storage_path text not null,
  alt_text text,
  sort_order int not null default 0,
  created_at timestamptz not null default now()
);
create table public.favourites (
  user_id uuid not null references auth.users(id) on delete cascade,
  provider_id uuid not null references public.provider_profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(user_id, provider_id)
);
create table public.inquiries (
  id uuid primary key default gen_random_uuid(),
  customer_id uuid not null references auth.users(id) on delete cascade,
  provider_id uuid not null references public.provider_profiles(id) on delete cascade,
  service_id uuid references public.services(id) on delete set null,
  subject text not null,
  description text not null,
  city text,
  preferred_date date,
  status text not null default 'received' check (status in ('received','quoted','accepted','in_progress','completed','cancelled','disputed')),
  created_at timestamptz not null default now()
);
create table public.conversations (
  id uuid primary key default gen_random_uuid(),
  inquiry_id uuid references public.inquiries(id) on delete set null,
  created_at timestamptz not null default now()
);
create table public.conversation_participants (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  joined_at timestamptz not null default now(),
  primary key(conversation_id,user_id)
);
create table public.messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete cascade,
  body text not null check (length(body) between 1 and 10000),
  created_at timestamptz not null default now(),
  read_at timestamptz
);
create index messages_conversation_idx on public.messages(conversation_id, created_at desc);
create table public.quotations (
  id uuid primary key default gen_random_uuid(),
  inquiry_id uuid not null references public.inquiries(id) on delete cascade,
  provider_id uuid not null references public.provider_profiles(id) on delete cascade,
  currency char(3) not null,
  labour_cost numeric(12,2) not null default 0 check(labour_cost >= 0),
  materials_cost numeric(12,2) not null default 0 check(materials_cost >= 0),
  notes text,
  estimated_days integer check(estimated_days is null or estimated_days >= 0),
  expires_at timestamptz,
  status text not null default 'submitted' check(status in ('submitted','accepted','declined','expired','withdrawn')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index quotations_inquiry_idx on public.quotations(inquiry_id, created_at desc);
create table public.quotation_items (
  id uuid primary key default gen_random_uuid(),
  quotation_id uuid not null references public.quotations(id) on delete cascade,
  description text not null check(length(description) between 1 and 500),
  quantity numeric(10,2) not null default 1 check(quantity > 0),
  unit_price numeric(12,2) not null check(unit_price >= 0),
  created_at timestamptz not null default now()
);
create table public.jobs (
  id uuid primary key default gen_random_uuid(),
  inquiry_id uuid not null unique references public.inquiries(id) on delete cascade,
  quotation_id uuid references public.quotations(id) on delete set null,
  status text not null default 'accepted' check(status in ('accepted','in_progress','completed','cancelled','disputed')),
  scheduled_for timestamptz,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create table public.blocked_users (
  blocker_id uuid not null references auth.users(id) on delete cascade,
  blocked_id uuid not null references auth.users(id) on delete cascade,
  reason text,
  created_at timestamptz not null default now(),
  primary key(blocker_id, blocked_id),
  check(blocker_id <> blocked_id)
);
create table public.notification_preferences (
  user_id uuid primary key references auth.users(id) on delete cascade,
  in_app_enabled boolean not null default true,
  email_enabled boolean not null default true,
  push_enabled boolean not null default false,
  updated_at timestamptz not null default now()
);
create table public.reviews (
  id uuid primary key default gen_random_uuid(),
  customer_id uuid not null references auth.users(id) on delete cascade,
  provider_id uuid not null references public.provider_profiles(id) on delete cascade,
  inquiry_id uuid not null unique references public.inquiries(id) on delete cascade,
  rating smallint not null check(rating between 1 and 5),
  body text,
  status text not null default 'pending' check(status in ('pending','approved','rejected','hidden')),
  provider_response text,
  created_at timestamptz not null default now()
);
create table public.verification_requests (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.provider_profiles(id) on delete cascade,
  status public.verification_status not null default 'pending',
  document_paths text[] not null default '{}',
  reviewer_id uuid references auth.users(id),
  reviewer_note text,
  created_at timestamptz not null default now(),
  reviewed_at timestamptz
);
create table public.subscription_plans (
  id text primary key,
  name text not null,
  monthly_price numeric(12,2) not null,
  currency char(3) not null default 'USD',
  active_listing_limit int not null,
  features jsonb not null default '[]',
  is_active boolean not null default true
);
create table public.subscriptions (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.provider_profiles(id) on delete cascade,
  plan_id text not null references public.subscription_plans(id),
  status text not null default 'pending' check(status in ('pending','active','past_due','cancelled','expired')),
  current_period_start timestamptz,
  current_period_end timestamptz,
  cancel_at_period_end boolean not null default false,
  payment_provider text,
  external_reference text,
  created_at timestamptz not null default now()
);
create table public.payment_events (
  id uuid primary key default gen_random_uuid(),
  subscription_id uuid references public.subscriptions(id) on delete set null,
  provider text not null,
  external_event_id text not null,
  event_type text not null,
  verified boolean not null default false,
  payload_redacted jsonb not null default '{}',
  received_at timestamptz not null default now(),
  unique(provider, external_event_id)
);
create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  type text not null,
  title text not null,
  body text not null,
  read_at timestamptz,
  created_at timestamptz not null default now()
);
create table public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references auth.users(id) on delete cascade,
  target_type text not null,
  target_id uuid not null,
  reason text not null,
  details text,
  status text not null default 'open' check(status in ('open','reviewing','resolved','dismissed')),
  created_at timestamptz not null default now()
);
create table public.admin_audit_logs (
  id uuid primary key default gen_random_uuid(),
  admin_id uuid not null references auth.users(id),
  action text not null,
  target_type text not null,
  target_id uuid,
  metadata_redacted jsonb not null default '{}',
  created_at timestamptz not null default now()
);

create or replace function public.is_admin() returns boolean language sql stable security definer set search_path=public as $$
  select exists(select 1 from public.user_roles where user_id=auth.uid() and role='admin');
$$;
create or replace function public.current_provider_id() returns uuid language sql stable security definer set search_path=public as $$
  select id from public.provider_profiles where user_id=auth.uid() limit 1;
$$;
create or replace function public.handle_new_user() returns trigger language plpgsql security definer set search_path=public as $$
begin
  if new.raw_user_meta_data ? 'date_of_birth' and (new.raw_user_meta_data->>'date_of_birth')::date > current_date - interval '13 years' then
    raise exception 'You must be at least 13 years old';
  end if;
  insert into public.profiles(id, full_name, date_of_birth)
  values(new.id, coalesce(new.raw_user_meta_data->>'full_name',''), nullif(new.raw_user_meta_data->>'date_of_birth','')::date);
  insert into public.user_roles(user_id, role) values(new.id,'customer');
  return new;
end; $$;
create trigger on_auth_user_created after insert on auth.users for each row execute procedure public.handle_new_user();

-- Enable RLS on every application table.
do $$ declare t text; begin
  foreach t in array array['profiles','user_roles','provider_profiles','categories','services','provider_locations','service_images','favourites','inquiries','conversations','conversation_participants','messages','quotations','quotation_items','jobs','blocked_users','notification_preferences','reviews','verification_requests','subscription_plans','subscriptions','payment_events','notifications','reports','admin_audit_logs'] loop
    execute format('alter table public.%I enable row level security', t);
  end loop;
end $$;

create policy "profiles read own or admin" on public.profiles for select using (id=auth.uid() or public.is_admin());
create policy "profiles insert own" on public.profiles for insert with check (id=auth.uid());
create policy "profiles update own" on public.profiles for update using (id=auth.uid()) with check (id=auth.uid());
create policy "roles read own or admin" on public.user_roles for select using (user_id=auth.uid() or public.is_admin());
-- No client insert/update/delete policy for user_roles: role escalation is server-controlled.
create policy "public read complete provider profiles" on public.provider_profiles for select using ((is_complete and verification_status in ('approved','not_submitted')) or user_id=auth.uid() or public.is_admin());
create policy "provider create own profile" on public.provider_profiles for insert with check (user_id=auth.uid());
create policy "provider update own profile" on public.provider_profiles for update using (user_id=auth.uid()) with check (user_id=auth.uid());
create policy "active categories readable" on public.categories for select using (is_active or public.is_admin());
create policy "admin manage categories" on public.categories for all using (public.is_admin()) with check (public.is_admin());
create policy "published services public" on public.services for select using ((status='published') or provider_id=public.current_provider_id() or public.is_admin());
create policy "provider create services" on public.services for insert with check (provider_id=public.current_provider_id());
create policy "provider update services" on public.services for update using (provider_id=public.current_provider_id()) with check (provider_id=public.current_provider_id());
create policy "provider delete draft services" on public.services for delete using (provider_id=public.current_provider_id() and status in ('draft','rejected'));
create policy "public provider locations" on public.provider_locations for select using (exists(select 1 from public.provider_profiles p where p.id=provider_id and p.is_complete and p.verification_status in ('approved','not_submitted')) or provider_id=public.current_provider_id() or public.is_admin());
create policy "provider manage locations" on public.provider_locations for all using (provider_id=public.current_provider_id()) with check (provider_id=public.current_provider_id());
create policy "public images for published service" on public.service_images for select using (exists(select 1 from public.services s where s.id=service_id and s.status='published') or exists(select 1 from public.services s where s.id=service_id and s.provider_id=public.current_provider_id()) or public.is_admin());
create policy "provider manage service images" on public.service_images for all using (exists(select 1 from public.services s where s.id=service_id and s.provider_id=public.current_provider_id())) with check (exists(select 1 from public.services s where s.id=service_id and s.provider_id=public.current_provider_id()));
create policy "favourites owner" on public.favourites for all using (user_id=auth.uid()) with check (user_id=auth.uid());
create policy "inquiry customer provider or admin" on public.inquiries for select using (customer_id=auth.uid() or provider_id=public.current_provider_id() or public.is_admin());
create policy "customer create inquiry" on public.inquiries for insert with check (customer_id=auth.uid());
create policy "customer or provider update inquiry" on public.inquiries for update using (customer_id=auth.uid() or provider_id=public.current_provider_id() or public.is_admin()) with check (customer_id=auth.uid() or provider_id=public.current_provider_id() or public.is_admin());
create policy "conversation participants read" on public.conversations for select using (exists(select 1 from public.conversation_participants cp where cp.conversation_id=id and cp.user_id=auth.uid()) or public.is_admin());
create policy "participants list own memberships" on public.conversation_participants for select using (user_id=auth.uid() or public.is_admin());
create policy "participant add self" on public.conversation_participants for insert with check (user_id=auth.uid());
create policy "participants read messages" on public.messages for select using (exists(select 1 from public.conversation_participants cp where cp.conversation_id=conversation_id and cp.user_id=auth.uid()) or public.is_admin());
create policy "participants send messages" on public.messages for insert with check (sender_id=auth.uid() and exists(select 1 from public.conversation_participants cp where cp.conversation_id=conversation_id and cp.user_id=auth.uid()));
create policy "quotation parties read" on public.quotations for select using (
  exists(select 1 from public.inquiries i where i.id=inquiry_id and (i.customer_id=auth.uid() or i.provider_id=public.current_provider_id())) or public.is_admin()
);
create policy "provider create quotation" on public.quotations for insert with check (
  provider_id=public.current_provider_id() and exists(select 1 from public.inquiries i where i.id=inquiry_id and i.provider_id=public.current_provider_id())
);
create policy "quotation parties update" on public.quotations for update using (
  exists(select 1 from public.inquiries i where i.id=inquiry_id and (i.customer_id=auth.uid() or i.provider_id=public.current_provider_id())) or public.is_admin()
) with check (
  exists(select 1 from public.inquiries i where i.id=inquiry_id and (i.customer_id=auth.uid() or i.provider_id=public.current_provider_id())) or public.is_admin()
);
create policy "quotation item parties read" on public.quotation_items for select using (
  exists(select 1 from public.quotations q join public.inquiries i on i.id=q.inquiry_id where q.id=quotation_id and (i.customer_id=auth.uid() or i.provider_id=public.current_provider_id())) or public.is_admin()
);
create policy "quotation provider manage items" on public.quotation_items for all using (
  exists(select 1 from public.quotations q where q.id=quotation_id and q.provider_id=public.current_provider_id()) or public.is_admin()
) with check (
  exists(select 1 from public.quotations q where q.id=quotation_id and q.provider_id=public.current_provider_id()) or public.is_admin()
);
create policy "job parties read" on public.jobs for select using (
  exists(select 1 from public.inquiries i where i.id=inquiry_id and (i.customer_id=auth.uid() or i.provider_id=public.current_provider_id())) or public.is_admin()
);
create policy "job parties update" on public.jobs for update using (
  exists(select 1 from public.inquiries i where i.id=inquiry_id and (i.customer_id=auth.uid() or i.provider_id=public.current_provider_id())) or public.is_admin()
) with check (
  exists(select 1 from public.inquiries i where i.id=inquiry_id and (i.customer_id=auth.uid() or i.provider_id=public.current_provider_id())) or public.is_admin()
);
create policy "users manage own blocks" on public.blocked_users for all using (blocker_id=auth.uid()) with check (blocker_id=auth.uid());
create policy "users manage own notification preferences" on public.notification_preferences for all using (user_id=auth.uid()) with check (user_id=auth.uid());
create policy "approved reviews public" on public.reviews for select using (status='approved' or customer_id=auth.uid() or exists(select 1 from public.provider_profiles p where p.id=provider_id and p.user_id=auth.uid()) or public.is_admin());
create policy "customer create review" on public.reviews for insert with check (customer_id=auth.uid() and exists(select 1 from public.inquiries i where i.id=inquiry_id and i.customer_id=auth.uid() and i.provider_id=provider_id and i.status='completed'));
create policy "provider respond review" on public.reviews for update using (exists(select 1 from public.provider_profiles p where p.id=provider_id and p.user_id=auth.uid())) with check (exists(select 1 from public.provider_profiles p where p.id=provider_id and p.user_id=auth.uid()));
create policy "verification owner or admin" on public.verification_requests for select using (exists(select 1 from public.provider_profiles p where p.id=provider_id and p.user_id=auth.uid()) or public.is_admin());
create policy "provider submit verification" on public.verification_requests for insert with check (exists(select 1 from public.provider_profiles p where p.id=provider_id and p.user_id=auth.uid()));
create policy "plans readable" on public.subscription_plans for select using (is_active or public.is_admin());
create policy "provider subscriptions own" on public.subscriptions for select using (exists(select 1 from public.provider_profiles p where p.id=provider_id and p.user_id=auth.uid()) or public.is_admin());
create policy "notifications owner" on public.notifications for all using (user_id=auth.uid()) with check (user_id=auth.uid());
create policy "reports owner or admin" on public.reports for select using (reporter_id=auth.uid() or public.is_admin());
create policy "authenticated report create" on public.reports for insert with check (reporter_id=auth.uid());
create policy "admin audit read only" on public.admin_audit_logs for select using (public.is_admin());

insert into public.subscription_plans(id,name,monthly_price,currency,active_listing_limit,features) values
('starter','Starter',0,'USD',1,'["Business profile","One active service listing","Basic enquiries","Standard directory visibility"]'),
('professional','Professional',19,'USD',10,'["Everything in Starter","Up to 10 active service listings","Enhanced profile","Portfolio support","Performance insights","Priority support"]'),
('business','Business',49,'USD',-1,'["Everything in Professional","Team member access","Multiple service locations","Advanced analytics","Promotional tools"]')
on conflict(id) do nothing;
