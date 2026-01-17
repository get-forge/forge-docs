# Codebase Sanitization Report

This document identifies all domain-specific (recruitment platform) references that need to be sanitized before extracting components to the public repository.

## Status Summary

✅ **Already Completed:**
- Deleted `match-service` (domain-specific)
- Renamed `candidate-service` → `actor-service`
- Deleted `textkernel-api` library (domain-specific resume/job parsing integration)
- Refactored `document-service` - Migrated from resume/job spec parsing to generic document parsing using Apache Tika
- Removed domain-specific DTOs (`ResumeResponse`, `JobSpecResponse`, `Resume`, `JobSpec`)
- Cleaned up `domain-clients` - Removed `ParseServiceClient` and resume/job spec methods from `DocumentServiceClient`

## Critical Domain-Specific Components to Sanitize

### 1. Services Directory

#### `document-service` ✅ **COMPLETE - GENERIC DOCUMENT SERVICE**
**Status:** Refactored to generic document parsing service

**Completed Migration:**
- ✅ Removed `ResumeResource` and `JobSpecResource` - Replaced with generic `DocumentResource` (`/documents` endpoint)
- ✅ Removed `ParsedResumeRepository` and `ParsedJobSpecRepository` - Replaced with generic `DocumentRepository`
- ✅ Removed `ResumeRecord` and `JobSpecRecord` - Replaced with generic `DocumentRecord`
- ✅ Removed `ResumeResponseMapper` and `JobSpecMapper` - Replaced with generic `DocumentResponseMapper`
- ✅ Removed `TextkernelResumeMapper` - Replaced with `TikaDocumentMapper` using Apache Tika
- ✅ Removed `ParseResumeResponseWrapper` and `ParseJobResponseWrapper` - Replaced with generic `DocumentParseResult`
- ✅ Migrated from Textkernel API to Apache Tika for generic document parsing
- ✅ Now supports any Tika-supported document format (PDF, DOCX, TXT, etc.)

**Current Implementation:**
- Generic document upload and parsing using Apache Tika
- Stores original documents in S3 (`forge-documents` bucket)
- Stores parsed metadata and extracted text in DynamoDB (`DOCUMENTS` table)
- Returns `DocumentResponse` with metadata and extracted text
- Supports multiple documents per actor

**Recommendation:** 
- ✅ Service is now generic and ready for public repository
- No further sanitization needed for this service

#### `auth-service` ✅ **GENERIC - NO SANITIZATION NEEDED**
**Status:** OAuth integration patterns are generic infrastructure

**OAuth Components (Keep - Generic Pattern):**
- `LinkedInLoginRedirectResource.java` - OAuth redirect pattern (reusable for Google/Apple/Facebook)
- `LinkedInLoginCallbackResource.java` - OAuth callback pattern (reusable for Google/Apple/Facebook)
- `LinkedInRegistrationRedirectResource.java` - OAuth registration pattern (reusable for Google/Apple/Facebook)
- `LinkedInRegistrationCallbackResource.java` - OAuth registration callback pattern (reusable for Google/Apple/Facebook)
- `LinkedInUserMapper.java` - OAuth user mapping pattern (reusable for Google/Apple/Facebook)
- `LinkedInEmailVerificationUtils.java` (in `libs/common`) - OAuth email verification pattern (reusable for Google/Apple/Facebook)
- Entire `oidc/linkedin/` package - OAuth provider implementation pattern (reusable for Google/Apple/Facebook)

**Recommendation:**
- OAuth integration is a generic infrastructure pattern, not domain-specific
- LinkedIn implementation demonstrates the pattern that will be repeated for other providers
- Keep as-is - it's valuable reference material for OAuth integration

### 2. Libraries Directory

#### `textkernel-api` ✅ **REMOVED**
**Status:** Deleted - No longer exists in codebase

**What Was Removed:**
- Entire library for resume/job spec parsing via TextKernel API
- `TextkernelClient.java` - Resume/job parsing interface
- `TextkernelTxClient.java` - TextKernel implementation
- `DummyClient.java` - Mock resume/job parsing
- `parse-dummy-resume-response.json` - Sample resume data
- `parse-dummy-job-spec-response.json` - Sample job spec data

**Replacement:**
- Generic document parsing now handled by `document-service` using Apache Tika
- Tika supports multiple document formats and is provider-agnostic

#### `domain-dtos` ✅ **CLEANED UP - GENERIC DTOs ONLY**
**Status:** Domain-specific DTOs removed

**Removed (Domain-Specific):**
- ✅ `ResumeResponse.java` - Removed
- ✅ `JobSpecResponse.java` - Removed
- ✅ `Resume.java` (in `textkernel` package) - Removed
- ✅ `JobSpec.java` (in `textkernel` package) - Removed

**Current Generic DTOs:**
- `ActorResponse.java` - Generic actor/user response
- `RegisterRequest.java` - Generic registration
- `ErrorResponse.java` - Generic error response
- `DocumentResponse.java` - Generic document response (replaces ResumeResponse/JobSpecResponse)
- Auth DTOs (generic)

**Recommendation:**
- ✅ All domain-specific DTOs removed
- ✅ Service now uses generic `DocumentResponse` for all document operations

#### `domain-clients` ✅ **CLEANED UP - GENERIC CLIENTS ONLY**
**Status:** Domain-specific client methods removed

**Removed (Domain-Specific):**
- ✅ `ParseServiceClient.java` - Entire client removed (had `createResume()` and `createJobSpec()` methods)

**Updated (Now Generic):**
- ✅ `DocumentServiceClient.java` - Now has generic `createDocument()` and `getDocuments()` methods (replaced `getResume()`)

**Current Generic Clients:**
- `ActorServiceClient.java` - Generic actor service client
- `AuthServiceClient.java` - Generic auth service client
- `DocumentServiceClient.java` - Generic document service client

**Recommendation:**
- ✅ All domain-specific client methods removed
- ✅ Clients now use generic document operations

#### `common` ⚠️ **PARTIALLY DOMAIN-SPECIFIC**
**Domain-Specific Utilities:**
- `AugmentMatchesToggle.java` - Match augmentation toggle (recruitment-specific)

**Generic Utilities (Keep):**
- `JsonNodeUtils.java` - Generic JSON utilities
- `ValidationUtils.java` - Generic validation
- `Base64Utils.java` - Generic base64 encoding
- `ClassUtils.java` - Generic class utilities
- `LogMethodEntry*.java` - Generic logging utilities
- `LinkedInEmailVerificationUtils.java` - OAuth email verification pattern (generic, reusable for other OAuth providers)

**Recommendation:**
- Remove `AugmentMatchesToggle` (domain-specific)
- Keep `LinkedInEmailVerificationUtils` (generic OAuth pattern)
- Keep all other generic utilities

### 3. Applications Directory

#### `backend-actor` ❌ **ENTIRELY DOMAIN-SPECIFIC**
**Domain-Specific Components:**
- `ResumeController.java` - Resume upload endpoint
- `LinkedInController.java` - LinkedIn integration
- Application name itself ("candidate" is domain-specific)

**Recommendation:**
- **Remove entirely** or rename to generic backend application
- If keeping, remove domain-specific controllers

#### `backend-investor` ❌ **DOMAIN-SPECIFIC**
**Status:** Appears to be domain-specific (investor demo)

**Recommendation:**
- **Remove entirely** if not needed for commercial product

#### Other Applications (Review Needed):
- `backend-client` - May be generic (needs review)
- `backend-mobile` - May be generic (needs review)
- `backoffice-admin` - May be generic (needs review)

### 4. UI Directory

#### `web-actor` ❌ **ENTIRELY DOMAIN-SPECIFIC**
**Domain-Specific Files:**
- `resume-upload.html/js` - Resume upload UI
- `match-active.html` - Match active UI
- `match-prospect.html` - Match prospect UI
- `dashboard.html` - Likely has domain-specific content
- `onboarding.html/js` - May have domain-specific flows
- Application name itself ("candidate" is domain-specific)

**Recommendation:**
- **Remove entirely** or extract to domain-specific module

#### `mobile-candidate` ❌ **DOMAIN-SPECIFIC**
**Recommendation:**
- **Remove entirely** if not needed for commercial product

#### `web-investor` ❌ **DOMAIN-SPECIFIC**
**Recommendation:**
- **Remove entirely** if not needed for commercial product

#### Other UI Modules (Review Needed):
- `web-client` - May be generic (needs review)
- `web-admin` - May be generic (needs review)

### 5. Performance Testing (`perf/`)

**Domain-Specific Scripts:**
- `flows/fetch-candidate.js` - Candidate fetching flow
- `flows/upload-resume.js` - Resume upload flow
- `scenarios/baseline-mix.js` - Likely has domain-specific scenarios
- `data/resumes/dummy-resume.pdf` - Sample resume data

**Recommendation:**
- Remove or sanitize to generic examples
- Replace with generic document upload/user fetch examples

### 6. Scripts Directory

**Domain-Specific Scripts:**
- `test/document-service/dummy-resume-post.sh` - Resume test script
- `test/document-service/dummy-job-spec-post.sh` - Job spec test script
- `aws/cognito/test-users.sh` - May have domain-specific user setup
- Various scripts referencing "candidate", "resume", "job-spec"

**Recommendation:**
- Remove domain-specific test scripts
- Sanitize generic scripts to remove domain references

### 7. Documentation

**Domain-Specific References:**
- Multiple docs reference "candidate", "resume", "job spec", "match"
- Architecture docs may have domain-specific examples
- ADRs may reference domain concepts

**Recommendation:**
- Review all documentation in `docs/`
- Replace domain terms with generic equivalents
- See `PUBLIC_REPO_STRATEGY.md` for sanitization guidelines

### 8. Configuration Files

**Domain-Specific References:**
- `config/src/main/resources/oidc.properties` - May have LinkedIn-specific config
- Various `application.properties` files may reference domain services

**Recommendation:**
- Review and sanitize configuration files
- Remove domain-specific service references

## Recommended Action Plan

### Phase 1: High Priority (Before Public Repo Split)

1. ✅ **Remove `textkernel-api` library entirely** - **COMPLETE**
   - Third-party resume parsing integration removed
   - Replaced with generic Apache Tika document parsing

2. ✅ **Refactor `document-service`** - **COMPLETE**
   - Removed `ResumeResource` and `JobSpecResource` - Replaced with `DocumentResource`
   - Removed `ParsedResumeRepository` and `ParsedJobSpecRepository` - Replaced with `DocumentRepository`
   - Service now handles generic document storage and parsing
   - Uses Apache Tika for document parsing (supports multiple formats)

3. ✅ **Remove domain-specific DTOs** - **COMPLETE**
   - Removed `ResumeResponse`, `JobSpecResponse`, `Resume`, `JobSpec`
   - Now uses generic `DocumentResponse`

4. ✅ **Clean up `domain-clients`** - **COMPLETE**
   - Removed `ParseServiceClient` entirely
   - Updated `DocumentServiceClient` to use generic `createDocument()` and `getDocuments()` methods

5. **Remove domain-specific applications**
   - Remove `backend-actor`
   - Remove `backend-investor` (if not needed)
   - Review other backend applications

5. **Remove domain-specific UI modules**
   - Remove `web-actor`
   - Remove `mobile-candidate`
   - Remove `web-investor` (if not needed)
   - Review other UI modules

### Phase 2: Medium Priority

6. **Clean up `common` library**
   - Remove `AugmentMatchesToggle` (domain-specific)
   - Keep `LinkedInEmailVerificationUtils` (generic OAuth pattern)

7. **Sanitize scripts and tests**
   - Remove domain-specific test scripts
   - Sanitize performance test scripts

### Phase 3: Documentation & Configuration

8. **Sanitize scripts and tests**
   - Remove domain-specific test scripts (e.g., `dummy-resume-post.sh`, `dummy-job-spec-post.sh`)
   - Sanitize performance test scripts
   - Update test scripts to use generic document operations

9. **Sanitize documentation**
   - Review all docs for domain references
   - Replace with generic terms
   - Follow `PUBLIC_REPO_STRATEGY.md` guidelines
   - Note: OAuth/LinkedIn patterns are generic and should be kept

10. **Sanitize configuration**
   - Review config files for domain references
   - Remove domain-specific service names
   - Keep OAuth provider configurations (generic pattern)

## Notes

- **`actor-service`**: Already renamed from `candidate-service`. Review for any remaining "candidate" references.
- **`document-service`**: You mentioned renaming to `storage-service` is too generic. Consider alternatives like:
  - `file-service` (if it handles file operations)
  - `blob-service` (if it handles binary/blob storage)
  - `asset-service` (if it handles asset storage)
  - Or keep as `document-service` but remove domain-specific functionality

## Questions to Consider

1. **Applications/UI**: Which applications/UI modules are needed for the commercial product vs. recruitment platform demos?
2. **Test Scripts**: Should test scripts be updated to use generic document operations, or removed entirely?

## Notes on Generic Patterns

- **OAuth Integration (LinkedIn)**: OAuth provider integration is a generic infrastructure pattern. The LinkedIn implementation demonstrates the pattern that will be repeated for Google, Apple, Facebook, and other OAuth providers. This is valuable reference material and should be kept as-is.
- **`actor-service`**: Already renamed from `candidate-service`. Review for any remaining "candidate" references.
