-- Run only in an owner-confirmed dedicated Supabase TEST project.
-- Requires a trusted postgres connection (SQL Editor or psql), NOT service_role.
-- No migrations are applied. All fixtures, test helpers, and writes roll back.
-- psql: \set ON_ERROR_STOP on
begin;
set local statement_timeout = '30s';
set local lock_timeout = '3s';

-- Abort rather than silently exercising a missing/disabled installation.
do $$
declare t text;
begin
  foreach t in array array['profiles','user_roles','provider_profiles','services',
    'provider_locations','service_images','inquiries','conversations',
    'conversation_participants','messages','verification_requests'] loop
    if not exists (
      select 1 from pg_class c join pg_namespace n on n.oid=c.relnamespace
      where n.nspname='public' and c.relname=t and c.relrowsecurity
    ) then raise exception 'Missing table or disabled RLS: %', t; end if;
  end loop;
  if to_regprocedure('public.create_provider_profile(text,text,text,text,text)') is null
    or to_regprocedure('public.start_inquiry_conversation(uuid)') is null then
    raise exception 'Required onboarding/conversation RPC is missing';
  end if;
end $$;

create temporary table privacy_ids (name text primary key, id uuid not null default gen_random_uuid());
insert into privacy_ids(name) values
  ('customer'),('other_customer'),('draft_owner'),('approved_owner'),('unapproved_owner'),('admin'),
  ('category'),('child_category'),('draft_provider'),('approved_provider'),('unapproved_provider'),
  ('draft_service'),('public_service'),('approved_draft'),('unapproved_service'),
  ('inquiry'),('other_inquiry'),('new_inquiry'),('conversation'),('other_conversation'),
  ('message'),('other_message'),('verification');
create temporary table privacy_results (label text, passed boolean, detail text);
grant select on privacy_ids to anon, authenticated;
grant insert, select on privacy_results to anon, authenticated;

create function pg_temp.fixture(k text) returns uuid language sql stable as $$
  select id from pg_temp.privacy_ids where name=k;
$$;
create function pg_temp.check_value(label text, statement text, expected text)
returns void language plpgsql as $$
declare actual text;
begin
  execute statement into actual;
  insert into pg_temp.privacy_results values(label, actual is not distinct from expected,
    format('expected=%s actual=%s', expected, actual));
exception when others then
  insert into pg_temp.privacy_results values(label, false, format('SQLSTATE %s: %s', SQLSTATE, SQLERRM));
end $$;
create function pg_temp.check_denied(label text, statement text, expected_error text default null)
returns void language plpgsql as $$
declare denied boolean := false; detail text;
begin
  -- Always roll back the attempted write, even when a broken policy permits it.
  begin
    execute statement;
    raise exception using errcode='ZX001', message='Operation unexpectedly succeeded';
  exception when others then
    denied := SQLSTATE in ('42501','P0001')
      and (expected_error is null or position(expected_error in SQLERRM)>0);
    detail := format('SQLSTATE %s: %s', SQLSTATE, SQLERRM);
  end;
  insert into pg_temp.privacy_results values(label, denied, detail);
end $$;
grant execute on function pg_temp.fixture(text), pg_temp.check_value(text,text,text),
  pg_temp.check_denied(text,text,text) to anon, authenticated;

-- Synthetic identities only: no Auth emails are sent and no existing account is reused.
insert into auth.users(id, aud, role, email, raw_app_meta_data, raw_user_meta_data)
select id, 'authenticated', 'authenticated', id::text || '@privacy-test.invalid',
  '{"provider":"email","providers":["email"]}'::jsonb, '{"full_name":"Synthetic privacy fixture"}'::jsonb
from privacy_ids where name in
  ('customer','other_customer','draft_owner','approved_owner','unapproved_owner','admin');
insert into public.user_roles(user_id,role) values
  (pg_temp.fixture('draft_owner'),'provider'),(pg_temp.fixture('approved_owner'),'provider'),
  (pg_temp.fixture('unapproved_owner'),'provider'),(pg_temp.fixture('admin'),'admin');
insert into public.categories(id,slug,name) values
  (pg_temp.fixture('category'),'privacy-' || pg_temp.fixture('category'), 'Privacy test');
insert into public.categories(id,slug,name,parent_id) values
  (pg_temp.fixture('child_category'),'privacy-' || pg_temp.fixture('child_category'),
   'Privacy child', pg_temp.fixture('category'));
insert into public.provider_profiles(id,user_id,business_name,is_complete,verification_status) values
  (pg_temp.fixture('draft_provider'),pg_temp.fixture('draft_owner'),'Privacy draft',false,'not_submitted'),
  (pg_temp.fixture('approved_provider'),pg_temp.fixture('approved_owner'),'Privacy approved',true,'approved'),
  (pg_temp.fixture('unapproved_provider'),pg_temp.fixture('unapproved_owner'),'Privacy unapproved',true,'not_submitted');
insert into public.services(id,provider_id,category_id,title,status) values
  (pg_temp.fixture('draft_service'),pg_temp.fixture('draft_provider'),pg_temp.fixture('child_category'),'Private draft','draft'),
  (pg_temp.fixture('public_service'),pg_temp.fixture('approved_provider'),pg_temp.fixture('child_category'),'Public service','published'),
  (pg_temp.fixture('approved_draft'),pg_temp.fixture('approved_provider'),pg_temp.fixture('child_category'),'Approved owner private draft','draft'),
  (pg_temp.fixture('unapproved_service'),pg_temp.fixture('unapproved_provider'),pg_temp.fixture('child_category'),'Unapproved published fixture','published');
insert into public.provider_locations(provider_id,country_code,city)
select id,'ZW','Fixture city' from privacy_ids where name in ('draft_provider','approved_provider','unapproved_provider');
insert into public.service_images(service_id,storage_path)
select id,'privacy-fixture/not-a-real-object' from privacy_ids
where name in ('draft_service','public_service','approved_draft','unapproved_service');
insert into public.inquiries(id,customer_id,provider_id,subject,description) values
  (pg_temp.fixture('inquiry'),pg_temp.fixture('customer'),pg_temp.fixture('draft_provider'),'Privacy fixture A','Synthetic'),
  (pg_temp.fixture('new_inquiry'),pg_temp.fixture('customer'),pg_temp.fixture('draft_provider'),'Privacy RPC fixture','Synthetic'),
  (pg_temp.fixture('other_inquiry'),pg_temp.fixture('other_customer'),pg_temp.fixture('approved_provider'),'Privacy fixture B','Synthetic');
insert into public.conversations(id,inquiry_id) values
  (pg_temp.fixture('conversation'),pg_temp.fixture('inquiry')),
  (pg_temp.fixture('other_conversation'),pg_temp.fixture('other_inquiry'));
insert into public.conversation_participants(conversation_id,user_id) values
  (pg_temp.fixture('conversation'),pg_temp.fixture('customer')),
  (pg_temp.fixture('conversation'),pg_temp.fixture('draft_owner')),
  (pg_temp.fixture('other_conversation'),pg_temp.fixture('other_customer')),
  (pg_temp.fixture('other_conversation'),pg_temp.fixture('approved_owner'));
insert into public.messages(id,conversation_id,sender_id,body) values
  (pg_temp.fixture('message'),pg_temp.fixture('conversation'),pg_temp.fixture('customer'),'Synthetic message A'),
  (pg_temp.fixture('other_message'),pg_temp.fixture('other_conversation'),pg_temp.fixture('other_customer'),'Synthetic message B');
insert into public.verification_requests(id,provider_id,document_paths) values
  (pg_temp.fixture('verification'),pg_temp.fixture('draft_provider'),array['private/synthetic-document']);

-- Anonymous access must not inherit a prior authenticated JWT subject.
set local role anon;
select set_config('request.jwt.claims','{"role":"anon"}',true),
  set_config('request.jwt.claim.sub','',true);
select pg_temp.check_value('anon has no identity','select auth.uid()::text',null);
select pg_temp.check_value('anon private profiles hidden',
  'select count(*)::text from public.profiles where id in (select id from pg_temp.privacy_ids)','0');
select pg_temp.check_value('anon only approved complete provider visible',
  'select count(*)::text from public.provider_profiles where id in (select id from pg_temp.privacy_ids)','1');
select pg_temp.check_value('anon only approved published service visible',
  'select count(*)::text from public.services where id in (select id from pg_temp.privacy_ids)','1');
select pg_temp.check_value('anon only approved locations visible',
  'select count(*)::text from public.provider_locations where provider_id in (select id from pg_temp.privacy_ids)','1');
select pg_temp.check_value('anon only public portfolio visible',
  'select count(*)::text from public.service_images where service_id in (select id from pg_temp.privacy_ids)','1');
select pg_temp.check_value('anon messages hidden',
  'select count(*)::text from public.messages where id in (select id from pg_temp.privacy_ids)','0');
select pg_temp.check_value('anon conversations hidden',
  'select count(*)::text from public.conversations where id in (select id from pg_temp.privacy_ids)','0');
select pg_temp.check_value('anon verification documents hidden',
  'select count(*)::text from public.verification_requests where id=pg_temp.fixture(''verification'')','0');
select pg_temp.check_denied('anon cannot start conversation',
  'select public.start_inquiry_conversation(pg_temp.fixture(''inquiry''))');
select pg_temp.check_denied('anon cannot onboard',
  'select public.create_provider_profile(''Synthetic'','''',''Harare'',''ZW'',''privacy-'' || pg_temp.fixture(''child_category''))');

reset role;
set local role authenticated;
select set_config('request.jwt.claims',json_build_object('sub',pg_temp.fixture('customer'),'role','authenticated')::text,true),
  set_config('request.jwt.claim.sub',pg_temp.fixture('customer')::text,true);
select pg_temp.check_value('customer owns exactly one private profile',
  'select count(*)::text from public.profiles where id in (select id from pg_temp.privacy_ids)','1');
select pg_temp.check_value('customer cannot see provider draft',
  'select count(*)::text from public.services where id=pg_temp.fixture(''draft_service'')','0');
select pg_temp.check_value('customer cannot see another owner draft on public provider',
  'select count(*)::text from public.services where id=pg_temp.fixture(''approved_draft'')','0');
select pg_temp.check_value('customer cannot update someone else profile',
  'with changed as (update public.profiles set full_name=''Unauthorized'' where id=pg_temp.fixture(''other_customer'') returning id) select count(*)::text from changed','0');
select pg_temp.check_denied('customer cannot grant admin role',
  'insert into public.user_roles(user_id,role) values(auth.uid(),''admin'')');
select pg_temp.check_value('customer is not admin','select public.is_admin()::text','false');
select pg_temp.check_value('customer sees own conversation',
  'select count(*)::text from public.conversations where id=pg_temp.fixture(''conversation'')','1');
select pg_temp.check_value('customer cannot see other conversation',
  'select count(*)::text from public.conversations where id=pg_temp.fixture(''other_conversation'')','0');
select pg_temp.check_value('customer reads own message',
  'select count(*)::text from public.messages where id=pg_temp.fixture(''message'')','1');
select pg_temp.check_value('customer cannot read message in other conversation despite own membership',
  'select count(*)::text from public.messages where id=pg_temp.fixture(''other_message'')','0');
select pg_temp.check_denied('customer cannot send to other conversation despite own membership',
  'insert into public.messages(conversation_id,sender_id,body) values(pg_temp.fixture(''other_conversation''),auth.uid(),''Unauthorized'')');
select pg_temp.check_denied('customer cannot spoof sender',
  'insert into public.messages(conversation_id,sender_id,body) values(pg_temp.fixture(''conversation''),pg_temp.fixture(''draft_owner''),''Unauthorized'')');
select pg_temp.check_value('customer can send to own conversation',
  'with sent as (insert into public.messages(conversation_id,sender_id,body) values(pg_temp.fixture(''conversation''),auth.uid(),''Allowed fixture'') returning id) select count(*)::text from sent','1');
select pg_temp.check_denied('customer cannot self-assign membership',
  'insert into public.conversation_participants(conversation_id,user_id) values(pg_temp.fixture(''other_conversation''),auth.uid())');
select pg_temp.check_denied('customer cannot open unrelated inquiry conversation',
  'select public.start_inquiry_conversation(pg_temp.fixture(''other_inquiry''))','Not authorised');
select pg_temp.check_value('customer RPC returns own conversation',
  'select (public.start_inquiry_conversation(pg_temp.fixture(''inquiry''))=pg_temp.fixture(''conversation''))::text','true');
select pg_temp.check_value('customer RPC creates a new conversation',
  'select (public.start_inquiry_conversation(pg_temp.fixture(''new_inquiry'')) is not null)::text','true');
select pg_temp.check_value('customer RPC is repeatable without duplicates',
  'select (public.start_inquiry_conversation(pg_temp.fixture(''new_inquiry''))=(select id from public.conversations where inquiry_id=pg_temp.fixture(''new_inquiry'')))::text','true');
select pg_temp.check_value('customer sees new RPC membership',
  'select count(*)::text from public.conversation_participants cp join public.conversations c on c.id=cp.conversation_id where c.inquiry_id=pg_temp.fixture(''new_inquiry'') and cp.user_id=auth.uid()','1');
select pg_temp.check_value('customer verification documents hidden',
  'select count(*)::text from public.verification_requests where id=pg_temp.fixture(''verification'')','0');
select pg_temp.check_denied('customer cannot bypass onboarding with approved profile',
  'insert into public.provider_profiles(user_id,business_name,is_complete,verification_status) values(auth.uid(),''Unauthorized'',true,''approved'')');

-- Provider checks include positive editing controls so a deny-all policy fails.
select set_config('request.jwt.claims',json_build_object('sub',pg_temp.fixture('draft_owner'),'role','authenticated')::text,true),
  set_config('request.jwt.claim.sub',pg_temp.fixture('draft_owner')::text,true);
select pg_temp.check_value('provider reads own draft profile',
  'select count(*)::text from public.provider_profiles where id=pg_temp.fixture(''draft_provider'')','1');
select pg_temp.check_value('provider reads own draft service',
  'select count(*)::text from public.services where id=pg_temp.fixture(''draft_service'')','1');
select pg_temp.check_value('provider cannot see other provider draft',
  'select count(*)::text from public.services where id=pg_temp.fixture(''approved_draft'')','0');
select pg_temp.check_value('provider private account profiles isolated',
  'select count(*)::text from public.profiles where id in (select id from pg_temp.privacy_ids)','1');
select pg_temp.check_value('provider can edit business details',
  'with changed as (update public.provider_profiles set description=''Allowed edit'' where id=pg_temp.fixture(''draft_provider'') returning id) select count(*)::text from changed','1');
select pg_temp.check_denied('provider cannot self-approve',
  'update public.provider_profiles set verification_status=''approved'' where id=pg_temp.fixture(''draft_provider'')','controlled by Trolley administrators');
select pg_temp.check_denied('provider cannot mark complete',
  'update public.provider_profiles set is_complete=true where id=pg_temp.fixture(''draft_provider'')','controlled by Trolley administrators');
select pg_temp.check_denied('provider cannot transfer profile',
  'update public.provider_profiles set user_id=pg_temp.fixture(''customer'') where id=pg_temp.fixture(''draft_provider'')','controlled by Trolley administrators');
select pg_temp.check_denied('provider cannot publish service',
  'update public.services set status=''published'' where id=pg_temp.fixture(''draft_service'')','Only Trolley administrators');
select pg_temp.check_value('provider can submit draft for review',
  'with changed as (update public.services set status=''pending_review'' where id=pg_temp.fixture(''draft_service'') returning id) select count(*)::text from changed','1');
select pg_temp.check_value('provider reads own verification request',
  'select count(*)::text from public.verification_requests where id=pg_temp.fixture(''verification'')','1');
select pg_temp.check_value('provider cannot update verification review fields',
  'with changed as (update public.verification_requests set status=''approved'',reviewer_id=auth.uid(),reviewed_at=now() where id=pg_temp.fixture(''verification'') returning id) select count(*)::text from changed','0');
select pg_temp.check_denied('provider cannot forge approved verification submission',
  'insert into public.verification_requests(provider_id,status,reviewer_id,reviewer_note,reviewed_at) values(pg_temp.fixture(''draft_provider''),''approved'',auth.uid(),''Forged approval'',now())');
select pg_temp.check_denied('provider cannot forge verification status alone',
  'insert into public.verification_requests(provider_id,status) values(pg_temp.fixture(''draft_provider''),''approved'')');
select pg_temp.check_denied('provider cannot forge reviewer identity alone',
  'insert into public.verification_requests(provider_id,reviewer_id) values(pg_temp.fixture(''draft_provider''),auth.uid())');
select pg_temp.check_denied('provider cannot forge reviewer note alone',
  'insert into public.verification_requests(provider_id,reviewer_note) values(pg_temp.fixture(''draft_provider''),''Forged note'')');
select pg_temp.check_denied('provider cannot forge review timestamp alone',
  'insert into public.verification_requests(provider_id,reviewed_at) values(pg_temp.fixture(''draft_provider''),now())');
select pg_temp.check_denied('provider cannot submit evidence for another provider',
  'insert into public.verification_requests(provider_id) values(pg_temp.fixture(''approved_provider''))');
select pg_temp.check_value('provider can submit pending verification',
  'with submitted as (insert into public.verification_requests(provider_id,document_paths) values(pg_temp.fixture(''draft_provider''),array[''private/synthetic'']) returning id) select count(*)::text from submitted','1');
select pg_temp.check_value('provider sees own conversation',
  'select count(*)::text from public.conversations where id=pg_temp.fixture(''conversation'')','1');
select pg_temp.check_value('provider sees membership created by customer RPC',
  'select count(*)::text from public.conversation_participants cp join public.conversations c on c.id=cp.conversation_id where c.inquiry_id=pg_temp.fixture(''new_inquiry'') and cp.user_id=auth.uid()','1');
select pg_temp.check_value('provider RPC returns conversation created by customer',
  'select (public.start_inquiry_conversation(pg_temp.fixture(''new_inquiry''))=(select id from public.conversations where inquiry_id=pg_temp.fixture(''new_inquiry'')))::text','true');
select pg_temp.check_value('provider cannot read unrelated messages',
  'select count(*)::text from public.messages where id=pg_temp.fixture(''other_message'')','0');
select pg_temp.check_denied('provider cannot send unrelated messages',
  'insert into public.messages(conversation_id,sender_id,body) values(pg_temp.fixture(''other_conversation''),auth.uid(),''Unauthorized'')');

-- JWT role remains authenticated: admin power must come from trusted user_roles.
select set_config('request.jwt.claims',json_build_object('sub',pg_temp.fixture('admin'),'role','authenticated')::text,true),
  set_config('request.jwt.claim.sub',pg_temp.fixture('admin')::text,true);
select pg_temp.check_value('trusted admin role recognized','select public.is_admin()::text','true');
select pg_temp.check_value('admin reads all fixture private profiles',
  'select count(*)::text from public.profiles where id in (select id from pg_temp.privacy_ids)','6');
select pg_temp.check_value('admin reads all fixture provider profiles',
  'select count(*)::text from public.provider_profiles where id in (select id from pg_temp.privacy_ids)','3');
select pg_temp.check_value('admin reads all fixture services',
  'select count(*)::text from public.services where id in (select id from pg_temp.privacy_ids)','4');
select pg_temp.check_value('admin can approve provider',
  'with changed as (update public.provider_profiles set verification_status=''approved'',is_complete=true where id=pg_temp.fixture(''draft_provider'') returning id) select count(*)::text from changed','1');
select pg_temp.check_value('admin can publish service',
  'with changed as (update public.services set status=''published'' where id=pg_temp.fixture(''draft_service'') returning id) select count(*)::text from changed','1');
select pg_temp.check_value('admin can review verification request',
  'with changed as (update public.verification_requests set status=''approved'',reviewer_id=auth.uid(),reviewed_at=now() where id=pg_temp.fixture(''verification'') returning id) select count(*)::text from changed','1');
select pg_temp.check_value('admin can insert verification decision',
  'with submitted as (insert into public.verification_requests(provider_id,status,reviewer_id,reviewer_note,reviewed_at) values(pg_temp.fixture(''draft_provider''),''approved'',auth.uid(),''Trusted review'',now()) returning id) select count(*)::text from submitted','1');
select pg_temp.check_value('admin reads both conversations',
  'select count(*)::text from public.conversations where id in (select id from pg_temp.privacy_ids)','2');
select pg_temp.check_value('admin reads both original messages',
  'select count(*)::text from public.messages where id in (select id from pg_temp.privacy_ids)','2');
select pg_temp.check_denied('admin still cannot send without membership',
  'insert into public.messages(conversation_id,sender_id,body) values(pg_temp.fixture(''conversation''),auth.uid(),''Unauthorized'')');

reset role;
select pg_temp.check_value('RPC created exactly one conversation',
  'select count(*)::text from public.conversations where inquiry_id=pg_temp.fixture(''new_inquiry'')','1');
select pg_temp.check_value('RPC created exactly the two intended memberships',
  'select (count(*)=2 and bool_and(cp.user_id in (pg_temp.fixture(''customer''),pg_temp.fixture(''draft_owner''))))::text from public.conversation_participants cp join public.conversations c on c.id=cp.conversation_id where c.inquiry_id=pg_temp.fixture(''new_inquiry'')','true');
select label,passed,detail from privacy_results order by passed,label;
select count(*) as total, count(*) filter(where passed) as passed,
  count(*) filter(where not passed) as failed from privacy_results;
do $$ begin
  if exists(select 1 from pg_temp.privacy_results where not passed) then
    raise exception 'Privacy checks failed: inspect the preceding results; transaction must roll back';
  end if;
end $$;
rollback;
