# Codebase Sanitization Report

This document identifies all domain-specific (recruitment platform) references that need to be sanitized before extracting components to the public repository.

## Status Summary

✅ **Already Completed:**
- Deleted `match-service` (domain-specific)
- Renamed `candidate-service` → `actor-service`

⚠️ **In Progress:**
- `document-service` - Contains resume/job spec parsing (identified for refactoring)

## Critical Domain-Specific Components to Sanitize

### 1. Services Directory

#### `document-service` ⚠️ **HIGH PRIORITY**
**Status:** Identified for refactoring

**Domain-Specific Components:**
- `ResumeResource.java` - `/resumes` endpoint
- `JobSpecResource.java` - `/job-specs` endpoint  
- `ParsedResumeRepository.java` - Resume persistence
- `ParsedJobSpecRepository.java` - Job spec persistence
- `ResumeRecord.java` - Resume entity
- `JobSpecRecord.java` - Job spec entity
- `ResumeResponseMapper.java` - Resume mapping
- `JobSpecMapper.java` - Job spec mapping
- `TextkernelResumeMapper.java` - Resume parsing mapper
- `ParseResumeResponseWrapper.java` - Resume DTO wrapper
- `ParseJobResponseWrapper.java` - Job spec DTO wrapper

**Recommendation:** 
- Remove resume/job spec specific endpoints and repositories
- Keep generic document storage/retrieval functionality
- Consider renaming to `storage-service` or `file-service` (but note: you mentioned this is too generic and contrary to microservices architecture)

#### `auth-service` ⚠️ **MEDIUM PRIORITY**
**Domain-Specific Components:**
- `LinkedInLoginRedirectResource.java` - LinkedIn OAuth integration
- `LinkedInLoginCallbackResource.java` - LinkedIn OAuth callback
- `LinkedInRegistrationRedirectResource.java` - LinkedIn registration
- `LinkedInRegistrationCallbackResource.java` - LinkedIn registration callback
- `LinkedInUserMapper.java` - LinkedIn user mapping
- `LinkedInEmailVerificationUtils.java` (in `libs/common`) - LinkedIn-specific email verification
- Entire `oidc/linkedin/` package - LinkedIn OAuth implementation

**Recommendation:**
- LinkedIn OAuth is recruitment-platform specific
- Consider extracting to a separate module or removing if not needed for commercial product
- If keeping OAuth, make it provider-agnostic (generic OAuth provider abstraction)

### 2. Libraries Directory

#### `textkernel-api` ❌ **ENTIRELY DOMAIN-SPECIFIC**
**Status:** Should be removed or extracted to domain-specific module

**Domain-Specific Components:**
- Entire library is for resume/job spec parsing via TextKernel API
- `TextkernelClient.java` - Resume/job parsing interface
- `TextkernelTxClient.java` - TextKernel implementation
- `DummyClient.java` - Mock resume/job parsing
- `parse-dummy-resume-response.json` - Sample resume data
- `parse-dummy-job-spec-response.json` - Sample job spec data

**Recommendation:**
- **Remove entirely** - This is a third-party resume parsing service integration
- If document parsing is needed, create a generic document parser abstraction

#### `domain-dtos` ⚠️ **PARTIALLY DOMAIN-SPECIFIC**
**Domain-Specific DTOs:**
- `ResumeResponse.java` - Resume response DTO
- `JobSpecResponse.java` - Job spec response DTO
- `Resume.java` (in `textkernel` package) - Resume entity
- `JobSpec.java` (in `textkernel` package) - Job spec entity

**Generic DTOs (Keep):**
- `ActorResponse.java` - Generic actor/user response
- `RegisterRequest.java` - Generic registration
- `ErrorResponse.java` - Generic error response
- Auth DTOs (generic)

**Recommendation:**
- Remove resume/job spec DTOs
- Keep generic DTOs

#### `domain-clients` ⚠️ **PARTIALLY DOMAIN-SPECIFIC**
**Domain-Specific Clients:**
- `DocumentServiceClient.java` - Has `getResume()` method
- `ParseServiceClient.java` - Has `createResume()` and `createJobSpec()` methods

**Generic Clients (Keep):**
- `ActorServiceClient.java` - Generic actor service client
- `AuthServiceClient.java` - Generic auth service client

**Recommendation:**
- Remove resume/job spec methods from document clients
- Keep generic document retrieval methods (if any)
- Or remove document clients entirely if document-service is being refactored

#### `common` ⚠️ **PARTIALLY DOMAIN-SPECIFIC**
**Domain-Specific Utilities:**
- `AugmentMatchesToggle.java` - Match augmentation toggle (recruitment-specific)
- `LinkedInEmailVerificationUtils.java` - LinkedIn-specific email verification

**Generic Utilities (Keep):**
- `JsonNodeUtils.java` - Generic JSON utilities
- `ValidationUtils.java` - Generic validation
- `Base64Utils.java` - Generic base64 encoding
- `ClassUtils.java` - Generic class utilities
- `LogMethodEntry*.java` - Generic logging utilities

**Recommendation:**
- Remove `AugmentMatchesToggle` and `LinkedInEmailVerificationUtils`
- Keep generic utilities

### 3. Applications Directory

#### `backend-candidate` ❌ **ENTIRELY DOMAIN-SPECIFIC**
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

#### `web-candidate` ❌ **ENTIRELY DOMAIN-SPECIFIC**
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

1. **Remove `textkernel-api` library entirely**
   - This is a third-party resume parsing integration
   - Not needed for generic platform

2. **Refactor `document-service`**
   - Remove `ResumeResource` and `JobSpecResource`
   - Remove `ParsedResumeRepository` and `ParsedJobSpecRepository`
   - Keep generic document storage functionality
   - Decide on final service name (not "storage-service" per your note)

3. **Remove domain-specific DTOs**
   - Remove `ResumeResponse`, `JobSpecResponse`, `Resume`, `JobSpec`
   - Keep generic DTOs

4. **Remove domain-specific applications**
   - Remove `backend-candidate`
   - Remove `backend-investor` (if not needed)
   - Review other backend applications

5. **Remove domain-specific UI modules**
   - Remove `web-candidate`
   - Remove `mobile-candidate`
   - Remove `web-investor` (if not needed)
   - Review other UI modules

### Phase 2: Medium Priority

6. **Extract or remove LinkedIn OAuth**
   - Move LinkedIn OAuth to separate module
   - Or remove if not needed for commercial product
   - Make OAuth provider-agnostic if keeping

7. **Clean up `common` library**
   - Remove `AugmentMatchesToggle`
   - Remove `LinkedInEmailVerificationUtils`

8. **Clean up `domain-clients`**
   - Remove resume/job spec methods from document clients
   - Or remove document clients if document-service is refactored

9. **Sanitize scripts and tests**
   - Remove domain-specific test scripts
   - Sanitize performance test scripts

### Phase 3: Documentation & Configuration

10. **Sanitize documentation**
    - Review all docs for domain references
    - Replace with generic terms
    - Follow `PUBLIC_REPO_STRATEGY.md` guidelines

11. **Sanitize configuration**
    - Review config files for domain references
    - Remove domain-specific service names

## Notes

- **`actor-service`**: Already renamed from `candidate-service`. Review for any remaining "candidate" references.
- **`document-service`**: You mentioned renaming to `storage-service` is too generic. Consider alternatives like:
  - `file-service` (if it handles file operations)
  - `blob-service` (if it handles binary/blob storage)
  - `asset-service` (if it handles asset storage)
  - Or keep as `document-service` but remove domain-specific functionality

## Questions to Consider

1. **LinkedIn OAuth**: Is this needed for the commercial product, or is it recruitment-platform specific?
2. **Document Parsing**: Do you need generic document parsing, or was resume/job spec parsing the only use case?
3. **Applications/UI**: Which applications/UI modules are needed for the commercial product vs. recruitment platform demos?
4. **TextKernel Integration**: Should this be completely removed, or extracted to a separate domain-specific module?
