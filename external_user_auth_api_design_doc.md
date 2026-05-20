# External User Authentication & Token Management Design Document

## 1. Objective

The objective of this design is to create a secure and maintainable authentication and token-management flow for external users or partners who need to access APIs exposed through the **Level 1 External API Service**.

The corrected target design uses only two application services:

```text
1. L1 External API Service
2. L2 Auth/Application Service
```

Redis and DB are infrastructure components accessed by L2. They are not separate business microservices.

The system should support:

- External client authentication
- Token generation
- Token validation
- Token refresh
- Token revocation/logout
- Role and scope-based API access
- Redis-backed runtime token/session management
- DB-backed persistent client, permission, and audit storage
- External access only through L1
- Redis and DB access only through L2

---

## 2. Final Requirement Summary

The expected architecture is:

```text
External User
   |
   v
External DNS / WAF / Load Balancer
   |
   v
L1 External API Service
   |
   v
L2 Auth/Application Service
   |
   +-------------> Redis
   |
   +-------------> DB
```

Important requirement:

```text
External users should access only L1.
L1 should call L2.
L2 should interface with Redis and DB.
L1 should not directly access Redis or DB.
```

There is no mandatory need for a separate Auth Microservice at this stage. Authentication and token management can be implemented inside the **L2 Auth/Application Service**.

A separate Auth Microservice may be considered later only if the system grows into many independent L2 services that all need centralized authentication.

---

## 3. Current System Context

Current structure:

```text
External DNS -> Level 1 API Layer -> Level 2 Application Layer -> Level 3 DB
```

Additional infrastructure:

```text
Redis
```

Corrected target structure:

```text
External DNS -> L1 External API Service -> L2 Auth/Application Service -> Redis / DB
```

L1 is the external-facing service.

L2 is the internal service that owns:

- Auth logic
- Token lifecycle
- Redis interaction
- DB interaction
- Permission checks
- Business processing

---

## 4. High-Level Architecture

```text
+-------------------+
| External Partner  |
+-------------------+
          |
          v
+-------------------+
| External DNS      |
+-------------------+
          |
          v
+-------------------+
| WAF / LB          |
+-------------------+
          |
          v
+--------------------------------------+
| L1 External API Service              |
|                                      |
| Responsibilities:                    |
| - Expose public APIs                 |
| - Accept external requests           |
| - Validate request format            |
| - Extract Authorization header       |
| - Add correlation ID                 |
| - Forward requests to L2             |
| - Format external responses          |
| - No direct DB access                |
| - Preferably no direct Redis access  |
+--------------------------------------+
          |
          v
+--------------------------------------+
| L2 Auth/Application Service          |
|                                      |
| Responsibilities:                    |
| - Validate client credentials        |
| - Generate tokens                    |
| - Validate tokens                    |
| - Refresh tokens                     |
| - Revoke tokens                      |
| - Check scopes/permissions           |
| - Execute business logic             |
| - Access Redis                       |
| - Access DB                          |
| - Write audit logs                   |
+--------------------------------------+
       |                     |
       v                     v
+-------------+        +-------------+
| Redis       |        | DB          |
| Runtime     |        | Persistent |
| token state |        | data       |
+-------------+        +-------------+
```

---

## 5. Service Responsibility Split

## 5.1 L1 External API Service

L1 is the only service exposed to external users.

Responsibilities:

- Expose external API endpoints
- Accept requests from DNS/WAF/LB
- Perform basic request validation
- Extract Bearer token from the `Authorization` header
- Add or propagate `X-Correlation-Id`
- Forward token and request payload to L2
- Return standardized responses to external users
- Hide internal service details
- Apply basic request-size and content-type validation
- Optionally apply coarse rate limiting at the edge

L1 should not contain core authentication logic.

L1 should not directly interact with DB.

L1 should preferably not directly interact with Redis in the initial design.

L1 acts as a secure external façade.

Example L1 endpoints:

```http
POST /auth/token
POST /auth/refresh
POST /auth/logout
GET  /tracking/{awb}
POST /shipment
POST /status/update
GET  /invoice/{id}
```

Even though these endpoints are exposed in L1, the actual logic is handled by L2.

---

## 5.2 L2 Auth/Application Service

L2 is internal only.

Responsibilities:

- Validate external client credentials
- Generate access tokens
- Generate refresh tokens
- Validate access tokens
- Refresh tokens
- Revoke/logout tokens
- Store and read token/session state in Redis
- Store and read client/user/permission/audit data from DB
- Perform scope and permission checks
- Execute business logic
- Return internal response to L1

L2 is the authority for authentication and authorization.

L2 owns Redis and DB communication.

---

## 5.3 Redis

Redis is used for runtime state.

Redis stores:

- Active token metadata
- Refresh token hashes
- Revoked token IDs
- Client sessions
- Rate-limit counters, if needed
- Failed-authentication counters
- Temporary locks

Redis should not be treated as the permanent source of truth for client configuration.

---

## 5.4 DB

DB stores persistent data.

DB stores:

- External clients
- Client secret hashes
- Roles
- Scopes
- Client-to-scope mappings
- API-to-scope mappings
- Token audit logs
- Client status
- Configuration

---

## 6. Authentication Model

The preferred model for external users is **system-to-system authentication**.

Each external partner/client receives:

```text
clientId
clientSecret
allowed scopes
```

The partner calls L1:

```http
POST /auth/token
```

L1 forwards the request to L2.

L2 validates the credentials and generates tokens.

---

## 7. Token Strategy

Recommended token strategy:

```text
JWT access token + Redis-backed refresh/revocation/session metadata
```

### 7.1 Access Token

Access token should be a signed JWT.

Recommended expiry:

```text
15 minutes
```

Recommended algorithm:

```text
RS256 or ES256
```

Example JWT claims:

```json
{
  "iss": "l2-auth-application-service",
  "sub": "external_partner_001",
  "clientId": "external_partner_001",
  "roles": ["PARTNER"],
  "scope": "tracking:read shipment:create",
  "jti": "8f7d7ad1-7d55-4d18-b9cd-456111aaa123",
  "iat": 1779270000,
  "exp": 1779270900
}
```

Important claims:

| Claim | Purpose |
|---|---|
| `iss` | Token issuer |
| `sub` | Subject/client/user |
| `clientId` | External client identity |
| `roles` | Role mapping |
| `scope` | API permissions |
| `jti` | Unique token ID for revocation and audit |
| `iat` | Issued at |
| `exp` | Expiry |

Do not store passwords, client secrets, or sensitive business data inside JWT.

---

### 7.2 Refresh Token

Refresh token should be a secure random value.

Recommended expiry:

```text
7 days to 30 days
```

Refresh token should be stored as a hash in Redis or DB, not as plain text.

Recommended:

```text
Use refresh token rotation.
Every refresh request invalidates the old refresh token and issues a new one.
```

---

## 8. API Design

## 8.1 Token Generation API

External API exposed by L1:

```http
POST /auth/token
Content-Type: application/json
```

Request:

```json
{
  "grantType": "client_credentials",
  "clientId": "external_partner_001",
  "clientSecret": "secret-value"
}
```

L1 processing:

```text
1. Validate request format.
2. Add correlation ID.
3. Forward request to L2.
4. Return L2 response to external user.
```

L2 processing:

```text
1. Fetch client by clientId from DB.
2. Check client status.
3. Validate clientSecret against stored hash.
4. Load allowed scopes from DB.
5. Generate JWT access token.
6. Generate refresh token.
7. Store token metadata in Redis.
8. Store refresh token hash in Redis.
9. Write token audit event in DB.
10. Return token response to L1.
```

Successful response:

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "refreshToken": "b4a5f0c0-strong-random-token",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "scope": "tracking:read shipment:create"
}
```

Failure response:

```json
{
  "status": 401,
  "error": "UNAUTHORIZED",
  "code": "AUTH_INVALID_CLIENT",
  "message": "Invalid client credentials"
}
```

---

## 8.2 Refresh Token API

External API exposed by L1:

```http
POST /auth/refresh
Content-Type: application/json
```

Request:

```json
{
  "refreshToken": "b4a5f0c0-strong-random-token"
}
```

L1 processing:

```text
1. Validate request format.
2. Forward refresh request to L2.
3. Return L2 response.
```

L2 processing:

```text
1. Hash incoming refresh token.
2. Check refresh token hash in Redis.
3. If missing or expired, reject request.
4. Load client/session metadata.
5. Delete old refresh token entry.
6. Generate new access token.
7. Generate new refresh token.
8. Store new refresh token hash.
9. Store new access token metadata.
10. Write audit log.
11. Return new token response.
```

Successful response:

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "refreshToken": "new-refresh-token-value",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "scope": "tracking:read shipment:create"
}
```

---

## 8.3 Logout / Revoke Current Token API

External API exposed by L1:

```http
POST /auth/logout
Authorization: Bearer <access-token>
```

L1 processing:

```text
1. Extract Authorization header.
2. Forward token to L2.
3. Return L2 response.
```

L2 processing:

```text
1. Validate token signature and expiry.
2. Extract token ID/jti.
3. Add jti to Redis revoked-token key.
4. Delete related refresh token, if available.
5. Write audit log.
6. Return success response.
```

Successful response:

```json
{
  "status": 200,
  "code": "AUTH_LOGOUT_SUCCESS",
  "message": "Token revoked successfully"
}
```

---

## 8.4 Protected Business APIs

External APIs exposed by L1:

```http
GET  /tracking/{awb}
POST /shipment
POST /status/update
GET  /invoice/{id}
```

Example request:

```http
GET /tracking/123456
Authorization: Bearer <access-token>
```

L1 processing:

```text
1. Validate request format.
2. Extract Authorization header.
3. Add correlation ID.
4. Forward request, token, and correlation ID to L2.
```

L2 processing:

```text
1. Validate access token.
2. Check token expiry.
3. Extract clientId, jti, scopes.
4. Check Redis for revoked token.
5. Check client status if required.
6. Resolve required scope for the API.
7. Check whether token has required scope.
8. Execute business logic.
9. Access DB if required.
10. Return response to L1.
```

---

## 9. Authorization Model

Use scope-based authorization.

Each protected API should have a required scope.

Example mapping:

| API | Required Scope |
|---|---|
| `GET /tracking/{awb}` | `tracking:read` |
| `POST /shipment` | `shipment:create` |
| `PUT /shipment/{id}` | `shipment:update` |
| `POST /status/update` | `status:update` |
| `GET /invoice/{id}` | `invoice:read` |

Token contains scopes:

```json
{
  "scope": "tracking:read shipment:create"
}
```

Validation rule:

```text
If token contains the required scope, allow.
If token is valid but scope is missing, return 403 Forbidden.
```

---

## 10. Request Flows

## 10.1 Token Generation Flow

```text
External Partner
      |
      | POST /auth/token
      v
L1 External API Service
      |
      | Forward clientId/clientSecret
      v
L2 Auth/Application Service
      |
      | Validate client credentials using DB
      | Load scopes from DB
      | Generate access token
      | Generate refresh token
      | Store token metadata in Redis
      | Write audit log in DB
      v
L1 External API Service
      |
      v
External Partner receives token
```

---

## 10.2 Protected API Flow

```text
External Partner
      |
      | GET /tracking/{awb}
      | Authorization: Bearer accessToken
      v
L1 External API Service
      |
      | Extract token
      | Add correlation ID
      | Forward request to L2
      v
L2 Auth/Application Service
      |
      | Validate token
      | Check Redis revoked/active status
      | Check required scope
      | Execute business logic
      | Query DB if needed
      v
L1 External API Service
      |
      v
External Partner receives response
```

---

## 10.3 Refresh Flow

```text
External Partner
      |
      | POST /auth/refresh
      v
L1 External API Service
      |
      | Forward refresh token
      v
L2 Auth/Application Service
      |
      | Hash refresh token
      | Validate in Redis
      | Delete old refresh token
      | Generate new access token
      | Generate new refresh token
      | Store new token metadata in Redis
      | Write audit log
      v
L1 External API Service
      |
      v
External Partner receives new token
```

---

## 10.4 Logout / Revocation Flow

```text
External Partner
      |
      | POST /auth/logout
      | Authorization: Bearer accessToken
      v
L1 External API Service
      |
      | Forward token to L2
      v
L2 Auth/Application Service
      |
      | Extract jti
      | Store jti as revoked in Redis
      | Delete refresh token mapping if available
      | Write audit log
      v
L1 External API Service
      |
      v
External Partner receives success response
```

---

## 11. Redis Key Design

Redis is accessed only by L2 in the initial design.

### 11.1 Active Access Token Metadata

```text
auth:access:jti:{jti}
```

Example value:

```json
{
  "clientId": "external_partner_001",
  "status": "ACTIVE",
  "issuedAt": "2026-05-20T10:00:00Z",
  "expiresAt": "2026-05-20T10:15:00Z"
}
```

TTL:

```text
Same as access token expiry, for example 15 minutes.
```

---

### 11.2 Revoked Access Token

```text
auth:revoked:jti:{jti}
```

Value:

```text
true
```

TTL:

```text
Remaining lifetime of access token.
```

---

### 11.3 Refresh Token

```text
auth:refresh:{refreshTokenHash}
```

Example value:

```json
{
  "clientId": "external_partner_001",
  "jti": "8f7d7ad1-7d55-4d18-b9cd-456111aaa123",
  "issuedAt": "2026-05-20T10:00:00Z",
  "expiresAt": "2026-05-27T10:00:00Z"
}
```

TTL:

```text
Refresh token validity period.
```

---

### 11.4 Client Active Sessions

```text
auth:client:{clientId}:sessions
```

Type:

```text
Set of active jti values
```

Purpose:

- Revoke all tokens for a client
- Track active sessions
- Debug active token count

---

### 11.5 Failed Authentication Counter

```text
auth:failure:{clientId}
```

Purpose:

- Count failed client authentication attempts
- Temporarily lock client after repeated failures

Example policy:

```text
5 failed attempts in 10 minutes -> lock for 30 minutes
```

---

### 11.6 Rate Limit Counter

If rate limiting is owned by L2:

```text
rate:{clientId}:{apiName}:{timeWindow}
```

Example:

```text
rate:external_partner_001:tracking-read:202605201030
```

If rate limiting is later moved to L1 or WAF, this key may not be needed in L2.

---

## 12. DB Design

## 12.1 `auth_client`

Stores external client details.

```sql
CREATE TABLE auth_client (
    id BIGINT PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL UNIQUE,
    client_name VARCHAR(255) NOT NULL,
    client_secret_hash VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

Possible statuses:

```text
ACTIVE
INACTIVE
LOCKED
SUSPENDED
DELETED
```

---

## 12.2 `auth_scope`

Stores available API scopes.

```sql
CREATE TABLE auth_scope (
    id BIGINT PRIMARY KEY,
    scope_name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL
);
```

Examples:

```text
tracking:read
shipment:create
shipment:update
status:update
invoice:read
```

---

## 12.3 `auth_client_scope`

Maps clients to allowed scopes.

```sql
CREATE TABLE auth_client_scope (
    id BIGINT PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL,
    scope_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE (client_id, scope_name)
);
```

---

## 12.4 `auth_api_scope_mapping`

Maps APIs to required scopes.

```sql
CREATE TABLE auth_api_scope_mapping (
    id BIGINT PRIMARY KEY,
    http_method VARCHAR(20) NOT NULL,
    api_pattern VARCHAR(255) NOT NULL,
    required_scope VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

Example data:

| HTTP Method | API Pattern | Required Scope |
|---|---|---|
| GET | `/tracking/**` | `tracking:read` |
| POST | `/shipment` | `shipment:create` |
| PUT | `/shipment/**` | `shipment:update` |
| POST | `/status/update` | `status:update` |

---

## 12.5 `auth_token_audit`

Stores token lifecycle audit events.

```sql
CREATE TABLE auth_token_audit (
    id BIGINT PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL,
    token_id VARCHAR(100),
    event_type VARCHAR(50) NOT NULL,
    ip_address VARCHAR(100),
    user_agent VARCHAR(500),
    status VARCHAR(30),
    failure_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL
);
```

Example event types:

```text
TOKEN_ISSUED
TOKEN_REFRESHED
TOKEN_REVOKED
TOKEN_EXPIRED
LOGIN_FAILED
CLIENT_LOCKED
SECRET_ROTATED
CLIENT_TOKENS_REVOKED
```

---

## 13. Internal L1-to-L2 Contract

L1 should forward requests to L2 with relevant headers.

Example headers:

```http
Authorization: Bearer <access-token>
X-Correlation-Id: abc-123
X-Forwarded-For: external-client-ip
X-External-User-Agent: original-user-agent
```

For protected business APIs, L1 should not decode and trust token claims as final authority in the initial design.

L2 should validate the token and decide whether the request is allowed.

---

## 14. Error Response Standards

Missing token:

```json
{
  "status": 401,
  "error": "UNAUTHORIZED",
  "code": "AUTH_TOKEN_MISSING",
  "message": "Authorization token is missing"
}
```

Expired token:

```json
{
  "status": 401,
  "error": "UNAUTHORIZED",
  "code": "AUTH_TOKEN_EXPIRED",
  "message": "Access token expired"
}
```

Invalid token:

```json
{
  "status": 401,
  "error": "UNAUTHORIZED",
  "code": "AUTH_TOKEN_INVALID",
  "message": "Invalid access token"
}
```

Insufficient scope:

```json
{
  "status": 403,
  "error": "FORBIDDEN",
  "code": "AUTH_INSUFFICIENT_SCOPE",
  "message": "Token does not have the required scope"
}
```

Invalid client credentials:

```json
{
  "status": 401,
  "error": "UNAUTHORIZED",
  "code": "AUTH_INVALID_CLIENT",
  "message": "Invalid client credentials"
}
```

Rate limit exceeded:

```json
{
  "status": 429,
  "error": "TOO_MANY_REQUESTS",
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Too many requests. Please try again later"
}
```

---

## 15. Security Design

## 15.1 Network Security

Network rule:

```text
External users -> L1 only
L1 -> L2 only
L2 -> Redis and DB
External users -> L2 denied
External users -> Redis denied
External users -> DB denied
L1 -> DB denied
L1 -> Redis denied, at least in initial design
```

Recommended access matrix:

| Source | Destination | Allowed? |
|---|---:|---|
| External user | L1 | Yes |
| External user | L2 | No |
| External user | Redis | No |
| External user | DB | No |
| L1 | L2 | Yes |
| L1 | Redis | No, initial design |
| L1 | DB | No |
| L2 | Redis | Yes |
| L2 | DB | Yes |

---

## 15.2 Transport Security

All external communication must use HTTPS.

```text
External User -> HTTPS -> DNS/WAF/LB -> L1
```

Internal service communication should also use TLS if possible.

---

## 15.3 Client Secret Security

Client secrets should never be stored as plain text.

Recommended:

```text
Store only hashed client secret using BCrypt, Argon2, or PBKDF2.
```

During client creation or secret rotation:

```text
1. Generate secret.
2. Show secret only once.
3. Store hash.
4. Never expose the secret again.
```

---

## 15.4 Token Logging Rules

Never log full tokens.

Allowed logging:

```text
clientId
jti/tokenId
correlationId
API path
HTTP method
response code
processing time
```

Avoid logging:

```text
accessToken
refreshToken
clientSecret
password
Authorization header
```

Masked token logging, if absolutely needed:

```text
eyJhbGci...last6
```

---

## 15.5 Token Expiry Recommendations

| Token Type | Recommended Expiry |
|---|---:|
| Access Token | 15 minutes |
| Refresh Token | 7 days to 30 days |
| Revoked Token Redis Key | Remaining access token lifetime |
| Failed-auth counter | 10 to 30 minutes |

---

## 15.6 JWT Signing Key Management

Use asymmetric signing.

Recommended:

```text
Private key -> L2 only
Public key -> only needed by L2 initially
```

Since L1 is not doing JWT validation in the initial design, L1 does not need the JWT public key.

If later L1 performs lightweight JWT validation, the public key can be shared with L1.

Support key rotation using `kid` in JWT header.

Example JWT header:

```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "auth-key-2026-05"
}
```

---

## 16. L1 vs L2 Token Validation Decision

There are two possible models.

## 16.1 Initial Recommended Model: L2 Validates Everything

```text
L1 extracts token and forwards it.
L2 validates token, Redis status, scope, and client status.
```

Pros:

- Simple design
- No duplicated authentication logic
- Redis and DB remain hidden behind L2
- Easier to maintain
- Clear ownership of auth logic

Cons:

- Invalid requests still reach L2
- Slightly more load on L2

This is the recommended starting model.

---

## 16.2 Future Optimization: L1 Performs Lightweight JWT Validation

Later, L1 may validate:

```text
JWT format
JWT signature
JWT expiry
```

L2 still validates:

```text
Redis revoked status
Client status
Scopes
Business permissions
```

This can reduce unnecessary L2 calls, but it adds complexity and duplicates part of auth logic.

Do not start with this unless traffic or performance requires it.

---

## 17. Observability

## 17.1 Logs

Every request should have a correlation ID.

Recommended log fields:

```text
correlationId
clientId, if known
tokenId/jti, if known
httpMethod
apiPath
statusCode
responseTime
sourceIp
userAgent
errorCode
```

Do not log secrets or tokens.

---

## 17.2 Metrics

Recommended metrics:

```text
auth.token.issued.count
auth.token.failed.count
auth.token.refreshed.count
auth.token.revoked.count
auth.invalid_token.count
auth.insufficient_scope.count
api.request.count
api.request.latency
api.rate_limit.exceeded.count
redis.token.lookup.latency
db.client.lookup.latency
```

---

## 17.3 Alerts

Recommended alerts:

```text
High token failure rate for a client
High 401 count
High 403 count
High 429 count
Redis unavailable
DB unavailable
JWT signing failure
Sudden traffic spike from one client
Repeated failed client credential attempts
```

---

## 18. Failure Handling

## 18.1 Redis Down

Since L2 depends on Redis for refresh tokens, revocation, and active token state, Redis outage affects auth behavior.

Recommended behavior:

```text
Token generation -> fail or degraded depending on whether Redis write is mandatory
Token validation -> fail closed for sensitive APIs
Token refresh -> fail
Logout/revoke -> fail or retry
```

Preferred security posture:

```text
Fail closed for protected APIs if Redis revocation/token status cannot be checked.
```

---

## 18.2 DB Down

DB is required for client credential validation and permission loading.

Recommended behavior:

```text
/auth/token -> fail if DB is unavailable
/auth/refresh -> may continue if all required metadata exists in Redis
protected APIs -> may continue if token and scope validation can be completed from token + Redis
```

---

## 18.3 L2 Down

If L2 is down, L1 cannot complete auth or business requests.

L1 should return standardized unavailable response.

Example:

```json
{
  "status": 503,
  "error": "SERVICE_UNAVAILABLE",
  "code": "L2_SERVICE_UNAVAILABLE",
  "message": "Service temporarily unavailable"
}
```

---

## 19. Recommended HTTP Status Codes

| Scenario | HTTP Status |
|---|---:|
| Missing token | 401 |
| Invalid token | 401 |
| Expired token | 401 |
| Revoked token | 401 |
| Invalid client credentials | 401 |
| Valid token but missing scope | 403 |
| Client disabled/suspended | 403 |
| Rate limit exceeded | 429 |
| Invalid request payload | 400 |
| L2 unavailable | 503 |
| Internal system error | 500 |
| Downstream timeout | 504 |

---

## 20. Suggested Spring Boot Package Design

Because the design has two application services, package structure can be split like this.

## 20.1 L1 External API Service

```text
com.company.l1api
  controller
    AuthProxyController
    TrackingController
    ShipmentController
    StatusController
  service
    L2ClientService
    RequestValidationService
    CorrelationIdService
  config
    WebClientConfig
    SecurityHeadersConfig
  dto
    TokenRequest
    TokenResponse
    ApiErrorResponse
```

L1 controllers should mostly validate and forward.

---

## 20.2 L2 Auth/Application Service

```text
com.company.l2app
  controller
    InternalAuthController
    InternalTrackingController
    InternalShipmentController
    InternalStatusController
  service
    TokenService
    ClientService
    RefreshTokenService
    ScopeService
    PermissionService
    AuditService
    BusinessService
  repository
    AuthClientRepository
    AuthScopeRepository
    TokenAuditRepository
    BusinessRepository
  redis
    TokenRedisRepository
    RefreshTokenRedisRepository
    RateLimitRedisRepository
  security
    JwtTokenProvider
    ClientSecretHasher
    TokenValidator
  entity
    AuthClient
    AuthScope
    AuthClientScope
    AuthTokenAudit
  dto
    TokenRequest
    TokenResponse
    ProtectedApiRequest
    ProtectedApiResponse
```

---

## 21. Deployment Design

Deployable application units:

```text
l1-external-api-service
l2-auth-application-service
```

Infrastructure:

```text
redis
database
```

Deployment rule:

```text
Only L1 is externally exposed.
L2 is internal-only.
Redis and DB are private.
```

---

## 22. Implementation Phases

## Phase 1: Basic Two-Service Flow

Deliverables:

- L1 service exposes `/auth/token`
- L1 forwards token request to L2
- L2 validates client from DB
- L2 generates JWT access token
- L2 returns token through L1

---

## Phase 2: Redis Token State

Deliverables:

- Store access token metadata in Redis
- Store refresh token hash in Redis
- Add `/auth/refresh`
- Add `/auth/logout`
- Add token revocation check in L2

---

## Phase 3: Protected APIs

Deliverables:

- L1 exposes first protected API, for example `/tracking/{awb}`
- L1 forwards token and request to L2
- L2 validates token
- L2 checks scope
- L2 executes business logic

---

## Phase 4: Authorization and Admin Configuration

Deliverables:

- Client-scope mapping
- API-scope mapping
- Admin client onboarding
- Client secret rotation
- Revoke all tokens for client

---

## Phase 5: Hardening and Observability

Deliverables:

- Audit logs
- Rate limiting
- Failed-auth lockout
- Metrics
- Alerts
- Masked logging
- Standard error responses

---

## 23. Open Design Decisions

| Decision | Recommended Default |
|---|---|
| Number of application services | 2: L1 and L2 |
| Does L1 access Redis? | No, initial design |
| Does L1 access DB? | No |
| Does L2 access Redis? | Yes |
| Does L2 access DB? | Yes |
| Token type | JWT access token |
| JWT algorithm | RS256 |
| Access token expiry | 15 minutes |
| Refresh token expiry | 7 days |
| Refresh token rotation | Yes |
| Token validation owner | L2 |
| Scope validation owner | L2 |
| External auth model | Client credentials |
| API authorization model | Scope-based |
| Level 2 public access | Never allowed |

---

## 24. Final Recommended Design

The corrected final design is:

```text
External users call L1 only.
L1 exposes all external APIs.
L1 does basic request validation and forwards requests to L2.
L2 owns authentication, authorization, token generation, token validation, token refresh, and token revocation.
L2 is the only application service that accesses Redis and DB.
Redis stores runtime token/session state.
DB stores persistent client, permission, and audit data.
```

Final architecture:

```text
External User
   |
External DNS / WAF / LB
   |
L1 External API Service
   |
L2 Auth/Application Service
   |
   +--> Redis
   |
   +--> DB
```

This keeps the design simple, layered, and aligned with the original requirement while still allowing future expansion if multiple L2 services or a dedicated Auth Service become necessary later.

