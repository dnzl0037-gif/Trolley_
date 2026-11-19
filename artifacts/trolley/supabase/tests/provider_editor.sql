-- Included inside a transaction by check-provider-editor.py. Nothing persists.
set local statement_timeout = '30s';
set local lock_timeout = '3s';
create temporary table editor_ids(k text primary key, id uuid default gen_random_uuid());
insert into editor_ids(k) values ('owner'),('other'),('provider'),('other_provider'),('service'),('location');
grant select on editor_ids to anon, authenticated;
create function pg_temp.eid(k text) returns uuid language sql as $$
  select id from pg_temp.editor_ids where editor_ids.k=$1;
$$;
create function pg_temp.assert_editor(ok boolean, label text) returns void language plpgsql as $$
begin
  if ok is distinct from true then raise exception 'FAIL: %',label; end if;
  raise notice 'PASS: %',label;
end $$;
create function pg_temp.deny_editor(statement text, label text) returns void language plpgsql as $$
begin
  begin
    execute statement;
  exception when insufficient_privilege or raise_exception then
    raise notice 'PASS: %',label;
    return;
  end;
  raise exception 'FAIL: % was allowed',label;
end $$;
insert into auth.users(id,aud,role,email,raw_app_meta_data,raw_user_meta_data)
select id,'authenticated','authenticated',id::text || '@editor-test.invalid',
  '{"provider":"email","providers":["email"]}'::jsonb,'{}'::jsonb
from editor_ids where k in ('owner','other');
insert into public.provider_profiles(id,user_id,business_name)
values (pg_temp.eid('provider'),pg_temp.eid('owner'),'Original business'),
  (pg_temp.eid('other_provider'),pg_temp.eid('other'),'Other business');
insert into public.services(id,provider_id,category_id,title)
select pg_temp.eid('service'),pg_temp.eid('provider'),id,'Original business'
from public.categories where slug='plumbing-leak-detection';
insert into public.provider_locations(id,provider_id,city,country_code)
values(pg_temp.eid('location'),pg_temp.eid('provider'),'Harare','ZW');

set local role authenticated;
select set_config('request.jwt.claim.sub',pg_temp.eid('owner')::text,true);
select pg_temp.assert_editor(public.update_provider_draft(pg_temp.eid('provider'),
  ' Edited business ','New description','Bulawayo','ZW','electrical-lighting-installation',false)=pg_temp.eid('provider'),
  'owner saves an existing draft');
select pg_temp.assert_editor(exists(select 1 from public.provider_profiles
  where id=pg_temp.eid('provider') and business_name='Edited business' and description='New description'
  and verification_status='not_submitted' and not is_complete),'profile read-back preserves approval flags');
select pg_temp.assert_editor(exists(select 1 from public.services s join public.categories c on c.id=s.category_id
  where s.id=pg_temp.eid('service') and c.slug='electrical-lighting-installation' and s.status='draft'
  and s.title='Edited business' and s.description='New description'),'category and service read-back');
select pg_temp.assert_editor(exists(select 1 from public.provider_locations
  where id=pg_temp.eid('location') and city='Bulawayo' and country_code='ZW'),'location read-back');
select pg_temp.deny_editor($q$select public.update_provider_draft(pg_temp.eid('provider'),'Invalid save',
  '','Mutare','ZW','missing-category',false)$q$,'invalid category rejected');
select pg_temp.assert_editor((select business_name='Edited business' from public.provider_profiles
  where id=pg_temp.eid('provider')),'invalid save changed nothing');
select public.update_provider_draft(pg_temp.eid('provider'),'Edited business','','Bulawayo','ZW','electrical-lighting-installation',true);
select pg_temp.assert_editor(exists(select 1 from public.services where id=pg_temp.eid('service')
  and status='pending_review' and submitted_at is not null and published_at is null),'submission sets pending review, never publication');
select public.update_provider_draft(pg_temp.eid('provider'),'Pending edit','','Mutare','ZA','plumbing-leak-detection',false);
select pg_temp.assert_editor(exists(select 1 from public.services where id=pg_temp.eid('service')
  and status='pending_review' and description is null),'pending edits retain review state and clear optional description');
select pg_temp.deny_editor($q$update public.provider_profiles set verification_status='approved'
  where id=pg_temp.eid('provider')$q$,'owner cannot approve verification');
select pg_temp.deny_editor($q$update public.provider_profiles set is_complete=true
  where id=pg_temp.eid('provider')$q$,'owner cannot make profile public');
select pg_temp.deny_editor($q$update public.services set status='published'
  where id=pg_temp.eid('service')$q$,'owner cannot publish service');
select set_config('request.jwt.claim.sub',pg_temp.eid('other')::text,true);
select pg_temp.deny_editor($q$select public.update_provider_draft(pg_temp.eid('provider'),'Stolen business',
  '','Harare','ZW','plumbing-leak-detection',true)$q$,'unrelated provider cannot edit or submit another owner draft');
reset role;
select pg_temp.assert_editor((select business_name='Pending edit' from public.provider_profiles
  where id=pg_temp.eid('provider')),'unauthorized write changed nothing');
set local role anon;
select set_config('request.jwt.claim.sub','',true);
select pg_temp.deny_editor($q$select public.update_provider_draft(pg_temp.eid('provider'),'Anonymous',
  '','Harare','ZW','plumbing-leak-detection',true)$q$,'anonymous cannot call editor');
select pg_temp.assert_editor(not exists(select 1 from public.services where id=pg_temp.eid('service')),
  'submitted draft remains private');
reset role;
