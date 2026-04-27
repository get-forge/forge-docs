# User authentication

OIDC package notes and LinkedIn flow details: [`services/auth-service/.../oidc/README.md`](https://github.com/get-forge/forge-platform/blob/main/services/auth-service/src/main/java/io/forge/services/auth/oidc/README.md).

## Overview

The Forge platform uses **fully stateless JWT-based authentication** across all modules. All
authentication flows return JWT tokens that are stored client-side and included in API requests via the
`Authorization` header.

See [ADR-0011: Stateless JWT Authentication](../decisions/0011-stateless-jwt-authentication.md) for the architectural
decision.

## Architecture Principles

1. **Stateless**: No server-side session storage
2. **Unified Model**: Single `@Secured` annotation for all authentication
3. **Client-Side Storage**: JWT tokens stored in localStorage (web) or secure storage (mobile)
4. **Automatic Validation**: JAX-RS filter validates tokens on every request
5. **Horizontal Scaling**: No session affinity required

## Authentication Flows

### Form-Based Login (Primary)

```
Frontend → POST /auth/login (backend-actor)
         → POST /auth/login (auth-service)
         → Cognito User Pool (authenticate)
         → JWT tokens returned (accessToken, idToken, refreshToken, expiresAt)
         → Frontend stores tokens in localStorage
         → Frontend includes Authorization: Bearer <token> in all API calls
```

#### Why form-based login instead of Cognito browser OAuth?

Email and password sign-in uses **`POST /auth/login`**, which authenticates against the Cognito User Pool
via server-side APIs (for example `InitiateAuth` with username and password), not the **OAuth2
authorization-code** flow where the browser is redirected to Cognito’s authorize endpoint and returns with
a `code`. Cognito still issues OIDC-shaped JWTs; the difference is how the user proves their identity.

This approach was chosen because:

- **Product UX**: The authorization-code path is often paired with **Cognito’s hosted sign-in** (or a flow
  that still feels like leaving the product for Cognito). A first-party login screen keeps branding, copy,
  and layout under full application control.
- **One contract for clients**: JSON request in, tokens in the response. The same pattern suits the web
  UI, future mobile apps, and other API consumers without each one implementing redirect URLs, state, and
  callback handling.
- **Stateless JWT model**: The platform stores tokens client-side and sends `Authorization: Bearer`.
  Direct login returns tokens in the API response. Browser OIDC “web-app” flows typically add redirects,
  callback URLs, and framework session or cookie behavior unless deliberately minimized.
- **Social login stays separate**: Providers such as LinkedIn require an OAuth redirect and their own
  callback; that path remains explicit in the LinkedIn flow below.

### OAuth2/OIDC Flow (LinkedIn)

LinkedIn sign-in uses OAuth2 redirects and a server-side callback (see below). It is separate from Cognito
email and password, which use form-based login only.

#### LinkedIn OAuth2 Flow

1. User clicks "Continue with LinkedIn" button
2. Frontend redirects to `GET /auth/linkedin/login` (auth-service)
3. `LinkedInLoginRedirectResource` constructs OAuth2 authorization URL and redirects to LinkedIn
4. User authenticates with LinkedIn
5. LinkedIn redirects back to `/auth/linkedin/login/callback` with authorization code
6. `LinkedInLoginCallbackResource` manually exchanges code for access token (LinkedIn doesn't support Quarkus OIDC's
   default flow)
7. Callback resource calls LinkedIn user info endpoint
8. User info mapped to `AuthUser` domain model
9. Temporary token generated via `TokenStore`, user redirected to UI with token
10. UI module exchanges temporary token for JWT tokens via `POST /auth/tokens/exchange`
11. Frontend stores JWT tokens in localStorage

**Security Note**: Temporary tokens are **single-use** and automatically invalidated after exchange.
See [Temporary Token Security](#temporary-token-security) section below.

### Registration

```
Frontend → POST /auth/register (backend-actor)
         → POST /auth/register (auth-service)
         → Cognito User Pool (create user)
         → POST /actors/register (actor-service, directly from auth-service)
         → PostgreSQL (save user profile)
         → JWT tokens returned to frontend
```

## Request Flow

### User-Initiated Requests

All frontend requests route through `backend-actor` (port 8500), which proxies to appropriate services:

- `POST /auth/login` → `backend-actor` → `auth-service`
- `POST /auth/register` → `backend-actor` → `auth-service`
- `POST /auth/refresh-user-token` → `backend-actor` → `auth-service`
- `GET /auth/linkedin/login` → `backend-actor` → `auth-service` (redirect)
- `GET /actors/{id}` → `backend-actor` → `actor-service`
- `POST /resumes` → `backend-actor` → `document-service`

### Service-to-Service Requests

Services can make calls to other services using service JWTs:

- `auth-service` → `actor-service` (with service JWT)
- `actor-service` → `x-service` (with service JWT)
- Background jobs / scheduled tasks (with service JWT, no user context)

## Security Model

### Temporary Token Security

OAuth2/OIDC flows use temporary tokens as an intermediate step between OAuth callback and JWT token
generation.
These tokens provide an additional security layer:

**How It Works**:

1. After OAuth callback, `TokenStore.generateToken()` creates a cryptographically secure random token (32-byte UUID)
2. Token is stored in cache with `AuthIdentity` (TTL: 5 minutes)
3. User is redirected to frontend callback page with token in URL query parameter
4. Frontend calls `POST /auth/tokens/exchange` with token in request body (not URL)
5. `TokenStore.exchangeToken()` retrieves `AuthIdentity` from cache
6. **Token is immediately invalidated** (removed from cache) - single-use only
7. Cognito JWT tokens are generated and returned to frontend

**Security Benefits**:

- ✅ **Single-Use**: Tokens cannot be replayed after exchange
- ✅ **Short-Lived**: Tokens expire after 5 minutes (cache TTL)
- ✅ **Secure Random**: Tokens are cryptographically secure (32 bytes of entropy)
- ✅ **Not in URL**: Token exchange uses POST with token in request body (prevents logging/exposure)
- ✅ **Immediate Invalidation**: Token removed from cache on successful exchange
- ✅ **Prevents Token Replay**: Even if token is intercepted, it can only be used once

**Cache Implementation**:

- Uses Quarkus Cache API with `ServiceTokenCacheKeyGenerator`
- Cache key format: `service-token:${token}`
- Cache operations: `put` (store), `get` (retrieve), `invalidate` (remove)
- All operations logged at DEBUG level for audit trail

**Why Not Direct JWT Generation?**:

- OAuth callbacks happen server-side (auth-service), but JWTs need to be delivered to frontend
- Temporary tokens allow secure handoff between server-side OAuth flow and client-side token storage
- Prevents exposing long-lived JWTs in redirect URLs
- Enables proper error handling and validation before issuing JWTs

### Backend REST Endpoints

All REST endpoints use JWT-based authentication supporting both user and service tokens:

1. **TokenAuthenticationFilter** (automatic):
    - Intercepts all JAX-RS requests
    - Checks for `Authorization: Bearer <token>` header
    - Validates JWT token using `TokenValidator`
    - Detects service tokens (via `custom:service_id` claim) or user tokens
    - Sets authenticated `User` or `authenticatedServiceId` in request context if valid

2. **AuthenticatedInterceptor** (automatic):
    - Intercepts methods annotated with `@Secured`
    - Checks request context for authenticated user
    - Throws `AuthenticationException` if no user found
    - Returns 401 Unauthorized response

3. **ServiceTokenAuthorizationInterceptor** (automatic):
    - Intercepts methods annotated with `@AllowedServices`
    - Checks request context for authenticated service ID
    - Verifies service ID is in the allowed list
    - Throws `AuthenticationException` if service is not authorized
    - Returns 403 Forbidden response

### Service authentication (overview)

Service accounts, service JWTs, client filters, and `@AllowedServices` are covered in
[SERVICE_AUTHENTICATION.md](SERVICE_AUTHENTICATION.md) (this document focuses on user-facing and
BFF auth flows).

### Frontend UI Modules

Frontend applications handle authentication client-side:

1. **Login**: Call `/auth/login` endpoint to get JWT tokens
2. **Store Tokens**: Save `accessToken`, `idToken`, `refreshToken`, and `expiresAt` in localStorage
3. **API Calls**: Include `Authorization: Bearer <token>` header in all API requests
4. **Token Refresh**: Automatically refresh tokens when expired
5. **Page Protection**: Check for valid token on page load, redirect to login if missing

## Components

### Core Security Library (`libs/security`)

- **`@Secured`**: Annotation to mark JAX-RS methods/classes requiring authentication
- **`@AllowedServices`**: Annotation to restrict endpoints to specific services
- **`TokenAuthenticationFilter`**: JAX-RS filter for token validation (supports both user and service tokens)
- **`UserTokenAuthorizationInterceptor`**: CDI interceptor that enforces `@Secured`
- **`ServiceTokenAuthorizationInterceptor`**: CDI interceptor that enforces `@AllowedServices`
- **`UserTokenClientRequestFilter`**: Client filter that forwards user tokens to downstream services
- **`ServiceTokenClientRequestFilter`**: Client filter that adds service tokens when no user token is present
- **`TokenValidator`**: Interface for JWT token validation (validates both user and service tokens)
- **`ServiceAuthenticationProvider`**: Interface for service authentication
- **`ServiceTokenProvider`**: Interface for obtaining and caching service JWT tokens
- **`CognitoTokenValidator`**: Cognito implementation of TokenValidator
- **`CachingServiceTokenProvider`**: Cognito implementation of ServiceTokenProvider with caching and automatic refresh

### Auth Service (`services/auth-service`)

- **`AuthResource`**: Form-based login and registration endpoints
- **`TokenExchangeResource`**: Exchanges temporary tokens for user info (`POST /auth/tokens/exchange`)
- **`LinkedInLoginRedirectResource`**: Initiates LinkedIn OAuth2 flow
- **`LinkedInLoginCallbackResource`**: Handles LinkedIn OAuth2 login callback, generates temporary token
- **`TokenStore`**: Generates temporary tokens for OIDC flows
- **`CognitoServiceAuthenticationProvider`**: Authenticates services with Cognito using service account credentials

### BFF (`applications/backend-actor`)

- **`AuthController`**: Proxies authentication requests to auth-service
- **`ActorController`**: Actor profile endpoints
- **`DocumentController`**: Document endpoints
- **`LinkedInController`**: LinkedIn-related BFF routes

## Configuration

### Environment Variables

| Variable                                    | Description                                                                                              | Default     |
|---------------------------------------------|----------------------------------------------------------------------------------------------------------|-------------|
| `COGNITO_ACTOR_POOL_ID`                     | AWS Cognito actor pool ID (for job seekers)                                                              | -           |
| `COGNITO_ACTOR_CLIENT_ID`                   | AWS Cognito actor client ID                                                                              | -           |
| `COGNITO_ACTOR_CLIENT_SECRET`               | AWS Cognito actor client secret                                                                          | -           |
| `COGNITO_SERVICE_POOL_ID`                   | AWS Cognito service pool ID (for service accounts)                                                       | -           |
| `COGNITO_SERVICE_CLIENT_ID`                 | AWS Cognito service client ID                                                                            | -           |
| `COGNITO_SERVICE_CLIENT_SECRET`             | AWS Cognito service client secret                                                                        | -           |
| `COGNITO_SERVICE_ACCOUNT_USERNAME`          | Service account username (e.g., `service-document-service`)                                              | -           |
| `COGNITO_SERVICE_ACCOUNT_PASSWORD`          | Service account password                                                                                 | -           |
| `AWS_REGION`                                | AWS region                                                                                               | `us-west-2` |
| `LINKEDIN_OAUTH2_CLIENT_ID`                 | LinkedIn OAuth2 client ID (from LinkedIn developer app)                                                  | -           |
| `LINKEDIN_OAUTH2_CLIENT_SECRET`             | LinkedIn OAuth2 client secret (from LinkedIn developer app)                                              | -           |
| `FORGE_OAUTH2_REFRESH_TOKEN_ENCRYPTION_KEY` | Base64 AES-256 key for encrypting stored OAuth2 refresh tokens (app-generated; not from OAuth providers) | -           |

### OIDC Configuration

The system uses Quarkus OIDC multi-tenant configuration for OAuth2 flows.

#### AWS Cognito (default tenant)

Authoritative copy: [`config/src/main/resources/oidc.properties`](https://github.com/get-forge/forge-platform/blob/main/config/src/main/resources/oidc.properties).
In short: `application-type=service` - no Quarkus OIDC authorization-code redirect; human login
is `POST /auth/login` (Cognito `InitiateAuth`); API JWT validation uses the security stack
(`CompositeTokenValidator` / `TokenAuthenticationFilter`), not a browser redirect to Cognito.

#### LinkedIn (named tenant)

`quarkus.oidc.linkedin.tenant-enabled=false`. LinkedIn OAuth2 uses custom JAX-RS resources under
`services/auth-service/.../oidc/linkedin/`, not the Quarkus OIDC redirect flow. Remaining
`quarkus.oidc.linkedin.*` properties are read via `@ConfigProperty` where needed.

### Multi-Tenant Resolution

The default tenant points at the Cognito issuer with `application-type=service` (no Quarkus OIDC
web-app redirect flow). LinkedIn tenant is disabled (`quarkus.oidc.linkedin.tenant-enabled=false`)
because LinkedIn OAuth2 is handled manually via custom callback resources, not using Quarkus OIDC's
automatic flow.

## Endpoints

### Public Endpoints (No Authentication Required)

- `POST /auth/login` - Form-based login (returns JWT tokens)
- `POST /auth/register` - Registration (returns JWT tokens)
- `POST /auth/tokens/exchange` - Exchange temporary token for user info (used in OIDC flows)
- `POST /auth/tokens/refresh` - Refresh access token using refresh token
- `GET /auth/linkedin/login` - Initiates LinkedIn OAuth2 flow (redirects to LinkedIn)

### Protected Endpoints (Require `@Secured`)

All other endpoints require `@Secured` annotation and valid JWT token in `Authorization: Bearer <token>` header.

## Zero-Trust Architecture

The authentication system implements a zero-trust security model where:

✅ **Every service call is authenticated** - Services must have valid JWTs (user or service tokens)
✅ **Service identity verification** - Receiving services know which service is calling via `custom:service_id` claim
✅ **Service-level authorization** - Fine-grained control with `@AllowedServices` annotation
✅ **No trusted network assumptions** - Services verify each other's identity regardless of network location
✅ **Credential isolation** - Service credentials are separate from user credentials
✅ **Automatic token management** - Service tokens are cached and refreshed automatically

### Zero-Trust Principles Implemented

1. **Verify Explicitly**: Every request is authenticated and authorized
2. **Use Least Privilege**: Services can only access endpoints they're authorized for
3. **Assume Breach**: Service credentials can be revoked independently of user credentials

### What's In Place

- ✅ Service-to-service authentication with Cognito service accounts
- ✅ Service-level authorization with `@AllowedServices`
- ✅ Automatic service token injection into outgoing requests
- ✅ Token caching and automatic refresh
- ✅ User and service token support in the same infrastructure

### Potential Future Enhancements

- Mutual TLS (mTLS) for additional transport security
- Service mesh integration (Istio, Linkerd)
- Certificate-based service authentication
- Network policy enforcement
- Service-to-service encryption at rest

## Benefits

1. **True Stateless**: No server-side session state
2. **Horizontal Scaling**: No sticky sessions or shared session stores required
3. **Simplified Architecture**: Single authentication mechanism
4. **Better Performance**: No session lookups
5. **Mobile-Friendly**: JWT tokens work well for mobile apps
6. **Consistent Security**: Same `@Secured` annotation everywhere
7. **Zero-Trust Ready**: Service-to-service authentication with service-level authorization
8. **Background Jobs**: Services can make calls without user context
9. **Service Isolation**: Service credentials are separate from user credentials

## Implementation Guide

For detailed implementation instructions, see:

- **Backend
  **: [libs/security/README.md](https://github.com/get-forge/forge-platform/blob/main/libs/security/README.md#rest-endpoint-security)
- **Frontend
  **: [libs/security/README.md](https://github.com/get-forge/forge-platform/blob/main/libs/security/README.md#frontend-ui-module-security)

## References

- [ADR-0011: Stateless JWT Authentication](../decisions/0011-stateless-jwt-authentication.md)
- [ADR-0003: Authentication and User Management Approach](../decisions/0003-authentication-and-user-management-approach.md)
- [ADR-0004: Use AWS Cognito Across All Environments](../decisions/0004-use-aws-cognito-across-all-environments.md)
- [Security Library README](https://github.com/get-forge/forge-platform/blob/main/libs/security/README.md)
