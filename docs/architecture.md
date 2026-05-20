# Application Architecture Document

## 1. System Overview

The system provides authenticated API access for external partners through a two-tier service architecture. External clients authenticate using client credentials (OAuth2 client_credentials grant) and receive JWT access tokens with scope-based authorization.

```
External Partner
    |
    v
DNS / WAF / Load Balancer
    |
    v
L1 External API Service  (port 8080, stateless, horizontal scale)
    |
    v
L2 Auth/Application Service  (port 8081, stateless, horizontal scale)
    |
    +---> Redis Cluster (3 nodes + sentinels)
    |       - Session state
    |       - Token revocation
    |       - Rate limits
    |       - Failed-auth counters
    |
    +---> PostgreSQL
            - Clients, scopes, permissions
            - Audit logs
```

## 2. Service Responsibilities

### L1 External API Service (`l1-external-api-service/`)

**Purpose**: External-facing API gateway. Thin proxy — no business logic, no DB or Redis access.

| Aspect | Detail |
|---|---|
| Port | 8080 |
| Framework | Spring Boot 3.5.11 + WebFlux (WebClient) |
| State | Stateless |
| Scaling | Horizontal (round-robins across L2 instances) |

**Responsibilities**:
- Expose external REST endpoints (`/auth/token`, `/auth/refresh`, `/auth/logout`, business APIs)
- Validate request format and content-type
- Add `X-Correlation-Id` to every request
- Extract Bearer token from `Authorization` header
- Forward requests to L2 via WebClient with round-robin load balancing
- Return standardized responses to external clients
- Do NOT access Redis or DB directly

**Endpoints**:

| Method | Path | Auth Required | Description |
|---|---|---|---|
| POST | `/auth/token` | No | Issue tokens via client credentials |
| POST | `/auth/refresh` | No | Refresh access token |
| POST | `/auth/logout` | Yes (Bearer) | Revoke current token |
| GET | `/tracking/{awb}` | Yes (Bearer) | Get tracking info |
| POST | `/shipment` | Yes (Bearer) | Create shipment |
| POST | `/status/update` | Yes (Bearer) | Update status |

**Key Classes**:

| Class | Role |
|---|---|
| `AuthProxyController` | `/auth/*` — proxies auth requests to L2 |
| `TrackingController` | `/tracking/*` — proxies tracking to L2 |
| `ShipmentController` | `/shipment/*` — proxies shipment to L2 |
| `StatusController` | `/status/*` — proxies status to L2 |
| `L2ClientService` | Round-robin WebClient calls to L2 instances |
| `CorrelationIdService` | UUID generation for X-Correlation-Id |
| `RequestValidationService` | Content-type and payload size validation |
| `GlobalExceptionHandler` | Standardized error responses from L2 errors |

### L2 Auth/Application Service (`l2-auth-application-service/`)

**Purpose**: Internal service that owns all auth logic, token lifecycle, permission checks, and business processing.

| Aspect | Detail |
|---|---|
| Port | 8081 |
| Framework | Spring Boot 3.5.11 + JPA + Redis |
| State | Stateless (state in Redis/DB) |
| Scaling | Horizontal (shared Redis cluster + DB) |

**Responsibilities**:
- Validate client credentials against DB
- Generate RS256-signed JWT access tokens
- Manage refresh token lifecycle with rotation
- Store/validate token metadata in Redis
- Check revocation status in Redis
- Enforce scope-based authorization
- Enforce client lockout after 5 failed attempts
- Write audit events to DB
- Execute business logic

**Key Classes**:

| Class | Role |
|---|---|
| `InternalAuthController` | `/internal/auth/*` — token issue, refresh, logout |
| `InternalTrackingController` | `/internal/tracking/*` — validated tracking |
| `InternalShipmentController` | `/internal/shipment/*` — validated shipment |
| `InternalStatusController` | `/internal/status/*` — validated status update |
| `JwtTokenProvider` | RS256 JWT generation and validation with 30s clock skew |
| `TokenValidator` | Signature + expiry + revocation check |
| `ClientSecretHasher` | SHA-256 + salt for client secret storage |
| `TokenService` | Token issuance and jti extraction |
| `RefreshTokenService` | Refresh with rotation (old token invalidated) |
| `ClientService` | Credential validation, lockout policy |
| `ScopeService` | Resolve client → scope mappings |
| `PermissionService` | Check token scope against API requirements |
| `AuditService` | Write TOKEN_ISSUED/REVOKED/LOGIN_FAILED events |

## 3. Authentication Flow

### 3.1 Token Issuance

```
Client                    L1                       L2                          Redis    DB
  |                       |                        |                            |       |
  | POST /auth/token      |                        |                            |       |
  |---------------------->|                        |                            |       |
  |                       | X-Correlation-Id       |                            |       |
  |                       |------------------------|---> /internal/auth/token   |       |
  |                       |                        |                            |       |
  |                       |                        |--- Fetch client ----------------->|
  |                       |                        |<-- client + hash ------------|
  |                       |                        |                            |       |
  |                       |                        |--- Validate secret          |       |
  |                       |                        |--- Load client scopes ----->|       |
  |                       |                        |<-- scope list --------------|       |
  |                       |                        |                            |       |
  |                       |                        |--- Generate JWT (RS256)     |       |
  |                       |                        |--- Generate refresh token   |       |
  |                       |                        |                            |       |
  |                       |                        |--- Store access meta ------>|       |
  |                       |                        |--- Store session entry ---->|       |
  |                       |                        |                            |       |
  |                       |                        |--- Write audit ------------------->|
  |                       |                        |                            |       |
  |                       |<-- token response -----|                            |       |
  |<-- token response ----|                        |                            |       |
```

### 3.2 Protected API Access

```
Client                    L1                       L2                          Redis    DB
  |                       |                        |                            |       |
  | GET /tracking/{awb}   |                        |                            |       |
  | Authorization: Bearer |                        |                            |       |
  |---------------------->|                        |                            |       |
  |                       | Extract Bearer          |                            |       |
  |                       | X-Correlation-Id        |                            |       |
  |                       |------------------------|---> /internal/tracking/{awb}|       |
  |                       |                        |                            |       |
  |                       |                        |--- Verify JWT signature     |       |
  |                       |                        |--- Check exp + 30s skew     |       |
  |                       |                        |--- Check revoked? -------->|       |
  |                       |                        |<-- not revoked -------------|       |
  |                       |                        |                            |       |
  |                       |                        |--- Check scope vs API ----->|       |
  |                       |                        |<-- scope mapping -----------|       |
  |                       |                        |                            |       |
  |                       |                        |--- Execute business logic   |       |
  |                       |                        |                            |       |
  |                       |<-- response -----------|                            |       |
  |<-- response ----------|                        |                            |       |
```

### 3.3 Token Refresh with Rotation

```
Client                    L1                       L2                          Redis
  |                       |                        |                            |
  | POST /auth/refresh    |                        |                            |
  |---------------------->|                        |                            |
  |                       |------------------------|---> /internal/auth/refresh |
  |                       |                        |                            |
  |                       |                        |--- SHA-256(refresh token)  |
  |                       |                        |--- Lookup hash ----------->|
  |                       |                        |<-- metadata (clientId,jti)-|
  |                       |                        |                            |
  |                       |                        |--- DELETE old refresh ---->|
  |                       |                        |--- DELETE old session ---->|
  |                       |                        |--- DELETE old access ----->|
  |                       |                        |                            |
  |                       |                        |--- Generate NEW token pair |
  |                       |                        |--- Store NEW refresh ------>|
  |                       |                        |--- Store NEW session ------>|
  |                       |                        |--- Write audit             |
  |                       |                        |                            |
  |                       |<-- new tokens ---------|                            |
  |<-- new tokens --------|                        |                            |
```

### 3.4 Logout / Revocation

```
Client                    L1                       L2                          Redis    DB
  |                       |                        |                            |
  | POST /auth/logout     |                        |                            |
  | Authorization: Bearer |                        |                            |
  |---------------------->|                        |                            |
  |                       | Extract Bearer          |                            |
  |                       |------------------------|---> /internal/auth/logout  |
  |                       |                        |                            |
  |                       |                        |--- Validate token          |
  |                       |                        |--- Extract jti             |
  |                       |                        |--- Set revoked:jti ------->| (TTL = remaining lifetime)
  |                       |                        |--- Delete access:jti ----->|
  |                       |                        |--- Delete session -------->|
  |                       |                        |--- Remove from client set->|
  |                       |                        |                            |
  |                       |                        |--- Write TOKEN_REVOKED ---------------->|
  |                       |                        |                            |
  |                       |<-- 200 OK -------------|                            |
  |<-- 200 OK ------------|                        |                            |
```

## 4. Redis Key Design

All Redis keys are prefixed for namespacing. L2 is the only service that accesses Redis.

| Key | Type | TTL | Purpose |
|---|---|---|---|
| `auth:access:jti:{jti}` | Hash | 900s (access token TTL) | Active token metadata |
| `auth:revoked:jti:{jti}` | String | Remaining access TTL | Blacklisted token marker |
| `auth:session:{jti}` | String | 900s (access token TTL) | TTL-backed session entry |
| `auth:refresh:{sha256}` | Hash | 7 days | Refresh token metadata |
| `auth:client:{id}:sessions` | Set | None (managed) | Active jti values per client |
| `auth:failure:{clientId}` | String | 10 min | Failed auth counter |
| `auth:lock:{clientId}` | String | 30 min | Client lock |

**Session Cleanup**: Individual `auth:session:{jti}` keys expire naturally via TTL. The `auth:client:{id}:sessions` Set is a secondary index that is pruned:
- On reads via `getActiveClientSessions()` — stale entries removed inline
- Every 5 minutes via `SessionCleanupTask` — SCANs all client session keys and prunes

## 5. Database Schema

| Table | Purpose | Key Columns |
|---|---|---|
| `auth_client` | External client registrations | `client_id`, `client_secret_hash`, `status` |
| `auth_scope` | Available permission scopes | `scope_name`, `description` |
| `auth_client_scope` | M × N client-to-scope mapping | `client_id`, `scope_name` |
| `auth_api_scope_mapping` | API endpoint → required scope | `http_method`, `api_pattern`, `required_scope` |
| `auth_token_audit` | Token lifecycle audit trail | `client_id`, `token_id`, `event_type` |

**Client statuses**: `ACTIVE`, `INACTIVE`, `LOCKED`, `SUSPENDED`, `DELETED`

**Audit event types**: `TOKEN_ISSUED`, `TOKEN_REFRESHED`, `TOKEN_REVOKED`, `TOKEN_EXPIRED`, `LOGIN_FAILED`, `CLIENT_LOCKED`, `SECRET_ROTATED`, `CLIENT_TOKENS_REVOKED`

## 6. JWT Token Structure

### Header
```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "auth-key-2026-05"
}
```

### Payload
```json
{
  "iss": "l2-auth-application-service",
  "sub": "external_partner_001",
  "clientId": "external_partner_001",
  "scope": "tracking:read shipment:create",
  "jti": "550e8400-e29b-41d4-a716-446655440000",
  "iat": 1745100000,
  "exp": 1745100900
}
```

### Claims

| Claim | Source | Purpose |
|---|---|---|
| `iss` | Fixed (`l2-auth-application-service`) | Verify token origin |
| `sub` | `clientId` from request | Subject identifier |
| `clientId` | `clientId` from request | Client identity for DB lookups |
| `scope` | From `auth_client_scope` + DB join | Space-delimited permission string |
| `jti` | UUID v4 | Unique token ID for revocation & audit |
| `iat` | Current time | Issuance timestamp |
| `exp` | `iat + 900s` | Expiry (checked with 30s clock skew) |

## 7. Security Design

### Network Isolation
```
Source → Destination     | Allowed
External → L1            | Yes
External → L2            | No
External → Redis         | No
External → DB            | No
L1 → L2                  | Yes
L1 → Redis               | No
L1 → DB                  | No
L2 → Redis               | Yes
L2 → DB                  | Yes
```

### Client Secrets
- Stored as `SHA-256(salt + secret)` with random 16-byte salt
- Format in DB: `base64(salt):base64(hash)`
- Shown only once at creation time, never logged

### Token Security
- Asymmetric RS256 signing — private key on L2 only
- Key rotation via `kid` header
- Refresh token rotation — each refresh invalidates the old pair
- Revoked tokens tracked in Redis via `auth:revoked:jti:{jti}`
- No token logging — only jti, clientId, correlationId in logs

### Client Lockout
- 5 failed auth attempts in 10 minutes → 30-minute lock
- Lock state in Redis (`auth:lock:{clientId}`, TTL 30 min)
- Counter resets on successful auth

### Fail-Closed Behavior
- Redis down → all protected API calls fail (can't check revocation)
- DB down → `/auth/token` fails; existing tokens may still be validated from Redis
- L2 down → L1 returns 503

## 8. Error Response Standards

| Scenario | HTTP | `error` | `code` |
|---|---|---|---|
| Missing token | 401 | UNAUTHORIZED | AUTH_TOKEN_MISSING |
| Invalid token | 401 | UNAUTHORIZED | AUTH_TOKEN_INVALID |
| Expired token | 401 | UNAUTHORIZED | AUTH_TOKEN_EXPIRED |
| Revoked token | 401 | UNAUTHORIZED | AUTH_TOKEN_REVOKED |
| Invalid credentials | 401 | UNAUTHORIZED | AUTH_INVALID_CLIENT |
| Insufficient scope | 403 | FORBIDDEN | AUTH_INSUFFICIENT_SCOPE |
| Client locked/disabled | 403 | FORBIDDEN | AUTH_CLIENT_INACTIVE |
| Rate limit exceeded | 429 | TOO_MANY_REQUESTS | RATE_LIMIT_EXCEEDED |
| L2 unavailable | 503 | SERVICE_UNAVAILABLE | L2_SERVICE_UNAVAILABLE |

All error responses follow the same structure:
```json
{
  "status": 401,
  "error": "UNAUTHORIZED",
  "code": "AUTH_TOKEN_EXPIRED",
  "message": "Access token expired"
}
```

## 9. Deployment

### Components
```
l1-external-api-service   — JAR, port 8080, stateless
l2-auth-application-service — JAR, port 8081, stateless
redis cluster             — 3 nodes + sentinels
postgresql                — Primary + replicas
```

### Configuration (L1)
```yaml
l2:
  service:
    base-urls:
      - http://l2-app-1:8081
      - http://l2-app-2:8081
```

### Configuration (L2)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres-host:5432/user_auth
    hikari:
      maximum-pool-size: 10   # scale with (#L2 instances × pool) ≤ DB max_connections
  data:
    redis:
      host: redis-cluster-host
      port: 6379
      timeout: 2000ms

auth:
  jwt:
    clock-skew-seconds: 30
  session:
    cleanup-interval-ms: 300000
```

### Startup Order
1. PostgreSQL + Redis cluster
2. L2 Auth/Application Service (Flyway migrations run on startup)
3. L1 External API Service (connects to L2 via WebClient)

## 10. Observability

### Log Fields (every request)
```
correlationId  — UUIDv4 generated by L1
clientId       — extracted from token (or "anonymous")
tokenId/jti    — from JWT claims
httpMethod     — GET/POST/PUT
apiPath        — /tracking/{awb}, /auth/token, etc.
statusCode     — HTTP response code
responseTime   — processing duration in ms
sourceIp       — original client IP (X-Forwarded-For)
userAgent      — original User-Agent
errorCode      — AUTH_* or null
```

### Metrics
```
auth.token.issued.count       — Counter
auth.token.failed.count       — Counter
auth.token.refreshed.count    — Counter
auth.token.revoked.count      — Counter
auth.invalid_token.count      — Counter
auth.insufficient_scope.count — Counter
api.request.count             — Counter (by path, method, status)
api.request.latency           — Histogram
redis.token.lookup.latency    — Histogram
db.client.lookup.latency      — Histogram
```

### Alerts
- High token failure rate per client
- Elevated 401/403/429 rates
- Redis cluster unavailable
- DB unavailable/unhealthy
- JWT signing failure
- Traffic spike from single client
- Repeated failed client credential attempts
