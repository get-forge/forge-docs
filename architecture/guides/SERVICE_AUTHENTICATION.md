# Service authentication

User login, Cognito form auth, and LinkedIn: [USER_AUTHENTICATION.md](USER_AUTHENTICATION.md).

## Current architecture (user token forwarding)

**How it works now:**

```
Frontend User → Gets JWT token (user identity)
     ↓
actor-bff → Forwards user JWT to actor-service
     ↓
actor-service → Validates user JWT (knows it's from a user)
```

**What the receiving service knows:**
- ✅ The token is valid (signed by Cognito)
- ✅ Which user is making the request
- ❌ **Which service** is making the call (could be actor-bff, could be an attacker with a stolen user token)
- ❌ Whether this is a legitimate service call or a direct user call

## The Problem: Missing Service Identity

### Scenario 1: Compromised User Token

**Attack:**
1. Attacker steals a user's JWT token (XSS, man-in-the-middle, etc.)
2. Attacker directly calls `document-service` with the stolen user token
3. `document-service` validates the token → ✅ Valid user token
4. `document-service` processes the request → ❌ But it doesn't know this isn't coming from `actor-bff`

**Current system:** Can't distinguish between:
- Legitimate: `actor-bff` forwarding user X's token → `document-service`
- Attack: Attacker directly calling `document-service` with user X's token

### Scenario 2: Background Jobs / Scheduled Tasks

**Problem:**
- You want a service to run a scheduled task (e.g., "clean up old resumes every night")
- The task isn't tied to any specific user
- Current system: Can't make service calls without a user token

**Example:**

```java
// This won't work - no user token available
@Scheduled(every = "1 day")
void cleanupOldResumes() {
    documentService.deleteOldResumes(); // ❌ Needs user JWT, but no user!
}
```

### Scenario 3: Service-Level Authorization

**Problem:**
- You want `parse-service` to only accept calls from `document-service`
- Currently: Any service (or attacker) with a valid user token can call it
- You can't say "only document-service is allowed to call parse-service"

**Example:**

```
document-service → parse-service ✅ (should work)
actor-bff → parse-service ❌ (should be blocked, but currently can't)
attacker → parse-service ❌ (should be blocked, but currently can't distinguish)
```

## Service-to-Service Authentication Solution

### How It Works

**Service Accounts:**
1. Each service has a **service account** in Cognito (separate from user accounts)
2. Services authenticate with Cognito using their service credentials
3. Services receive **service JWTs** (different from user JWTs)
4. Service JWTs contain service identity claims (e.g., `service_id: "document-service"`)

**Service Calls:**

```
document-service → Authenticates with Cognito → Gets service JWT
     ↓
document-service → Calls parse-service with service JWT
     ↓
parse-service → Validates service JWT → Knows it's from document-service
     ↓
parse-service → Can check: "Is the caller document-service?" → ✅ Authorize
```

### What Service Accounts Enable

1. **Service Identity Verification:**
   - Receiving service knows **which service** is calling
   - Can implement service-level authorization
   - Can audit which services are making calls

2. **Background Jobs:**
   - Services can make calls without user context
   - Scheduled tasks can authenticate as the service
   - System-level operations don't need user tokens

3. **Security Isolation:**
   - Even if a user token is compromised, attacker can't impersonate services
   - Services have separate credentials from users
   - Can revoke service credentials independently

4. **Service Mesh / Zero Trust:**
   - Every service call is authenticated
   - No "trusted internal network" assumptions
   - Services verify each other's identity

## Real-World Examples

### Example 1: E-commerce Platform

**Scenario:** Order service needs to call inventory service

**Without service accounts:**
- Order service forwards user's JWT to inventory service
- Inventory service doesn't know if call is from order-service or an attacker
- Can't implement "only order-service can reserve inventory"

**With service accounts:**
- Order service authenticates as `order-service` account
- Gets service JWT with `service_id: "order-service"`
- Inventory service validates service JWT
- Inventory service checks: "Is caller order-service?" → ✅ Authorize

### Example 2: Scheduled Data Sync

**Scenario:** Analytics service needs to sync data every hour

**Without service accounts:**
- Can't make service calls without a user token
- Would need a "system user" account (bad practice)
- User token could expire, breaking scheduled jobs

**With service accounts:**
- Analytics service authenticates as `analytics-service`
- Gets long-lived service JWT (or refreshes automatically)
- Can make calls 24/7 without user context
- Scheduled jobs work reliably

### Example 3: Microservices Authorization

**Scenario:** Only specific services should access sensitive endpoints

**Without service accounts:**
- All services with valid user tokens can call any endpoint
- Can't restrict `admin-service` endpoints to only `admin-service`
- Security relies on network isolation (not good enough)

**With service accounts:**
- `admin-service` endpoints check: "Is caller admin-service?"
- Other services can't call admin endpoints even with valid user tokens
- Fine-grained service-level authorization

## Implementation details

### Service Accounts

Service accounts are created in Cognito using the seed script (`scripts/aws/sandbox-cognito-seed.sh`):
- Username format: `service-{service-name}` (e.g., `service-document-service`)
- Custom attribute: `custom:service_id` = `{service-name}`
- Credentials stored in AWS Parameter Store and `.envrc`

### Service Authentication Flow

```
1. Service starts up
   ↓
2. CachingServiceTokenProvider initializes (if credentials configured)
   ↓
3. Service makes REST client call
   ↓
4. `UserTokenClientRequestFilter` runs → forwards user token if present
   ↓
5. `ServiceTokenClientRequestFilter` runs → adds service token if no user token
   ↓
6. Receiving service receives request with service JWT
   ↓
7. TokenAuthenticationFilter validates token → detects custom:service_id claim
   ↓
8. Stores authenticatedServiceId in request context
   ↓
9. ServiceTokenAuthorizationInterceptor checks @AllowedServices annotation
   ↓
10. Request proceeds if service is authorized ✅
```

### Components

**Domain Interfaces:**
- `ServiceAuthenticationProvider` - Authenticate services
- `ServiceTokenProvider` - Get and cache service JWTs

**Infrastructure:**
- `CognitoServiceAuthenticationProvider` - Authenticates services with Cognito
- `CachingServiceTokenProvider` - Caches and refreshes service JWTs automatically

**Infrastructure:**
- `ServiceTokenClientRequestFilter` - Automatically injects service JWTs into outgoing REST client calls
- `ServiceTokenAuthorizationInterceptor` - Enforces `@AllowedServices` restrictions
- `@AllowedServices` - Annotation to restrict endpoints to specific services

### Configuration

Services need the following configuration to enable service-to-service authentication:

```properties
cognito.service-account.username=service-document-service
cognito.service-account.password=<password-from-parameter-store>
quarkus.application.name=document-service
```

These are automatically set by the Cognito seed script in AWS Parameter Store and `.envrc`.

### Zero-Trust Architecture

This implementation provides the foundation for zero-trust architecture:

✅ **Every service call is authenticated** - Services must have valid JWTs
✅ **Service identity verification** - Receiving services know which service is calling
✅ **Service-level authorization** - Fine-grained control over which services can access endpoints
✅ **No trusted network assumptions** - Services verify each other's identity regardless of network location
✅ **Credential isolation** - Service credentials are separate from user credentials
✅ **Automatic token management** - Tokens are cached and refreshed automatically

**What's in place:**
- Service-to-service authentication ✅
- Service-level authorization ✅
- Automatic token injection ✅
- Token caching and refresh ✅

**Potential future enhancements:**
- Mutual TLS (mTLS) for additional transport security
- Service mesh integration (Istio, Linkerd)
- Certificate-based service authentication
- Network policy enforcement
