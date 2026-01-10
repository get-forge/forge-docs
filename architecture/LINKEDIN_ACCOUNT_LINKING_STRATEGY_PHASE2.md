# LinkedIn Account Linking Strategy - Phase 2

## Overview

This document outlines the approach for implementing LinkedIn login flow that uses linked accounts. This is Phase 2 of fixing the LinkedIn OAuth integration, focusing on enabling users to log in using their linked LinkedIn accounts.

**Status**: Implemented  
**Phase**: 2 of 3  
**Scope**: LinkedIn login flow using linked accounts (account linking was completed in Phase 1)

---

## Problem Statement

Currently, the LinkedIn login flow is broken because:

1. `LinkedInOidcCallbackResource` creates `AuthIdentity` with LinkedIn's `sub` (which doesn't match Cognito `sub`)
2. `TokenExchangeResource` returns user info but not Cognito JWT tokens
3. No mechanism exists to lookup candidates by `linked_in_sub` during login
4. Users with linked LinkedIn accounts cannot use LinkedIn to log in

## Solution: LinkedIn Login with Account Lookup

Following the account linking pattern from Phase 1, we'll implement LinkedIn login that:

- Looks up candidate by `linked_in_sub` from LinkedIn OAuth response
- Retrieves the candidate's Cognito `sub` (candidate_id) from the database
- Generates Cognito JWT tokens using stored refresh tokens (see "Implementation Note" below)
- Returns JWT tokens via `TokenExchangeResource` (matching Cognito OAuth flow)

**Implementation Note**: The final implementation uses **refresh token storage** instead of admin-initiated auth flows. Refresh tokens are stored when users link their LinkedIn accounts (Phase 1) and used to generate new JWT tokens during LinkedIn login. See "Known Limitations & Future Improvements" section for details on refresh token expiration.

---

## Architecture

### Current State (Phase 1)

```
Linking Flow (Phase 1):
User (authenticated) → Link LinkedIn → Store linked_in_sub in database
```

```
Login Flow (Current - Broken):
User → LinkedIn OAuth → LinkedInOidcCallbackResource → 
  → AuthIdentity with LinkedIn sub → TokenStore → 
  → TokenExchangeResource → Returns user info (no JWT tokens)
```

### Target State (Phase 2)

```
Login Flow (Phase 2):
User → LinkedIn OAuth → LinkedInOidcCallbackResource → 
  → Lookup candidate by linked_in_sub → Get candidate_id (Cognito sub) → 
  → Create AuthIdentity with Cognito sub → TokenStore → 
  → TokenExchangeResource → Generate Cognito JWT tokens → 
  → Return JWT tokens to frontend
```

---

## Database Changes

### No New Migrations Required

Phase 1 already added:
- `linked_in_sub` column to `candidates` table
- Index `idx_candidates_linked_in_sub` for fast lookups

### New Repository Method

**File**: `services/candidate-service/src/main/java/io/eagledrive/services/candidate/infrastructure/persistence/CandidateRepository.java`

Add method:
```java
@Timeout(1000)
@Retry(maxRetries = 2, delay = 150, jitter = 50)
@CircuitBreaker(delay = 1000)
@CircuitBreakerName(CircuitBreakerNames.CANDIDATE_REPOSITORY_FIND_BY_LINKED_IN_SUB)
public Optional<CandidateRecord> findByLinkedInSub(final String linkedInSub)
{
    return this.entityManager.createQuery(
        "SELECT c FROM CandidateRecord c WHERE c.linkedInSub = :linkedInSub",
        CandidateRecord.class)
        .setParameter("linkedInSub", linkedInSub)
        .getResultStream()
        .findFirst();
}
```

**Rationale**:
- Fast lookup by `linked_in_sub` during login flow
- Uses existing index from Phase 1
- Follows same pattern as `findByEmailAddress`

---

## Backend Changes

### 1. Update Candidate Service

#### Add Repository Method

**File**: `services/candidate-service/src/main/java/io/eagledrive/services/candidate/infrastructure/persistence/CandidateRepository.java`

Add `findByLinkedInSub` method (see Database Changes section above).

#### Add Service Method

**File**: `services/candidate-service/src/main/java/io/eagledrive/services/candidate/domain/CandidateService.java`

Add method:
```java
@WithSpan
@ServiceMetrics(CandidateMetricsRecorder.class)
@LogMethodEntry(message = "for linkedInSub: %s")
@CacheResult(cacheName = "candidate-profiles", keyGenerator = CandidateCacheKeyGenerator.class)
public Optional<CandidateResponse> getCandidateByLinkedInSub(final String linkedInSub)
{
    final Optional<CandidateRecord> candidateRecord = candidateRepository.findByLinkedInSub(linkedInSub);
    return candidateRecord.map(record -> candidateMapper.toCandidateResponse(record));
}
```

#### Add REST Endpoint

**File**: `services/candidate-service/src/main/java/io/eagledrive/services/candidate/presentation/rest/CandidateResource.java`

Add endpoint:
```java
/**
 * Retrieves candidate by LinkedIn sub.
 * Called by auth-service during LinkedIn login flow.
 *
 * @param linkedInSub the LinkedIn sub (unique identifier from LinkedIn OAuth)
 * @return candidate response
 */
@GET
@WithSpan
@Path("/linkedin/{linkedInSub}")
@AllowedServices({"auth-service"})
public Response getCandidateByLinkedInSub(@PathParam("linkedInSub") final String linkedInSub)
{
    try
    {
        final var candidateResponse = candidateService.getCandidateByLinkedInSub(linkedInSub);

        if (candidateResponse.isEmpty())
        {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Candidate not found for LinkedIn sub: " + linkedInSub))
                .build();
        }

        return Response.ok(candidateResponse.get())
            .build();
    }
    catch (final Exception e)
    {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(Map.of("error", "Failed to retrieve candidate: " + e.getMessage()))
            .build();
    }
}
```

#### Update REST Client

**File**: `libs/domain-clients/src/main/java/io/eagledrive/client/candidate/CandidateServiceClient.java`

Add method:
```java
/**
 * Retrieves candidate by LinkedIn sub.
 * Used by auth-service during LinkedIn login flow.
 *
 * @param linkedInSub the LinkedIn sub (unique identifier from LinkedIn OAuth)
 * @return candidate response
 */
@GET
@Path("/linkedin/{linkedInSub}")
@Produces(MediaType.APPLICATION_JSON)
Response getCandidateByLinkedInSub(@PathParam("linkedInSub") final String linkedInSub);
```

---

### 2. Update LinkedIn OAuth Callback

**File**: `services/auth-service/src/main/java/io/eagledrive/services/auth/oidc/linkedin/LinkedInOidcCallbackResource.java`

**Changes**:
1. After fetching LinkedIn user info, extract `linked_in_sub` from `AuthIdentity`
2. Lookup candidate by `linked_in_sub` via `CandidateServiceClient`
3. If candidate found, create new `AuthIdentity` with Cognito `sub` (candidate_id) instead of LinkedIn `sub`
4. If candidate not found, return error (account not linked)

**Updated Flow**:
```java
@GET
@Path("/callback")
public Response callback(@QueryParam("code") final String code, @QueryParam("state") final String state)
{
    // ... existing validation ...

    try
    {
        final String redirectUri = linkBuilder.buildCallbackUri(uriInfo);
        final String accessToken = linkedinClient.exchangeCodeForAccessToken(code, redirectUri);
        final AuthIdentity linkedInIdentity = linkedinClient.fetchUser(accessToken);
        
        // NEW: Lookup candidate by linked_in_sub
        final String linkedInSub = linkedInIdentity.getSub();
        final Response candidateResponse = candidateServiceClient.getCandidateByLinkedInSub(linkedInSub);
        
        if (candidateResponse.getStatus() != Response.Status.OK.getStatusCode())
        {
            LOGGER.warnf("LinkedIn login failed: candidate not found for linked_in_sub %s", linkedInSub);
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", "LinkedIn account not linked. Please link your account first."))
                .build();
        }
        
        final CandidateResponse candidate = candidateResponse.readEntity(CandidateResponse.class);
        final String candidateId = candidate.candidateId();
        
        // Create AuthIdentity with Cognito sub (candidate_id) instead of LinkedIn sub
        final AuthIdentity cognitoIdentity = new AuthIdentity(
            candidateId,  // Use Cognito sub (candidate_id)
            linkedInIdentity.getUsername(),
            candidate.emailAddress(),
            linkedInIdentity.getRoles(),
            linkedInIdentity.getExpiresAt()
        );
        
        final String token = tokenStore.generateToken(cognitoIdentity);
        final String redirectUrl = linkBuilder.buildCallbackRedirectUrl(state, token);

        return Response.seeOther(URI.create(redirectUrl)).build();
    }
    catch (final Exception e)
    {
        // ... existing error handling ...
    }
}
```

**Dependencies**:
- Inject `CandidateServiceClient` via `@RestClient`
- Handle case where candidate is not found (account not linked)

---

### 3. Update Token Exchange Resource

**File**: `services/auth-service/src/main/java/io/eagledrive/services/auth/rest/TokenExchangeResource.java`

**Changes**:
1. After exchanging token for `AuthIdentity`, generate Cognito JWT tokens using stored refresh token
2. Return `AuthResponse` with JWT tokens (matching form-based login response)

**Updated Flow**:
```java
@POST
@Path("/exchange")
public Response exchangeToken(final Map<String, String> request)
{
    // ... existing token validation ...

    final AuthIdentity identity = authIdentity.get();
    
    // NEW: Generate Cognito JWT tokens for Cognito sub
    final AuthResponse authResponse = generateCognitoTokens(identity);
    
    if (!authResponse.success())
    {
        LOGGER.warnf("Failed to generate Cognito tokens for user: %s", identity.getUsername());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(Map.of("error", "Failed to generate authentication tokens"))
            .build();
    }
    
    LOGGER.info("Token exchanged successfully for user: " + identity.getUsername());
    
    // Return AuthResponse with JWT tokens (matching form-based login)
    return Response.ok(authResponse).build();
}

private AuthResponse generateCognitoTokens(final AuthIdentity identity)
{
    // Use stored refresh token to generate Cognito JWT tokens
    // This is safe because we've already verified identity via LinkedIn OAuth
    final String username = identity.getEmail(); // Cognito username is email
    final String candidateId = identity.getSub(); // Cognito sub (candidate_id)
    
    try
    {
        // Uses REFRESH_TOKEN_AUTH flow with stored refresh token
        // Refresh token was stored when user linked their LinkedIn account
        return cognitoTokenGenerator.generateTokensForUser(username, candidateId);
    }
    catch (final Exception e)
    {
        LOGGER.errorf(e, "Failed to generate Cognito tokens for user: %s", username);
        return AuthResponse.failure("Failed to generate authentication tokens: " + e.getMessage());
    }
}
```

**New Service**: Create `CognitoTokenGenerator` service to handle refresh token-based token generation.

---

### 4. Create Cognito Token Generator Service

**File**: `services/auth-service/src/main/java/io/eagledrive/services/auth/infrastructure/cognito/CognitoTokenGenerator.java`

**Purpose**: Generate Cognito JWT tokens using stored refresh tokens for OAuth flows.

**Implementation**:
```java
@ApplicationScoped
public class CognitoTokenGenerator
{
    @Inject
    Instance<CognitoIdentityProviderClient> cognitoClient;
    
    @Inject
    CognitoAuthRequests authRequests;
    
    @Inject
    CognitoAuthLogger authLogger;
    
    @Inject
    CandidateServiceClient candidateServiceClient;
    
    @Inject
    RefreshTokenEncryption refreshTokenEncryption;
    
    /**
     * Generates Cognito JWT tokens for a user using stored refresh token.
     * Used for OAuth flows where we've already verified identity via external provider.
     *
     * @param username the Cognito username (email)
     * @param candidateId the candidate ID (Cognito sub)
     * @return AuthResponse with JWT tokens
     */
    @WithSpan
    public AuthResponse generateTokensForUser(final String username, final String candidateId)
    {
        // Retrieve stored refresh token from database
        final String refreshToken = retrieveRefreshToken(candidateId);
        if (refreshToken == null) {
            return AuthResponse.failure("No refresh token found. Please login with password or re-register.");
        }
        
        return withClientAuthResponse(client ->
        {
            try
            {
                // Use REFRESH_TOKEN_AUTH flow to generate new tokens from stored refresh token
                final Map<String, String> authParams = new HashMap<>();
                authParams.put("REFRESH_TOKEN", refreshToken);
                
                // Add SECRET_HASH if client has secret
                if (StringUtils.isNotEmpty(authRequests.clientSecret())) {
                    authParams.put("SECRET_HASH",
                        secretHash.calculate(authRequests.clientId(), authRequests.clientSecret(), username));
                }
                
                final InitiateAuthRequest request = InitiateAuthRequest.builder()
                    .clientId(authRequests.clientId())
                    .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                    .authParameters(authParams)
                    .build();
                
                final InitiateAuthResponse response = client.initiateAuth(request);
                
                return requireComponentOr(
                    response.authenticationResult(),
                    result -> authLogger.successRefresh(username, refreshToken, result, tokenValidator),
                    () -> AuthResponse.failure("Failed to generate tokens")
                );
            }
            catch (final Exception e)
            {
                LOGGER.errorf(e, "Failed to generate Cognito tokens for user: %s", username);
                return AuthResponse.failure("Failed to generate tokens: " + e.getMessage());
            }
        });
    }
    
    private String retrieveRefreshToken(final String candidateId)
    {
        // Retrieve encrypted refresh token from candidate-service
        // Decrypt and return for use in Cognito refresh flow
    }
}
```

**Note**: This implementation uses the standard `REFRESH_TOKEN_AUTH` flow:
- Requires `ALLOW_REFRESH_TOKEN_AUTH` explicit auth flow (already configured)
- Uses stored refresh tokens from database (encrypted)
- No admin permissions required (standard user-initiated flow)
- Refresh tokens are stored when users link their LinkedIn accounts

---

## UI Changes

### 1. Create Auth Callback Page

**File**: `ui/web-candidate/src/main/resources/META-INF/resources/auth/callback.html` (new file)

**Purpose**: Handle OAuth callback redirects and exchange tokens for JWT tokens.

**Flow**:
1. Extract `token` and `redirect` from URL query parameters
2. Call `POST /auth/tokens/exchange` with token
3. Store JWT tokens in localStorage (matching form-based login)
4. Redirect to `redirect` path or default to `/dashboard`

**Implementation**:
```html
<!DOCTYPE html>
<html>
<head>
    <title>Authenticating...</title>
</head>
<body>
    <div>Authenticating...</div>
    <script src="/auth-utils.js"></script>
    <script>
        (async function() {
            const urlParams = new URLSearchParams(window.location.search);
            const token = urlParams.get('token');
            const redirect = urlParams.get('redirect') || '/dashboard';
            
            if (!token) {
                window.location.href = '/login.html?error=missing_token';
                return;
            }
            
            try {
                // Exchange temporary token for JWT tokens
                const response = await fetch('/auth/tokens/exchange', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ token: token })
                });
                
                if (!response.ok) {
                    throw new Error('Token exchange failed');
                }
                
                const authResponse = await response.json();
                
                // Store JWT tokens (matching form-based login)
                const tokenManager = new TokenManager();
                tokenManager.setTokens(authResponse, authResponse.user.email);
                
                // Redirect to target page
                window.location.href = redirect;
            } catch (error) {
                console.error('Authentication failed:', error);
                window.location.href = '/login.html?error=authentication_failed';
            }
        })();
    </script>
</body>
</html>
```

---

### 2. Update LinkedIn Login Button

**File**: `ui/web-candidate/src/main/resources/META-INF/resources/login.html` (or wherever LinkedIn login button exists)

**Changes**: Ensure LinkedIn login button redirects to `/auth/linkedin/login` (already implemented).

**No changes needed** - existing implementation should work once backend is updated.

---

## Security Considerations

### Account Linking Requirement

**Why**: Users must link their LinkedIn account before using LinkedIn login.

**Implementation**:
- `LinkedInOidcCallbackResource` checks if candidate exists for `linked_in_sub`
- If not found, returns error: "LinkedIn account not linked. Please link your account first."
- Users must complete Phase 1 (account linking) before Phase 2 (login) works

### Refresh Token Security

**Why**: Stored refresh tokens enable token generation without requiring user passwords during OAuth flows.

**Security Measures**:
- Refresh tokens are encrypted at rest in the database using AES-GCM encryption
- Tokens are only stored when users explicitly link their LinkedIn accounts (authenticated session)
- Only used after identity verification via LinkedIn OAuth
- LinkedIn OAuth provides strong identity verification
- Tokens are generated for existing Cognito users (created during registration)
- Uses standard `REFRESH_TOKEN_AUTH` flow (no special admin permissions required)
- Refresh tokens have limited lifetime (30 days default, configurable)

**Token Generation**:
1. User authenticates via LinkedIn OAuth (identity verified)
2. System looks up candidate by `linked_in_sub` (verifies account is linked)
3. System retrieves encrypted refresh token from database
4. System decrypts refresh token
5. System uses `REFRESH_TOKEN_AUTH` flow with stored refresh token
6. Cognito generates new JWT tokens for the verified user
7. Tokens returned to frontend (same format as form-based login)

**Encryption**: Refresh tokens are encrypted using AES-GCM with a key from configuration (`linkedin.refresh-token.encryption-key`).

### State Parameter Validation

**Why**: State parameter prevents CSRF attacks in OAuth flows.

**Implementation**:
- State parameter validated in `LinkedInOidcCallbackResource`
- State includes redirect path for post-authentication redirect
- State is single-use (validated once, then discarded)

---

## Data Flow Diagram

```
┌─────────────┐
│   User      │
│ (Not Authenticated)│
└──────┬──────┘
       │
       │ 1. Click "Continue with LinkedIn"
       ▼
┌─────────────────────────┐
│  Login Page              │
│  (login.html)            │
└──────┬──────────────────┘
       │
       │ 2. Redirect to /auth/linkedin/login
       ▼
┌─────────────────────────┐
│  LinkedInOidcLoginResource│
│  - Generate state        │
│  - Redirect to LinkedIn  │
└──────┬──────────────────┘
       │
       │ 3. Redirect to LinkedIn OAuth
       │    state=linkedin:uuid|redirectPath
       ▼
┌─────────────────────────┐
│  LinkedIn OAuth         │
│  (User authenticates)   │
└──────┬──────────────────┘
       │
       │ 4. Redirect with code
       │    GET /auth/linkedin/callback?code=...&state=...
       ▼
┌─────────────────────────┐
│  LinkedInOidcCallback   │
│  Resource               │
│  - Exchange code        │
│  - Get LinkedIn user    │
│  - Extract linked_in_sub│
│  - Lookup candidate     │
│  - Create AuthIdentity  │
│    with Cognito sub     │
│  - Generate token       │
└──────┬──────────────────┘
       │
       │ 5. Redirect to callback page
       │    /auth/callback?token=xxx&redirect=/dashboard
       ▼
┌─────────────────────────┐
│  Auth Callback Page     │
│  (callback.html)        │
│  - Extract token        │
│  - Exchange for JWT     │
└──────┬──────────────────┘
       │
       │ 6. POST /auth/tokens/exchange
       │    { "token": "xxx" }
       ▼
┌─────────────────────────┐
│  TokenExchangeResource  │
│  - Validate token       │
│  - Get AuthIdentity      │
│  - Generate Cognito JWT   │
│  - Return AuthResponse   │
└──────┬──────────────────┘
       │
       │ 7. Return JWT tokens
       │    { accessToken, idToken, refreshToken, ... }
       ▼
┌─────────────────────────┐
│  Auth Callback Page     │
│  - Store tokens         │
│  - Redirect to dashboard│
└──────┬──────────────────┘
       │
       │ 8. User authenticated
       ▼
┌─────────────────────────┐
│  Dashboard              │
│  (User logged in)       │
└─────────────────────────┘
```

---

## Implementation Checklist

### Infrastructure (Database)

- [ ] Run database migration to add `refresh_token_encrypted` column to `candidates` table
- [ ] Configure `linkedin.refresh-token.encryption-key` in `oidc.properties` (environment variable)
- [ ] Generate encryption key: `openssl rand -base64 32`
- [ ] Add `LINKEDIN_REFRESH_TOKEN_ENCRYPTION_KEY` to `.envrc` (via `task -t scripts/taskfile.init.yml env`)

### Backend (candidate-service)

- [ ] Add `findByLinkedInSub` method to `CandidateRepository`
- [ ] Add `getCandidateByLinkedInSub` method to `CandidateService`
- [ ] Add `GET /candidates/linkedin/{linkedInSub}` endpoint to `CandidateResource`
- [ ] Add `getCandidateByLinkedInSub` method to `CandidateServiceClient`
- [ ] Add circuit breaker name for new repository method
- [ ] Write unit tests for new repository method
- [ ] Write unit tests for new service method
- [ ] Write integration tests for new endpoint

### Backend (candidate-service)

- [ ] Add `refresh_token_encrypted` column to `CandidateRecord` entity
- [ ] Add `updateRefreshToken` and `getRefreshToken` methods to `CandidateRepository`
- [ ] Add `storeRefreshToken` and `getRefreshToken` methods to `CandidateService`
- [ ] Add `PATCH /candidates/{candidateId}/refresh-token` endpoint to `CandidateResource`
- [ ] Add `GET /candidates/{candidateId}/refresh-token` endpoint to `CandidateResource`
- [ ] Add refresh token methods to `CandidateServiceClient`
- [ ] Add circuit breaker names for new repository methods

### Backend (auth-service)

- [ ] Update `LinkedInOidcCallbackResource` to lookup candidate by `linked_in_sub`
- [ ] Update `LinkedInOidcCallbackResource` to create `AuthIdentity` with Cognito sub
- [ ] Create `RefreshTokenEncryption` service for encrypting/decrypting refresh tokens
- [ ] Create `CognitoTokenGenerator` service using refresh token flow
- [ ] Update `TokenExchangeResource` to generate Cognito JWT tokens
- [ ] Update `TokenExchangeResource` to return `AuthResponse` instead of user info
- [ ] Add `POST /auth/linkedin/link/complete` endpoint to store refresh tokens after linking
- [ ] Update `LinkedInLinkCallbackResource` to include completion endpoint
- [ ] Add error handling for unlinked accounts and missing refresh tokens
- [ ] Add logging for LinkedIn login flow
- [ ] Write unit tests for `RefreshTokenEncryption`
- [ ] Write unit tests for `CognitoTokenGenerator`
- [ ] Write unit tests for updated `LinkedInOidcCallbackResource`
- [ ] Write unit tests for updated `TokenExchangeResource`
- [ ] Write integration tests for LinkedIn login flow

### Frontend (UI)

- [ ] Create `auth/callback.html` page
- [ ] Implement token exchange logic in callback page
- [ ] Implement token storage in callback page
- [ ] Add error handling for authentication failures
- [ ] Update `onboarding.js` to call `/auth/linkedin/link/complete` after successful linking
- [ ] Test LinkedIn login flow end-to-end
- [ ] Update error messages for unlinked accounts

### Testing

- [ ] Test LinkedIn login with linked account (success case)
- [ ] Test LinkedIn login with unlinked account (error case)
- [ ] Test token exchange flow
- [ ] Test JWT token generation
- [ ] Test redirect flow after authentication
- [ ] Test error cases (invalid token, expired token, etc.)
- [ ] Test with multiple users (no cross-contamination)

### Infrastructure

- [ ] Verify Cognito User Pool client has `ALLOW_REFRESH_TOKEN_AUTH` flow enabled (already configured)
- [ ] Configure refresh token encryption key in environment
- [ ] Update documentation for new endpoints
- [ ] Update API documentation

---

## Out of Scope (Phase 2)

The following are explicitly **NOT** part of Phase 2:

- ❌ Unlinking LinkedIn accounts (future enhancement)
- ❌ LinkedIn profile data sync (future enhancement)
- ❌ Multiple OAuth provider linking (future enhancement)
- ❌ Refresh token rotation (updating stored tokens when Cognito returns new ones)

---

## Known Limitations & Future Improvements

### Refresh Token Expiration

**Current Implementation**: Refresh tokens are stored when users link their LinkedIn accounts and used for LinkedIn OAuth login flows.

**Limitations**:

1. **Token Expiration**: Cognito refresh tokens are **not valid indefinitely**. They typically expire after:
   - **30 days of inactivity** (default Cognito setting, configurable)
   - When user changes password
   - When token is explicitly revoked by admin
   - When user account is deleted or disabled

2. **No Token Rotation**: When Cognito returns a new refresh token during token refresh operations, we're not updating the stored token in the database. This means:
   - Stored tokens may become stale
   - Users may experience failures after token rotation

3. **Error Handling**: When a stored refresh token is expired or invalid, users receive a generic error message and must log in with password. No graceful fallback or clear messaging about token expiration.

**Recommended Improvements** (Future Work):

1. **Update Stored Refresh Tokens**: When `CognitoTokenGenerator.generateTokensForUser()` successfully refreshes tokens and Cognito returns a new refresh token, update the stored token in the database:
   ```java
   // After successful token refresh
   if (result.refreshToken() != null && !result.refreshToken().equals(originalRefreshToken)) {
       // Update stored refresh token
       storeRefreshToken(candidateId, result.refreshToken());
   }
   ```

2. **Improved Error Handling**: Detect specific Cognito errors for expired/revoked tokens:
   - `NotAuthorizedException` with message indicating expired token
   - Provide clear error message: "Your LinkedIn login session has expired. Please log in with your password and re-link your LinkedIn account."

3. **Fallback Strategy**: If stored refresh token fails:
   - Option 1: Prompt user to log in with password and re-link LinkedIn account
   - Option 2: Automatically attempt to re-link (if user is already authenticated via LinkedIn OAuth)

4. **Token Expiration Monitoring**: Add logging/monitoring to track when refresh tokens expire, helping identify users who need to re-link.

**Configuration**: Refresh token expiration can be configured in Cognito User Pool settings:
- `RefreshTokenValidity` (default: 30 days)
- Can be set via AWS Console or CLI when creating/updating User Pool

**References**:
- [AWS Cognito Refresh Token Expiration](https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-with-identity-providers.html)
- [Cognito Token Refresh Flow](https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-refreshing.html)

---

## Success Criteria

Phase 2 is complete when:

1. ✅ Users with linked LinkedIn accounts can log in using LinkedIn
2. ✅ LinkedIn login generates Cognito JWT tokens
3. ✅ `TokenExchangeResource` returns JWT tokens (matching form-based login)
4. ✅ Users without linked accounts receive clear error messages
5. ✅ Auth callback page handles token exchange and redirects
6. ✅ All tests pass
7. ✅ Documentation is updated

---

## Open Questions

1. **Error Messages**: What should users see if their LinkedIn account is not linked?  
   **Accepted**: "Your LinkedIn account is not linked. Please log in with email/password and link your account in settings."

2. **Token Expiration**: Should LinkedIn login tokens have the same expiration as form-based login?  
   **Accepted**: Yes, use same expiration (1 hour for access token).

3. **Client Update Strategy**: For existing Cognito clients, should we:
   - Delete and recreate (simpler, but loses client ID)
   - Update via AWS CLI (preserves client ID, but requires manual step)
   **Proposed**: Update via AWS CLI to preserve client IDs in configuration.

---

## References

- [AWS Cognito Refresh Token Auth Flow](https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-refreshing.html)
- [OAuth 2.0 Security Best Practices](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics)
- Phase 1 Documentation: `docs/architecture/LINKEDIN_ACCOUNT_LINKING_STRATEGY.md`
- Current LinkedIn OAuth implementation: `services/auth-service/src/main/java/io/eagledrive/services/auth/oidc/linkedin/`

---

**Document Version**: 1.0  
**Last Updated**: 2025-01-01  
**Author**: AI Assistant  
**Reviewer**: Pending

