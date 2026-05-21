# Adding a New Protected API

This guide walks through the full process of adding a new microservice API endpoint that requires token validation.

---

## 1. Overview

Every protected API involves changes in **both** L1 and L2 services:

```
External Partner            L1 (proxy)              L2 (auth + logic)
     |                         |                         |
     |  GET /api/foo           |                         |
     |------------------------>|                         |
     |                         |  GET /internal/foo      |
     |                         |  Authorization: Bearer  |
     |                         |------------------------>|
     |                         |                         |-- validate JWT
     |                         |                         |-- check scope
     |                         |                         |-- business logic
     |                         |                         |
     |  { "result": ... }      |                         |
     |<------------------------|-------------------------|
```

---

## 2. Step-by-Step Process

### Step 1: Define the Scope (if new)

If your API needs a permission scope that doesn't exist yet, add it to the database:

```sql
INSERT INTO auth_scope (scope_name, description)
VALUES ('billing:read', 'View billing information');
```

Also add it to the Flyway seed file `V2__seed_data.sql` (use `MERGE INTO` pattern) so new environments get it automatically.

### Step 2: Map the API to the Required Scope

Insert into `auth_api_scope_mapping` to link the API path with the required scope:

```sql
INSERT INTO auth_api_scope_mapping (http_method, api_pattern, required_scope, status)
VALUES ('GET', '/billing/invoice', 'billing:read', 'ACTIVE');
```

Also add this to `V2__seed_data.sql` for new environments.

### Step 3: Grant the Scope to Clients

Assign the scope to each client that needs access:

```sql
INSERT INTO auth_client_scope (client_id, scope_name)
VALUES ('partner_acme_corp', 'billing:read');
```

Existing tokens won't include the new scope — partners must obtain a new token after assignment.

### Step 4: Create the L2 Internal Controller

In `l2-auth-application-service`, create a new controller under `com.company.l2app.controller`:

```java
@RestController
@RequestMapping("/internal/billing")
public class InternalBillingController {

    private final TokenValidator tokenValidator;
    private final PermissionService permissionService;
    private final BusinessService businessService;

    // constructor injection

    @GetMapping("/invoice")
    public ResponseEntity<?> getInvoice(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam String id) {

        String token = authHeader.replace("Bearer ", "");

        // 1. Validate token (signature + expiry + revocation)
        ValidationResult result = tokenValidator.validate(token);
        if (!result.valid()) {
            return ResponseEntity.status(401).body(error(result.code(), result.message()));
        }

        // 2. Check scope permission
        if (!permissionService.hasPermission(token, "GET", "/billing/invoice")) {
            return ResponseEntity.status(403).body(error("AUTH_INSUFFICIENT_SCOPE",
                    "Token does not have the required scope"));
        }

        // 3. Execute business logic
        var data = businessService.getInvoice(id);
        return ResponseEntity.ok(data);
    }
}
```

### Step 5: Create the L1 Proxy Controller

In `l1-external-api-service`, create a controller under `com.company.l1api.controller`:

```java
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final L2ClientService l2Client;

    // constructor injection

    @GetMapping("/invoice")
    public Mono<ResponseEntity<String>> getInvoice(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam String id) {

        String token = authHeader.replace("Bearer ", "");

        return l2Client.forwardRequest(
            "/internal/billing/invoice?id=" + id,
            HttpMethod.GET,
            token,
            null,
            String.class
        );
    }
}
```

### Step 6: Register the L2 URL in L1 Config (if new base URL)

If the new API lives on a different L2 service (not the same `l2-auth-application-service`), add its base URL to `l1-external-api-service/src/main/resources/application.properties`:

```properties
l2.service.base-urls[2]=http://billing-service:8082
```

Then update `L2ClientService` and `WebClientConfig` to support multiple service groups.

---

## 3. Token Validation Flow (Reference)

When a request arrives at L2, the token goes through exactly 3 checks:

| # | Check | Component | Failure Code |
|---|-------|-----------|-------------|
| 1 | JWT signature + structure | `JwtTokenProvider.validateToken()` | `AUTH_TOKEN_INVALID` |
| 2 | Token expiry | `JwtTokenProvider.isTokenExpired()` | `AUTH_TOKEN_EXPIRED` |
| 3 | Revocation (Redis) | `TokenRedisRepository.isTokenRevoked()` | `AUTH_TOKEN_REVOKED` |

After validation, `PermissionService.hasPermission()` checks the token's `scope` claim against the `auth_api_scope_mapping` table.

---

## 4. File Checklist

| File | Action |
|------|--------|
| `V2__seed_data.sql` | Add scope + API mapping (if new) |
| L2: `controller/InternalXxxController.java` | **Create** — validate, check scope, business logic |
| L1: `controller/XxxController.java` | **Create** — extract token, forward to L2 |
| `application.properties` (L1) | Add base URL (if separate service) |
| Database | Run SQL for scope + mapping + client grants |

---

## 5. Testing

```bash
# 1. Get a token
TOKEN=$(curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"grantType":"client_credentials","clientId":"external_partner_001","clientSecret":"secret123"}' \
  | jq -r '.accessToken')

# 2. Call the new API
curl -s http://localhost:8080/api/billing/invoice?id=INV001 \
  -H "Authorization: Bearer $TOKEN"

# 3. Verify 403 without the right scope
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/billing/invoice?id=INV001

# 4. Verify 401 with an expired/invalid token
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/billing/invoice?id=INV001 \
  -H "Authorization: Bearer badtoken"
```
