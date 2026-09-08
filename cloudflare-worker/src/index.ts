
type Env = {
  FIREBASE_PROJECT_ID: string;
  FIREBASE_SERVICE_ACCOUNT_JSON: string;
};

type ServiceAccount = {
  client_email: string;
  private_key: string;
  project_id: string;
};

type JwtPayload = Record<string, unknown> & { sub?: string; aud?: string; iss?: string; exp?: number; iat?: number };

type JwtHeader = { alg?: string; kid?: string };

const GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const FIRESTORE_SCOPE = "https://www.googleapis.com/auth/datastore";
const FIREBASE_KEYS_URL = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";
let cachedGoogleKeys: { fetchedAt: number; certs: Record<string, string> } | null = null;
let cachedServiceToken: { token: string; expiresAt: number } | null = null;

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json", "cache-control": "no-store" } });
}

function base64UrlToBytes(value: string): Uint8Array {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
  const decoded = atob(padded);
  return Uint8Array.from(decoded, c => c.charCodeAt(0));
}

function base64UrlEncode(value: string | ArrayBuffer | Uint8Array): string {
  const bytes = typeof value === "string" ? new TextEncoder().encode(value) : value instanceof ArrayBuffer ? new Uint8Array(value) : value;
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function pemToBytes(pem: string): Uint8Array {
  return base64UrlToBytes(pem.replace(/-----BEGIN CERTIFICATE-----|-----END CERTIFICATE-----|\s/g, "").replace(/\+/g, "-").replace(/\//g, "_"));
}

function parseJwt(token: string): { header: JwtHeader; payload: JwtPayload; signingInput: string; signature: Uint8Array } {
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("Invalid JWT");
  return {
    header: JSON.parse(new TextDecoder().decode(base64UrlToBytes(parts[0]))),
    payload: JSON.parse(new TextDecoder().decode(base64UrlToBytes(parts[1]))),
    signingInput: `${parts[0]}.${parts[1]}`,
    signature: base64UrlToBytes(parts[2]),
  };
}

async function verifyFirebaseIdToken(token: string, expectedProjectId: string): Promise<JwtPayload> {
  const { header, payload, signingInput, signature } = parseJwt(token);
  if (header.alg !== "RS256" || !header.kid) throw new Error("Unsupported token");
  const now = Math.floor(Date.now() / 1000);
  if (!payload.sub || payload.sub.length > 128) throw new Error("Invalid subject");
  if (payload.aud !== expectedProjectId) throw new Error("Invalid audience");
  if (payload.iss !== `https://securetoken.google.com/${expectedProjectId}`) throw new Error("Invalid issuer");
  if (!payload.exp || payload.exp <= now || !payload.iat || payload.iat > now + 60) throw new Error("Expired token");

  if (!cachedGoogleKeys || Date.now() - cachedGoogleKeys.fetchedAt > 60 * 60 * 1000) {
    const response = await fetch(FIREBASE_KEYS_URL);
    if (!response.ok) throw new Error("Could not load Firebase verification keys");
    cachedGoogleKeys = { fetchedAt: Date.now(), certs: await response.json() };
  }
  const cert = cachedGoogleKeys.certs[header.kid];
  if (!cert) throw new Error("Unknown signing key");
  const key = await crypto.subtle.importKey("spki", pemToBytes(cert), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, signature, new TextEncoder().encode(signingInput));
  if (!ok) throw new Error("Invalid signature");
  return payload;
}

async function importServiceAccountKey(pem: string): Promise<CryptoKey> {
  const bytes = base64UrlToBytes(pem.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, "").replace(/\+/g, "-").replace(/\//g, "_"));
  return crypto.subtle.importKey("pkcs8", bytes, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
}

async function getGoogleAccessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedServiceToken && cachedServiceToken.expiresAt > now + 60) return cachedServiceToken.token;
  const key = await importServiceAccountKey(sa.private_key);
  const header = base64UrlEncode(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = base64UrlEncode(JSON.stringify({ iss: sa.client_email, scope: `${FCM_SCOPE} ${FIRESTORE_SCOPE}`, aud: GOOGLE_TOKEN_URL, iat: now, exp: now + 3600 }));
  const input = `${header}.${payload}`;
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(input));
  const assertion = `${input}.${base64UrlEncode(signature)}`;
  const response = await fetch(GOOGLE_TOKEN_URL, { method: "POST", headers: { "content-type": "application/x-www-form-urlencoded" }, body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }).toString() });
  if (!response.ok) throw new Error(`OAuth token exchange failed: ${response.status}`);
  const data = await response.json() as { access_token: string; expires_in: number };
  cachedServiceToken = { token: data.access_token, expiresAt: now + data.expires_in };
  return data.access_token;
}

async function getRecipientToken(projectId: string, accessToken: string, recipientUid: string): Promise<string | null> {
  const url = `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/databases/(default)/documents/userNotificationTokens/${encodeURIComponent(recipientUid)}`;
  const response = await fetch(url, { headers: { authorization: `Bearer ${accessToken}` } });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`Firestore lookup failed: ${response.status}`);
  const doc = await response.json() as { fields?: Record<string, { stringValue?: string }> };
  return doc.fields?.fcmToken?.stringValue ?? null;
}

async function isAuthorizedResource(
  projectId: string,
  accessToken: string,
  resourceType: string,
  resourceId: string,
  senderUid: string,
  recipientUid: string
): Promise<boolean> {
  const collection = resourceType === "chat" ? "chats" : "calls";
  const url = `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/databases/(default)/documents/${collection}/${encodeURIComponent(resourceId)}`;
  const response = await fetch(url, { headers: { authorization: `Bearer ${accessToken}` } });
  if (response.status === 404) return false;
  if (!response.ok) throw new Error(`Firestore authorization lookup failed: ${response.status}`);
  const doc = await response.json() as { fields?: Record<string, { arrayValue?: { values?: Array<{ stringValue?: string }> } }> };
  const participants = doc.fields?.participants?.arrayValue?.values?.map(v => v.stringValue).filter((v): v is string => Boolean(v)) ?? [];
  return participants.includes(senderUid) && participants.includes(recipientUid);
}

async function sendFcm(projectId: string, accessToken: string, token: string, type: string, senderName: string): Promise<void> {
  const url = `https://fcm.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/messages:send`;
  const response = await fetch(url, {
    method: "POST",
    headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
    body: JSON.stringify({ message: { token, data: { type, sender: senderName } } }),
  });
  if (!response.ok) throw new Error(`FCM send failed: ${response.status}`);
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);
    const url = new URL(request.url);
    if (url.pathname !== "/send-notification") return json({ error: "Not found" }, 404);

    const authHeader = request.headers.get("Authorization") || "";
    if (!authHeader.startsWith("Bearer ")) return json({ error: "Missing Firebase ID token" }, 401);
    const token = authHeader.slice(7).trim();

    let body: { recipientUid?: string; type?: string; senderName?: string; resourceType?: string; resourceId?: string };
    try { body = await request.json(); } catch { return json({ error: "Invalid JSON" }, 400); }
    if (!body.recipientUid || !/^[A-Za-z0-9_-]{1,128}$/.test(body.recipientUid)) return json({ error: "Invalid recipientUid" }, 400);
    if (!body.type || !["message", "call"].includes(body.type)) return json({ error: "Invalid type" }, 400);
    if (!body.resourceType || !["chat", "call"].includes(body.resourceType)) return json({ error: "Invalid resourceType" }, 400);
    if (!body.resourceId || !/^[A-Za-z0-9_-]{1,128}$/.test(body.resourceId)) return json({ error: "Invalid resourceId" }, 400);
    if ((body.type === "message" && body.resourceType !== "chat") || (body.type === "call" && body.resourceType !== "call")) return json({ error: "Notification type/resource mismatch" }, 400);
    const senderName = String(body.senderName || "CipherChat").slice(0, 80);

    try {
      const claims = await verifyFirebaseIdToken(token, env.FIREBASE_PROJECT_ID);
      const senderUid = claims.sub as string;
      if (senderUid === body.recipientUid) return json({ ok: true, skipped: "self" });
      const serviceAccount = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON) as ServiceAccount;
      if (serviceAccount.project_id !== env.FIREBASE_PROJECT_ID) return json({ error: "Service account project mismatch" }, 500);
      const accessToken = await getGoogleAccessToken(serviceAccount);
      if (!(await isAuthorizedResource(env.FIREBASE_PROJECT_ID, accessToken, body.resourceType, body.resourceId, senderUid, body.recipientUid))) {
        return json({ error: "Sender is not a participant in the referenced resource" }, 403);
      }
      const recipientToken = await getRecipientToken(env.FIREBASE_PROJECT_ID, accessToken, body.recipientUid);
      if (!recipientToken) return json({ ok: true, skipped: "no-token" });
      await sendFcm(env.FIREBASE_PROJECT_ID, accessToken, recipientToken, body.type, senderName);
      return json({ ok: true });
    } catch (error) {
      return json({ error: error instanceof Error ? error.message : "Notification failed" }, 500);
    }
  }
};
