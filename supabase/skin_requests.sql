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
