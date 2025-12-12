# Authentication Architecture

## Overview

The Bravo application uses **fully stateless JWT-based authentication** across all modules. All authentication flows return JWT tokens that are stored client-side and included in API requests via the `Authorization` header.

See [ADR-0011: Stateless JWT Authentication](../decisions/0011-stateless-jwt-authentication.md) for the architectural decision.

## Architecture Principles

1. **Stateless**: No server-side session storage
2. **Unified Model**: Single `@Secured` annotation for all authentication
3. **Client-Side Storage**: JWT tokens stored in localStorage (web) or secure storage (mobile)
4. **Automatic Validation**: JAX-RS filter validates tokens on every request
5. **Horizontal Scaling**: No session affinity required

## Authentication Flows

### Form-Based Login (Primary)

```
Frontend → POST /auth/login (backend-candidate)
         → POST /auth/login (auth-service)
         → Cognito User Pool (authenticate)
         → JWT tokens returned (accessToken, idToken, refreshToken, expiresAt)
         → Frontend stores tokens in localStorage
         → Frontend includes Authorization: Bearer <token> in all API calls
```

### OAuth2/OIDC Flows (Cognito & LinkedIn)

#### Cognito OAuth2 Flow

1. User clicks "Continue with Cognito" button
2. Frontend redirects to `GET /auth/cognito/login` (auth-service)
3. `CognitoOidcLoginResource` constructs OAuth2 authorization URL and redirects to Cognito
4. User authenticates with Cognito
5. Cognito redirects back to `/auth/cognito/callback` with authorization code
6. Quarkus OIDC processes callback, exchanges code for tokens, creates `SecurityIdentity`
7. `CognitoSecurityIdentityAugmentor` maps Cognito claims to `AuthUser` domain model
8. Quarkus OIDC redirects to `/auth/cognito/success`
9. `CognitoOidcCallbackResource` extracts `AuthUser`, generates temporary token via `TokenStore`, redirects to UI with token
10. UI module exchanges temporary token for JWT tokens via `POST /auth/tokens/exchange`
11. Frontend stores JWT tokens in localStorage

#### LinkedIn OAuth2 Flow

1. User clicks "Continue with LinkedIn" button
2. Frontend redirects to `GET /auth/linkedin/login` (auth-service)
3. `LinkedInOidcLoginResource` constructs OAuth2 authorization URL and redirects to LinkedIn
4. User authenticates with LinkedIn
5. LinkedIn redirects back to `/auth/linkedin/callback` with authorization code
6. `LinkedInOidcCallbackResource` manually exchanges code for access token (LinkedIn doesn't support Quarkus OIDC's default flow)
7. Callback resource calls LinkedIn user info endpoint
8. User info mapped to `AuthUser` domain model
9. Temporary token generated via `TokenStore`, user redirected to UI with token
10. UI module exchanges temporary token for JWT tokens via `POST /auth/tokens/exchange`
11. Frontend stores JWT tokens in localStorage

### Registration

```
Frontend → POST /auth/register (backend-candidate)
         → POST /auth/register (auth-service)
         → Cognito User Pool (create user)
         → POST /candidates/register (candidate-service, directly from auth-service)
         → PostgreSQL (save user profile)
         → JWT tokens returned to frontend
```

## Request Flow

All frontend requests route through `backend-candidate` (port 8500), which proxies to appropriate services:

- `POST /auth/login` → `backend-candidate` → `auth-service`
- `POST /auth/register` → `backend-candidate` → `auth-service`
- `POST /auth/refresh-user-token` → `backend-candidate` → `auth-service`
- `GET /auth/linkedin/login` → `backend-candidate` → `auth-service` (redirect)
- `GET /candidates/{id}` → `backend-candidate` → `candidate-service`
- `POST /resumes` → `backend-candidate` → `document-service`

## Security Model

### Backend REST Endpoints

All REST endpoints use JWT-based authentication:

1. **JwtAuthenticationFilter** (automatic):
   - Intercepts all JAX-RS requests
   - Checks for `Authorization: Bearer <token>` header
   - Validates JWT token using `JwtAuthenticationProvider`
   - Sets authenticated `User` in request context if valid

2. **AuthenticatedInterceptor** (automatic):
   - Intercepts methods annotated with `@Secured`
   - Checks request context for authenticated user
   - Throws `AuthenticationException` if no user found
   - Returns 401 Unauthorized response

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
- **`JwtAuthenticationFilter`**: JAX-RS filter for JWT token validation
- **`AuthenticatedInterceptor`**: CDI interceptor that enforces `@Secured`
- **`JwtAuthenticationProvider`**: Interface for JWT operations
- **`UserPoolAuthenticationProvider`**: Interface for user pool operations

### Auth Service (`services/auth-service`)

- **`AuthResource`**: Form-based login and registration endpoints
- **`TokenExchangeResource`**: Exchanges temporary tokens for user info (`POST /auth/tokens/exchange`)
- **`CognitoOidcLoginResource`**: Initiates Cognito OAuth2 flow
- **`CognitoOidcCallbackResource`**: Handles Cognito OAuth2 callback, generates temporary token
- **`LinkedInOidcLoginResource`**: Initiates LinkedIn OAuth2 flow
- **`LinkedInOidcCallbackResource`**: Handles LinkedIn OAuth2 callback, generates temporary token
- **`CognitoSecurityIdentityAugmentor`**: Maps OIDC claims to `AuthUser` domain model
- **`OidcTenantResolver`**: Resolves OIDC tenant (Cognito vs LinkedIn)
- **`TokenStore`**: Generates temporary tokens for OIDC flows

### Backend Candidate (`application/backend-candidate`)

- **`AuthController`**: Proxies authentication requests to auth-service
- **`CandidateController`**: Protected candidate endpoints
- **`ResumeController`**: Protected resume upload endpoints

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `COGNITO_USER_POOL_ID` | AWS Cognito user pool ID | - |
| `COGNITO_CLIENT_ID` | AWS Cognito client ID | - |
| `COGNITO_CLIENT_SECRET` | AWS Cognito client secret | - |
| `AWS_REGION` | AWS region | `us-west-2` |
| `OAUTH2_LINKEDIN_CLIENT_ID` | LinkedIn OAuth2 client ID | - |
| `OAUTH2_LINKEDIN_SECRET` | LinkedIn OAuth2 secret | - |

### OIDC Configuration

The system uses Quarkus OIDC multi-tenant configuration for OAuth2 flows.

#### AWS Cognito (Default Tenant)

```properties
quarkus.oidc.tenant-enabled=true
quarkus.oidc.auth-server-url=https://cognito-idp.${aws.region}.amazonaws.com/${cognito.user-pool.user-pool-id}
quarkus.oidc.client-id=${cognito.user-pool.client-id}
quarkus.oidc.credentials.secret=${cognito.user-pool.client-secret}
quarkus.oidc.application-type=web-app
quarkus.oidc.authentication.scopes=openid,profile,email
quarkus.oidc.authentication.redirect-path=/auth/cognito/success
```

#### LinkedIn (Named Tenant)

LinkedIn tenant is disabled (`tenant-enabled=false`) because LinkedIn OAuth2 is handled manually via custom resources, not using Quarkus OIDC's automatic flow.

```properties
quarkus.oidc.linkedin.tenant-enabled=false
quarkus.oidc.linkedin.application-type=service
quarkus.oidc.linkedin.discovery-enabled=false
quarkus.oidc.linkedin.auth-server-url=https://www.linkedin.com
quarkus.oidc.linkedin.client-id=${OAUTH2_LINKEDIN_CLIENT_ID:}
quarkus.oidc.linkedin.credentials.secret=${OAUTH2_LINKEDIN_SECRET:}
```

### Multi-Tenant Resolution

The `OidcTenantResolver` determines which OIDC tenant configuration to use:
- Paths starting with `/auth/linkedin` → LinkedIn tenant
- State parameter starting with `linkedin:` → LinkedIn tenant
- All other paths → Default tenant (Cognito)

## Endpoints

### Public Endpoints (No Authentication Required)

- `POST /auth/login` - Form-based login (returns JWT tokens)
- `POST /auth/register` - Registration (returns JWT tokens)
- `POST /auth/tokens/exchange` - Exchange temporary token for user info (used in OIDC flows)
- `POST /auth/tokens/refresh` - Refresh access token using refresh token
- `GET /auth/cognito/login` - Initiates Cognito OAuth2 flow (redirects to Cognito)
- `GET /auth/linkedin/login` - Initiates LinkedIn OAuth2 flow (redirects to LinkedIn)

### Protected Endpoints (Require `@Secured`)

All other endpoints require `@Secured` annotation and valid JWT token in `Authorization: Bearer <token>` header.

## Benefits

1. **True Stateless**: No server-side session state
2. **Horizontal Scaling**: No sticky sessions or shared session stores required
3. **Simplified Architecture**: Single authentication mechanism
4. **Better Performance**: No session lookups
5. **Mobile-Friendly**: JWT tokens work well for mobile apps
6. **Consistent Security**: Same `@Secured` annotation everywhere

## Implementation Guide

For detailed implementation instructions, see:
- **Backend**: [libs/security/README.md](../../libs/security/README.md#rest-endpoint-security)
- **Frontend**: [libs/security/README.md](../../libs/security/README.md#frontend-ui-module-security)

## References

- [ADR-0011: Stateless JWT Authentication](../decisions/0011-stateless-jwt-authentication.md)
- [ADR-0003: Authentication and User Management Approach](../decisions/0003-authentication-and-user-management-approach.md)
- [ADR-0004: Use AWS Cognito Across All Environments](../decisions/0004-use-aws-cognito-across-all-environments.md)
- [Security Library README](../../libs/security/README.md)

