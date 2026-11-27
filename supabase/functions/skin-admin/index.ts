// 별똥 택배 스킨 관리 (개발자 폰의 관리 페이지 docs/admin.html 이 부른다)
//   { secret, action: "list" }              → 확인 중인 요청 목록
//   { secret, action: "issue", id }        → 코드를 서명해서 발급 (게임에서 자동으로 열림)
//   { secret, action: "reject", id }       → 거절
// secret(관리자 비밀번호)은 데이터베이스 함수(skin_pending, skin_handle)가 확인한다.
// 서명용 개인키는 Edge Function 비밀값 STARDROP_PRIVATE_KEY 에만 있다 (저장소에는 없음).

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "sb_publishable_zfJR61YWduStzWrDbShJkA_bdfPFt8b";
// 게임(SkinCode.java)에 들어 있는 확인용 공개키. 발급 전에 이걸로 한 번 더 확인한다
const PUBLIC_KEY = "MCowBQYDK2VwAyEA6GNVpEGN5ootMrHx6eva5F6jdlE4nvsbWyYNYOu+JX8=";
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type, apikey, authorization, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });
}

async function rpc(fn: string, body: Record<string, unknown>) {
  const headers: Record<string, string> = { apikey: KEY, "Content-Type": "application/json" };
  if (KEY.startsWith("eyJ")) headers.Authorization = `Bearer ${KEY}`;
  const r = await fetch(`${SUPABASE_URL}/rest/v1/rpc/${fn}`, { method: "POST", headers, body: JSON.stringify(body) });
  const text = await r.text();
  if (!r.ok) {
    let msg = text;
    try { msg = JSON.parse(text).message ?? text; } catch { /* 그대로 */ }
    throw new Error(msg);
  }
  return text ? JSON.parse(text) : null;
}

const bytes = (b64: string) => Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
function base64url(b: Uint8Array) {
  let s = "";
  for (const x of b) s += String.fromCharCode(x);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

// 게임의 SkinCode 와 같은 규칙: "STARDROP|스킨|기기번호(글자 10개)" 에 Ed25519 서명
async function sign(skin: string, device: string) {
  const b64 = (Deno.env.get("STARDROP_PRIVATE_KEY") ?? "").trim();
  if (!b64) throw new Error("서명용 개인키(STARDROP_PRIVATE_KEY)가 아직 설정되지 않았어요");
  const message = new TextEncoder().encode(`STARDROP|${skin}|${device.toUpperCase().replace(/[^A-Z0-9]/g, "")}`);
  const key = await crypto.subtle.importKey("pkcs8", bytes(b64), { name: "Ed25519" }, false, ["sign"]);
  const sig = new Uint8Array(await crypto.subtle.sign("Ed25519", key, message));
  const pub = await crypto.subtle.importKey("spki", bytes(PUBLIC_KEY), { name: "Ed25519" }, false, ["verify"]);
  if (!(await crypto.subtle.verify("Ed25519", pub, sig, message))) {
    throw new Error("개인키가 게임의 공개키와 맞지 않아요. STARDROP_PRIVATE_KEY 를 확인해 주세요");
  }
  return base64url(sig);
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "POST only" }, 405);
  try {
    const { secret, action, id } = await req.json();
    if (typeof secret !== "string" || !secret) return json({ error: "비밀번호가 없어요" }, 401);
    const pending = await rpc("skin_pending", { p_secret: secret });   // 비밀번호가 틀리면 여기서 오류
    if (action === "list") return json({ pending });
    const row = (pending as Array<{ id: number; skin: string; device: string }>).find((r) => r.id === Number(id));
    if (!row) return json({ error: "이미 처리됐거나 없는 요청이에요" }, 404);
    if (action === "issue") {
      const code = await sign(row.skin, row.device);
      await rpc("skin_handle", { p_secret: secret, p_id: row.id, p_status: "issued", p_code: code });
      return json({ ok: true });
    }
    if (action === "reject") {
      await rpc("skin_handle", { p_secret: secret, p_id: row.id, p_status: "rejected" });
      return json({ ok: true });
    }
    return json({ error: "모르는 동작" }, 400);
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    return json({ error: msg === "not allowed" ? "비밀번호가 틀렸어요" : msg }, msg === "not allowed" ? 401 : 400);
  }
});
