
# CipherChat Notification Worker

This Worker is the only server-side component that holds Firebase service-account credentials. The Android APK does **not** contain a Firebase service-account key, FCM legacy server key, or other backend credential.

## Important security decision: no shared secret in the APK

The original request proposed putting an `X-App-Secret` in the Android `.env` and sending it from the APK. That would make the value recoverable from the APK because build-time values embedded in an Android binary are not secret. The worker therefore uses a **Firebase Auth ID token** in `Authorization: Bearer ...` and verifies its JWT signature, issuer, audience, expiry, and subject. A stolen/random caller cannot use the endpoint without a valid authenticated Firebase user token.


## Prerequisites

- A Cloudflare account on the Workers Free plan.
- Node.js 20+.
- A Firebase project with Firebase Authentication, Firestore, and Cloud Messaging configured.

Cloudflare's current Workers Free plan includes 100,000 inbound requests/day and 10 ms CPU time per invocation. Worker-to-Google subrequests do not count as additional billed Worker requests, although the per-invocation subrequest limit still applies. For 5–10 users this architecture is far below the daily request ceiling for normal chat/call notification volume.

## 1. Create the Cloudflare account and install Wrangler

Use Cloudflare's Free plan. A current Cloudflare getting-started workshop documents a Free account as requiring no credit card. Then install Wrangler:

```bash
node --version
npm install
npx wrangler@latest --version
```

Log in:

```bash
npx wrangler login
```

## 2. Get your Firebase project ID

Firebase Console → Project settings → General → Project ID.

Put it in `wrangler.toml`:

```toml
[vars]
FIREBASE_PROJECT_ID = "your-project-id"
```

Do not put a service-account private key in `wrangler.toml`.

## 3. Generate the Firebase service-account key

Firebase Console → Project settings → Service accounts → Generate New Private Key → confirm Generate Key. Firebase downloads a JSON private-key file. Treat it like a password and keep it off GitHub.

## 4. Upload the service-account JSON as a Worker secret

From this directory, run:

```bash
npx wrangler secret put FIREBASE_SERVICE_ACCOUNT_JSON
```

Paste the **entire JSON file contents** when Wrangler prompts for the secret.

Do not add either value to Git, `wrangler.toml`, or the Android APK.

## 5. Deploy

```bash
npx wrangler deploy
```

Wrangler will print the deployed Worker URL. Put that URL in the Android root `.env` as:

```properties
NOTIFICATION_WORKER_URL=https://your-worker-name.your-subdomain.workers.dev
GOOGLE_WEB_CLIENT_ID=your-web-oauth-client-id.apps.googleusercontent.com
```

Those two Android values are not backend secrets: the OAuth client ID is public by design, and the Worker URL is public by design.

## 6. Firebase permissions for the Worker

The Worker uses the Firebase service account to call Firestore REST and FCM HTTP v1. Server-side APIs authenticated by a service account bypass Firestore Security Rules, so IAM/service-account permissions must be sufficient for both services. For Firestore REST, the service account needs Firestore/Datastore read access (for example `roles/datastore.user`). For FCM, grant the service account the FCM sending permission required by your project (the current Firebase docs use the `Firebase Cloud Messaging API Admin` role when a sender account is separate from the target project).

FCM uses the HTTP v1 endpoint:

```text
POST https://fcm.googleapis.com/v1/projects/PROJECT_ID/messages:send
```

The Worker obtains a short-lived OAuth 2.0 access token from the service account; no legacy FCM server key is used.

## 7. Test

After deploying and signing into the app, send a message or start a call. The app writes the encrypted data to Firestore first, then calls:

```text
POST /send-notification
Authorization: Bearer <Firebase ID token>
Content-Type: application/json

{
  "recipientUid": "...",
  "type": "message",
  "senderName": "...",
  "resourceType": "chat",
  "resourceId": "..."
}
```

The Worker first verifies that the authenticated sender is a participant in the referenced chat/call, then reads `userNotificationTokens/{recipientUid}.fcmToken` with Firestore REST and sends a data-only FCM notification. This prevents one authenticated user from using the Worker to notify arbitrary users.
