# LinkedIn Account Linking Strategy - Phase 1

## Overview

This document outlines the strategy for implementing LinkedIn account linking as part of the onboarding flow. This is Phase 1 of fixing the LinkedIn OAuth integration, focusing on allowing authenticated users to link their LinkedIn accounts post-registration.

**Status**: Draft for Review  
**Phase**: 1 of 3  
**Scope**: Onboarding LinkedIn linking only (login flow fixes deferred to Phase 2)

---

## Problem Statement

Currently, the LinkedIn OAuth flow is broken because:
1. LinkedIn returns a LinkedIn `sub` (unique ID) that doesn't match Cognito `sub` (source of truth)
2. No mechanism exists to link LinkedIn accounts to existing Cognito accounts
3. Users can't use LinkedIn as an authentication method because accounts aren't linked

## Solution: Account Linking Pattern

Following industry best practices (Google, GitHub, etc.), we'll implement an **account linking** pattern where:
- Users authenticate with Cognito first (primary provider)
- After authentication, users can link external OAuth providers (LinkedIn)
- The LinkedIn `sub` is stored in the database for future login lookups
- Email verification ensures the LinkedIn account belongs to the user

---

## Architecture

### Current State

```
Registration Flow:
User → Register with Email/Password → Cognito User Created → Postgres Candidate Record Created
                                                              (candidate_id = Cognito sub)
                                                              (linked_in_slug = optional, from form)
```

### Target State (Phase 1)

```
Registration Flow:
User → Register with Email/Password → Cognito User Created → Postgres Candidate Record Created
                                                              (candidate_id = Cognito sub)
                                                              (linked_in_slug = optional, from form)
                                                              (linked_in_sub = null initially)

Onboarding Flow:
User (authenticated) → Onboarding Page → "Link LinkedIn" Button → LinkedIn OAuth → 
  → Verify Email Matches → Store linked_in_sub → Continue Onboarding
```

---

## Database Changes

### Migration: Add `linked_in_sub` Column

**File**: `services/candidate-service/src/main/resources/db/migration/V2__add_linked_in_sub.sql`

```sql
-- Add LinkedIn sub (unique identifier from LinkedIn OAuth) to candidates table
ALTER TABLE candidates ADD COLUMN linked_in_sub VARCHAR(255);

-- Create index for fast lookups during LinkedIn login (Phase 2)
CREATE INDEX idx_candidates_linked_in_sub ON candidates(linked_in_sub);

-- Add comment for documentation
COMMENT ON COLUMN candidates.linked_in_sub IS 'LinkedIn OAuth sub (unique identifier) for OAuth login';
```

**Rationale**:
- `linked_in_sub` is LinkedIn's unique identifier (different from `linked_in_slug` which is the profile URL)
- Index needed for fast lookups during login flow (Phase 2)
- Nullable because linking is optional

### Entity Updates

**File**: `services/candidate-service/src/main/java/io/eagledrive/services/candidate/infrastructure/persistence/CandidateRecord.java`

Add field:
```java
@Column(name = "linked_in_sub")
private String linkedInSub;
```

---

## API Endpoints

### 1. Initiate LinkedIn Linking

**Endpoint**: `GET /auth/linkedin/link`  
**Authentication**: Required (`@Secured`)  
**Location**: `services/auth-service/src/main/java/io/eagledrive/services/auth/oidc/linkedin/LinkedInLinkResource.java`

**Purpose**: Initiates LinkedIn OAuth flow for account linking (different from login flow)

**Flow**:
1. User is authenticated (has Cognito JWT token)
2. Extract `candidate_id` (Cognito sub) from JWT token
3. Look up candidate in Postgres to get email
4. Generate state parameter: `link:{candidate_id}|{email}` (for verification)
5. Redirect to LinkedIn OAuth with state parameter

**State Parameter Format**: `link:{candidate_id}|{email}`

**Example**:
```
GET /auth/linkedin/link
Authorization: Bearer <cognito-jwt-token>

→ Redirects to: https://www.linkedin.com/oauth/v2/authorization?...
  &state=link:123e4567-e89b-12d3-a456-426614174000|user@example.com
```

---

### 2. LinkedIn Linking Callback

**Endpoint**: `GET /auth/linkedin/link/callback`  
**Authentication**: Public (called by LinkedIn)  
**Location**: `services/auth-service/src/main/java/io/eagledrive/services/auth/oidc/linkedin/LinkedInLinkCallbackResource.java`

**Purpose**: Handles LinkedIn OAuth callback for account linking

**Flow**:
1. Receive authorization code from LinkedIn
2. Exchange code for LinkedIn access token
3. Fetch user info from LinkedIn (email, sub, name)
4. Extract state parameter: `link:{candidate_id}|{email}`
5. **Verify email matches** (security check)
6. Look up candidate by `candidate_id` from state
7. Verify candidate email matches LinkedIn email
8. Store `linked_in_sub` in candidate record
9. Redirect to onboarding page with success message

**Security Checks**:
- ✅ Email from state matches candidate email in database
- ✅ Email from LinkedIn matches candidate email in database
- ✅ User is authenticated (state contains their candidate_id)
- ✅ LinkedIn sub is not already linked to another account (optional check)

**Error Cases**:
- Email mismatch → Redirect to error page with message
- Candidate not found → 404 error
- LinkedIn OAuth error → Redirect to error page

**Success Response**:
```
Redirect to: /onboarding.html?linkedin=linked
```

---

## UI Changes

### 1. Update Onboarding Page

**File**: `ui/web-candidate/src/main/resources/META-INF/resources/onboarding.html`

**Changes**:
- Add "Link LinkedIn Account" step (Step 2 of 3)
- Show LinkedIn linking button
- Display status (linked/not linked)
- Show success message after linking

**UI Flow**:
```
Step 1: Basic Information (existing placeholder)
Step 2: Link LinkedIn Account (NEW)
  - Button: "Link LinkedIn Account"
  - Status: "Not linked" or "Linked as {name}"
  - Info: "Connect your LinkedIn account to enable LinkedIn login"
Step 3: Resume Upload (existing)
```

**JavaScript**:
- Add handler for "Link LinkedIn" button
- Call `GET /auth/linkedin/link` (with JWT token)
- Handle callback redirect with success message

---

### 2. Create Callback Handler (Optional)

**File**: `ui/web-candidate/src/main/resources/META-INF/resources/onboarding.js` (new file)

**Purpose**: Handle LinkedIn linking callback and show success message

**Flow**:
1. Check URL for `?linkedin=linked` parameter
2. Show success message
3. Update UI to show "Linked" status
4. Optionally fetch updated profile to show LinkedIn name

---

## Security Considerations

### Email Verification

**Why**: Prevents account hijacking if someone gains access to a LinkedIn account with a different email.

**Implementation**:
1. Store candidate email in OAuth state parameter
2. After LinkedIn OAuth, compare:
   - Email from state (what we sent)
   - Email from candidate record (database)
   - Email from LinkedIn user info
3. All three must match

**Edge Cases**:
- User changes email in LinkedIn after linking → Future logins may fail (Phase 2 concern)
- User has different email in LinkedIn → Linking fails with clear error message

### State Parameter Security

**Format**: `link:{candidate_id}|{email}`

**Protection**:
- State is opaque to user (base64 encoded if needed)
- State is validated server-side
- State includes email for verification
- State expires with OAuth flow (single-use)

### Unlinking

**Future Consideration**: Allow users to unlink LinkedIn accounts later (settings page). For Phase 1, we only support linking.

---

## Data Flow Diagram

```
┌─────────────┐
│   User      │
│ (Authenticated)│
└──────┬──────┘
       │
       │ 1. Click "Link LinkedIn"
       ▼
┌─────────────────────────┐
│  Onboarding Page        │
│  (onboarding.html)      │
└──────┬──────────────────┘
       │
       │ 2. GET /auth/linkedin/link
       │    Authorization: Bearer <jwt>
       ▼
┌─────────────────────────┐
│  LinkedInLinkResource   │
│  - Extract candidate_id │
│  - Get email from DB    │
│  - Generate state       │
└──────┬──────────────────┘
       │
       │ 3. Redirect to LinkedIn OAuth
       │    state=link:{candidate_id}|{email}
       ▼
┌─────────────────────────┐
│  LinkedIn OAuth         │
│  (User authenticates)   │
└──────┬──────────────────┘
       │
       │ 4. Redirect with code
       │    GET /auth/linkedin/link/callback?code=...&state=...
       ▼
┌─────────────────────────┐
│  LinkedInLinkCallback   │
│  Resource               │
│  - Exchange code        │
│  - Get LinkedIn user    │
│  - Verify email match   │
│  - Store linked_in_sub  │
└──────┬──────────────────┘
       │
       │ 5. Redirect to onboarding
       │    /onboarding.html?linkedin=linked
       ▼
┌─────────────────────────┐
│  Onboarding Page        │
│  (Shows success)        │
└─────────────────────────┘
```

---

## Implementation Checklist

### Backend (auth-service)

- [ ] Create database migration `V2__add_linked_in_sub.sql`
- [ ] Update `CandidateRecord` entity to include `linkedInSub` field
- [ ] Update `CandidateMapper` to map `linkedInSub` field
- [ ] Create `LinkedInLinkResource` class (`GET /auth/linkedin/link`)
- [ ] Create `LinkedInLinkCallbackResource` class (`GET /auth/linkedin/link/callback`)
- [ ] Implement email verification logic
- [ ] Add service method to update candidate with `linked_in_sub`
- [ ] Add error handling and logging
- [ ] Write unit tests for linking flow

### Backend (candidate-service)

- [ ] Add `updateLinkedInSub(String candidateId, String linkedInSub)` method to `CandidateService`
- [ ] Add `updateLinkedInSub(String candidateId, String linkedInSub)` method to `CandidateRepository`
- [ ] Create endpoint `PATCH /candidates/{candidateId}/linkedin` (if needed, or call from auth-service directly)
- [ ] Update `CandidateResponse` DTO to include `linkedInSub` (optional, for Phase 2)

### Frontend (UI)

- [ ] Update `onboarding.html` to add LinkedIn linking step
- [ ] Add "Link LinkedIn Account" button
- [ ] Add status display (linked/not linked)
- [ ] Create `onboarding.js` to handle linking flow
- [ ] Handle callback redirect with success message
- [ ] Add loading states and error handling
- [ ] Update styling for new step

### Testing

- [ ] Test linking flow end-to-end
- [ ] Test email mismatch scenario
- [ ] Test duplicate linking (already linked)
- [ ] Test with unauthenticated user (should fail)
- [ ] Test database migration
- [ ] Test error cases

---

## Out of Scope (Phase 1)

The following are explicitly **NOT** part of Phase 1:

- ❌ Fixing LinkedIn login flow (Phase 2)
- ❌ Creating `/auth/callback` page for login (Phase 2)
- ❌ Updating `TokenExchangeResource` to return JWT tokens (Phase 2)
- ❌ Unlinking LinkedIn accounts (future enhancement)
- ❌ LinkedIn profile data sync (future enhancement)
- ❌ Multiple OAuth provider linking (future enhancement)

---

## Success Criteria

Phase 1 is complete when:

1. ✅ Users can link LinkedIn accounts during onboarding
2. ✅ Email verification prevents account hijacking
3. ✅ `linked_in_sub` is stored in database
4. ✅ UI shows linking status
5. ✅ Error cases are handled gracefully
6. ✅ Database migration is applied
7. ✅ All tests pass

---

## Next Steps (Phase 2 Preview)

After Phase 1, Phase 2 will:
1. Fix LinkedIn login flow to lookup by `linked_in_sub`
2. Generate Cognito JWT tokens for linked accounts
3. Create `/auth/callback` page in UI
4. Update `TokenExchangeResource` to return JWT tokens

---

## Questions for Review

1. **Email Verification**: Should we allow linking if emails don't match but show a warning? (Currently: hard fail)   
   Yes, with a UX warning.
2. **Duplicate Linking**: Should we prevent linking if `linked_in_sub` already exists for another account? (Currently: not checked)  
   I'm not sure this question makes sense? The implementation is immediddately after registration so there's no way for  `linked_in_sub` to be pre-populated. 
4. **Onboarding Step Order**: Should LinkedIn linking be Step 2, or can users skip it? (Currently: Step 2, can skip)  
   Yeah Step 2 and can skip if not desired
4. **Error Messages**: What should users see if email doesn't match? (Currently: generic error)  
   “The email on your LinkedIn account (jane@company.com) doesn’t match the email on your account here (jane@gmail.com). This is common if you use a work email on LinkedIn.”
6. **State Parameter**: Should we encode/encrypt the state parameter? (Currently: plain text with delimiter)  
   Yes, I think we should encrypt or at least encoded - go with industry standard / best practice again.

---

## References

- [AWS Cognito Account Linking Best Practices](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-identity-federation.html)
- [OAuth 2.0 Security Best Practices](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics)
- Current LinkedIn OAuth implementation: `services/auth-service/src/main/java/io/eagledrive/services/auth/oidc/linkedin/`

---

**Document Version**: 1.0  
**Last Updated**: 2025-01-01  
**Author**: AI Assistant  
**Reviewer**: Pending

