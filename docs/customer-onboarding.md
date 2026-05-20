# Customer Onboarding Guide

This document describes how to onboard a new external partner/client to the API authentication system.

---

## 1. Overview

Each external partner receives:
- A `clientId` — public identifier
- A `clientSecret` — confidential credential, shown only once
- A set of `scopes` — permitted API operations

The partner authenticates via OAuth2 client_credentials grant to receive a JWT access token, then uses the token to call protected APIs.

---

## 2. Prerequisites

- Access to the L2 Auth/Application Service database (`user_auth`)
- Permission to insert into `auth_client`, `auth_client_scope`
- Admin UI or direct SQL access (this guide uses SQL)

---

## 3. Onboarding Procedure

### Step 1: Register the Client

Insert a row into `auth_client`:

```sql
INSERT INTO auth_client (client_id, client_name, client_secret_hash, status)
VALUES ('partner_acme_corp', 'Acme Corporation', '<hash>', 'ACTIVE');
```

**Fields**:
| Column | Value |
|---|---|
| `client_id` | Unique identifier for the partner. Use a reverse-DNS or kebab-case convention, e.g. `partner_acme_corp`, `org_xyz_logistics` |
| `client_name` | Human-readable display name for operational use |
| `client_secret_hash` | See Step 2 |
| `status` | `ACTIVE`, `INACTIVE`, `LOCKED`, `SUSPENDED`, or `DELETED` |

### Step 2: Generate a Client Secret and Hash

The secret must be generated securely and shown to the partner **only once**. The hash is what gets stored.

**Generate a secret** (48 random bytes, Base64URL-encoded):
```bash
python3 -c "
import secrets, base64
secret = base64.urlsafe_b64encode(secrets.token_bytes(48)).rstrip(b'=').decode()
print(f'Client Secret: {secret}')
"
```

**Compute the hash** for DB storage:
```bash
python3 -c "
import hashlib, base64, os

secret = '<the-secret-from-above>'  # paste here
salt = os.urandom(16)
salt_b64 = base64.b64encode(salt).decode()
digest = hashlib.sha256(salt + secret.encode()).digest()
digest_b64 = base64.b64encode(digest).decode()
print(f'Hash (store in DB): {salt_b64}:{digest_b64}')
"
```

The stored format is `base64(salt):base64(hash)` — 4 parts. The `ClientSecretHasher` on L2 uses this for validation.

> **Security rules**:
> - Never log the plaintext secret
> - Never store the plaintext secret
> - The secret must be transmitted securely to the partner (out-of-band, encrypted channel)
> - If the secret is lost, rotate it (see §7)

### Step 3: Assign Scopes

Determine which APIs the partner needs access to and grant the corresponding scopes.

```sql
INSERT INTO auth_client_scope (client_id, scope_name)
VALUES
    ('partner_acme_corp', 'tracking:read'),
    ('partner_acme_corp', 'shipment:create');
```

**Available scopes** (defined in `auth_scope`):

| Scope | Description | API Access |
|---|---|---|
| `tracking:read` | View tracking info | `GET /tracking/{awb}` |
| `shipment:create` | Create shipments | `POST /shipment` |
| `shipment:update` | Update shipments | `PUT /shipment/{id}` |
| `status:update` | Update status | `POST /status/update` |
| `invoice:read` | View invoices | `GET /invoice/{id}` |

> If a new scope is needed, it must first be added to `auth_scope`, then mapped to an API endpoint in `auth_api_scope_mapping`.

### Step 4: Provide Credentials to the Partner

Provide the partner with:
```
Client ID:     partner_acme_corp
Client Secret: <the-secret-from-step-2>
Token Endpoint: POST https://api.company.com/auth/token
Scopes:        tracking:read shipment:create
```

Emphasize that:
- The secret will never be shown again
- Tokens expire in 15 minutes and must be refreshed
- The secret should be stored securely (vault, encrypted config, etc.)

---

## 4. Partner Integration Example

### Get an access token

```bash
curl -X POST https://api.company.com/auth/token \
  -H "Content-Type: application/json" \
  -d '{
    "grantType": "client_credentials",
    "clientId": "partner_acme_corp",
    "clientSecret": "<the-secret>"
  }'
```

**Response**:
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "refreshToken": "b4a5f0c0-strong-random-value",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "scope": "tracking:read shipment:create"
}
```

### Call a protected API

```bash
curl https://api.company.com/tracking/AWB123 \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIs..."
```

### Refresh the token (before it expires)

```bash
curl -X POST https://api.company.com/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "b4a5f0c0-strong-random-value"
  }'
```

Returns a new access + refresh token pair. The old refresh token is invalidated (rotation).

---

## 5. API Endpoint → Scope Mapping

Each protected API requires a specific scope as defined in `auth_api_scope_mapping`:

| HTTP Method | API Path | Required Scope |
|---|---|---|
| GET | `/tracking/{awb}` | `tracking:read` |
| POST | `/shipment` | `shipment:create` |
| PUT | `/shipment/{id}` | `shipment:update` |
| POST | `/status/update` | `status:update` |
| GET | `/invoice/{id}` | `invoice:read` |

Path matching supports wildcards: `/tracking/**` matches any tracking path.

**Example**: if a client has only `tracking:read` assigned but calls `POST /shipment`, they receive:
```json
{
  "status": 403,
  "error": "FORBIDDEN",
  "code": "AUTH_INSUFFICIENT_SCOPE",
  "message": "Token does not have the required scope"
}
```

---

## 6. Client Status Lifecycle

```
CREATED → ACTIVE → INACTIVE  → ACTIVE   (reactivation)
                  → LOCKED   → ACTIVE   (manual unlock after lockout)
                  → SUSPENDED
                  → DELETED   (soft delete, irreversible)
```

| Status | Token Issuance | API Access | Notes |
|---|---|---|---|
| `ACTIVE` | Allowed | Allowed | Normal operation |
| `INACTIVE` | Denied (403) | Denied | Manual deactivation |
| `LOCKED` | Denied (403) | Denied | Automatic after 5 failed auth attempts; also settable manually |
| `SUSPENDED` | Denied (403) | Denied | Compliance or policy suspension |
| `DELETED` | Denied (401) | Denied | Soft delete; client_id preserved for audit |

---

## 7. Secret Rotation

When a client secret is compromised or expires:

```sql
-- 1. Update the hash with the new secret
UPDATE auth_client
SET client_secret_hash = '<new-hash>',
    updated_at = NOW()
WHERE client_id = 'partner_acme_corp';

-- 2. Optionally revoke all active tokens for this client
--    (handled at application level via Redis)
```

**Procedure**:
1. Generate a new secret and hash (same process as Step 2)
2. Update `auth_client.client_secret_hash`
3. If the compromise is suspected, also revoke all active tokens via the admin endpoint
4. Provide the new secret to the partner out-of-band
5. Write an audit event: `INSERT INTO auth_token_audit (client_id, event_type, status) VALUES ('partner_acme_corp', 'SECRET_ROTATED', 'SUCCESS');`

---

## 8. Revoke All Tokens for a Client

In case of compromise or deactivation:

```sql
-- Mark client as inactive/suspended
UPDATE auth_client SET status = 'SUSPENDED', updated_at = NOW()
WHERE client_id = 'partner_acme_corp';
```

At the application layer, an admin endpoint iterates through `auth:client:{clientId}:sessions` in Redis and revokes every active jti. This is available via the L2 internal controller.

---

## 9. Adding a New Scope

If a new API is added that requires a new scope:

```sql
-- 1. Create the scope
INSERT INTO auth_scope (scope_name, description)
VALUES ('billing:read', 'View billing information');

-- 2. Map it to the API endpoint
INSERT INTO auth_api_scope_mapping (http_method, api_pattern, required_scope, status)
VALUES ('GET', '/billing/invoice', 'billing:read', 'ACTIVE');
```

Then grant it to specific clients:

```sql
INSERT INTO auth_client_scope (client_id, scope_name)
VALUES ('partner_acme_corp', 'billing:read');
```

Existing tokens won't include the new scope — the partner must obtain a new token after the scope is assigned.

---

## 10. Troubleshooting

### "Invalid client credentials" (401)
- Verify the `clientId` exists in `auth_client`
- Check the client `status` is `ACTIVE`
- Verify the client secret hash matches — run the hash generation again with the same secret

### "Client is locked" (429)
- 5 failed auth attempts in 10 minutes trigger a 30-minute lock
- To manually unlock: `UPDATE auth_client SET status = 'ACTIVE', updated_at = NOW() WHERE client_id = '...'` and delete `auth:lock:{clientId}` from Redis

### "Token does not have the required scope" (403)
- Check `auth_client_scope` for the client — the scope must be assigned
- Check `auth_api_scope_mapping` — the API path must have a mapping
- The partner must get a new token after scope changes

### "Token expired" (401)
- Access tokens expire in 15 minutes
- The client must use the refresh token to get a new pair
- If the refresh token also expired (7 days), the client must re-authenticate
