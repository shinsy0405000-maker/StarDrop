-- 별똥 택배 유료 스킨 코드 요청 (Supabase)
--
-- 게임(공개용 열쇠)은 아래 두 함수만 부를 수 있다:
--   skin_request(기기 번호, 스킨, 입금자명)  : 코드 요청 넣기
--   skin_status(기기 번호)                   : 그 기기의 요청 상태와 발급된 코드 보기
-- 개발자 도구(코드 발급기)는 관리자 비밀번호로 아래 두 함수를 부른다:
--   skin_pending(비밀번호)                         : 확인 중인 요청 목록
--   skin_handle(비밀번호, 요청 번호, 상태, 코드)  : 발급(issued) 또는 거절(rejected)
-- 표(skin_requests, skin_admin)는 공개 API 로 직접 읽거나 고칠 수 없다.
-- 관리자 비밀번호는 여기 적지 않는다 (해시만 skin_admin 표에 따로 넣는다).

create table if not exists public.skin_requests (
  id         bigint generated always as identity primary key,
  created_at timestamptz not null default now(),
  device     text not null check (device ~ '^[2-9A-HJ-NP-Z]{5}-[2-9A-HJ-NP-Z]{5}$'),
  skin       text not null check (skin in ('galaxy', 'gold')),
  payer      text check (payer is null or char_length(payer) <= 40),
  status     text not null default 'pending' check (status in ('pending', 'issued', 'rejected')),
  code       text,
  handled_at timestamptz
);
-- 같은 기기·스킨으로 확인 중인 요청은 하나만
create unique index if not exists skin_requests_one_pending on public.skin_requests (device, skin) where status = 'pending';

create table if not exists public.skin_admin (
  id          int primary key default 1 check (id = 1),
  secret_hash text not null
);

alter table public.skin_requests enable row level security;
alter table public.skin_admin enable row level security;
revoke all on public.skin_requests from anon, authenticated;
revoke all on public.skin_admin from anon, authenticated;

-- 기기 번호를 "ABCDE-FGHJK" 모양으로
create or replace function public.skin_device(p text) returns text
language sql immutable set search_path = public as $$
  select case when length(d) = 10 then substr(d, 1, 5) || '-' || substr(d, 6) else d end
  from (select regexp_replace(upper(coalesce(p, '')), '[^A-Z0-9]', '', 'g') as d) x
$$;

-- 코드 요청 (게임)
create or replace function public.skin_request(p_device text, p_skin text, p_payer text default null) returns text
language plpgsql security definer set search_path = public as $$
declare d text := skin_device(p_device);
begin
  if exists (select 1 from skin_requests where device = d and skin = p_skin and status = 'issued') then return 'issued'; end if;
  if exists (select 1 from skin_requests where device = d and skin = p_skin and status = 'pending') then return 'already'; end if;
  insert into skin_requests (device, skin, payer) values (d, p_skin, nullif(left(btrim(coalesce(p_payer, '')), 40), ''));
  return 'sent';
end $$;

-- 요청 상태와 발급된 코드 (게임). 코드는 그 기기에서만 열려서 기기 번호를 아는 사람이 봐도 괜찮다
create or replace function public.skin_status(p_device text)
returns table (skin text, status text, code text, created_at timestamptz)
language sql stable security definer set search_path = public as $$
  select r.skin, r.status, r.code, r.created_at from skin_requests r
  where r.device = skin_device(p_device) order by r.created_at desc limit 20
$$;

-- 관리자 비밀번호 확인 (안에서만 쓴다)
create or replace function public.skin_admin_ok(p_secret text) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from skin_admin where secret_hash = encode(sha256(convert_to(coalesce(p_secret, ''), 'UTF8')), 'hex'))
$$;

-- 확인 중인 요청 목록 (개발자)
create or replace function public.skin_pending(p_secret text)
returns table (id bigint, created_at timestamptz, device text, skin text, payer text)
language plpgsql stable security definer set search_path = public as $$
begin
  if not skin_admin_ok(p_secret) then raise exception 'not allowed'; end if;
  return query select r.id, r.created_at, r.device, r.skin, r.payer from skin_requests r where r.status = 'pending' order by r.created_at;
end $$;

-- 발급 또는 거절 (개발자)
create or replace function public.skin_handle(p_secret text, p_id bigint, p_status text, p_code text default null) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not skin_admin_ok(p_secret) then raise exception 'not allowed'; end if;
  if p_status not in ('issued', 'rejected') then raise exception 'bad status'; end if;
  if p_status = 'issued' and coalesce(p_code, '') = '' then raise exception 'code needed'; end if;
  update skin_requests set status = p_status, code = case when p_status = 'issued' then p_code end, handled_at = now()
  where id = p_id and status = 'pending';
  if not found then raise exception 'no pending request %', p_id; end if;
end $$;

revoke execute on function public.skin_device(text), public.skin_request(text, text, text), public.skin_status(text),
  public.skin_admin_ok(text), public.skin_pending(text), public.skin_handle(text, bigint, text, text) from public, anon, authenticated;
grant execute on function public.skin_request(text, text, text), public.skin_status(text),
  public.skin_pending(text), public.skin_handle(text, bigint, text, text) to anon, authenticated;

-- ── 폰 알림 (ntfy) ─────────────────────────────────────────────
-- 새 요청이 들어오면 개발자 폰으로 알림을 보낸다. 알림 토픽 이름은 skin_admin.ntfy_topic 에만 둔다 (여기 적지 않음).
create extension if not exists pg_net;
alter table public.skin_admin add column if not exists ntfy_topic text;
alter table public.skin_admin add column if not exists admin_url text;

create or replace function public.skin_request(p_device text, p_skin text, p_payer text default null) returns text
language plpgsql security definer set search_path = public as $$
declare
  d text := skin_device(p_device);
  a record;
  payer text := nullif(left(btrim(coalesce(p_payer, '')), 40), '');
begin
  if exists (select 1 from skin_requests where device = d and skin = p_skin and status = 'issued') then return 'issued'; end if;
  if exists (select 1 from skin_requests where device = d and skin = p_skin and status = 'pending') then return 'already'; end if;
  insert into skin_requests (device, skin, payer) values (d, p_skin, payer);
  begin
    select ntfy_topic, admin_url into a from skin_admin where id = 1;
    if a.ntfy_topic is not null then
      perform net.http_post(
        url := 'https://ntfy.sh',
        body := jsonb_build_object(
          'topic', a.ntfy_topic,
          'title', '새 스킨 요청 · 5,000원',
          'message', (case p_skin when 'gold' then '황금 택배' when 'galaxy' then '은하수 소포' else p_skin end)
                     || E'\n입금자: ' || coalesce(payer, '(안 적음)') || E'\n기기: ' || d,
          'click', a.admin_url,
          'tags', jsonb_build_array('star')),
        headers := '{"Content-Type": "application/json"}'::jsonb);
    end if;
  exception when others then
    null;   -- 알림이 실패해도 요청은 저장된다
  end;
  return 'sent';
end $$;
revoke execute on function public.skin_request(text, text, text) from public;
grant execute on function public.skin_request(text, text, text) to anon, authenticated;
