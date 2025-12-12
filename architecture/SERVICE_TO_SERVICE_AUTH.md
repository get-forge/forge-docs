# Service-to-Service Authentication Explained

## Current Architecture (User Token Forwarding)

**How it works now:**
```
Frontend User → Gets JWT token (user identity)
     ↓
backend-candidate → Forwards user JWT to candidate-service
     ↓
candidate-service → Validates user JWT (knows it's from a user)
```

**What the receiving service knows:**
- ✅ The token is valid (signed by Cognito)
- ✅ Which user is making the request
- ❌ **Which service** is making the call (could be backend-candidate, could be an attacker with a stolen user token)
- ❌ Whether this is a legitimate service call or a direct user call

## The Problem: Missing Service Identity

### Scenario 1: Compromised User Token

**Attack:**
1. Attacker steals a user's JWT token (XSS, man-in-the-middle, etc.)
2. Attacker directly calls `document-service` with the stolen user token
3. `document-service` validates the token → ✅ Valid user token
4. `document-service` processes the request → ❌ But it doesn't know this isn't coming from `backend-candidate`

**Current system:** Can't distinguish between:
- Legitimate: `backend-candidate` forwarding user X's token → `document-service`
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
backend-candidate → parse-service ❌ (should be blocked, but currently can't)
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

## Your Current Architecture

**What you have:**
- ✅ User authentication (users get JWTs)
- ✅ Token forwarding (services forward user JWTs)
- ✅ Token validation (services validate JWTs)
- ❌ Service identity (services don't have their own identity)
- ❌ Service authorization (can't restrict calls to specific services)

**Is this a problem?**
- **For now:** Probably fine if:
  - All services are in a trusted network (VPC, private network)
  - You don't have background jobs that need to make service calls
  - You don't need service-level authorization
  - You're okay with "any valid user token can call any service"

- **You'll need service accounts if:**
  - Services are exposed to untrusted networks
  - You need background jobs/scheduled tasks
  - You want service-level authorization
  - You're implementing zero-trust architecture
  - You need to audit which services are making calls

## Implementation (If Needed)

Per ADR-0005, you would:
1. Create service accounts in Cognito (one per service)
2. Services authenticate with Cognito to get service JWTs
3. Services include service JWT in calls to other services
4. Receiving services validate service JWT and check service identity
5. Implement service-level authorization rules

But this is **not implemented yet** - your current system works fine for user-initiated requests.
