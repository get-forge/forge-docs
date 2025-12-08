# Java Package Structure Analysis & Recommendations

## Executive Summary

This document analyzes the Java package naming conventions across all modules in the codebase and provides recommendations for standardization based on Clean Architecture principles established in `libs/security` and `services/auth-service`.

## Current State Analysis

### Services Module

#### ✅ auth-service (Partially Aligned)
```
tech.eagledrive.services.auth/
├── AuthResource.java                    # ✓ Root level
├── TokenExchangeResource.java           # ✓ Root level (moved from oidc/)
├── SecuredDevResource.java              # ✓ Root level
├── infrastructure/
│   └── TokenStore.java                  # ✓ Infrastructure
├── oidc/                                # ✓ Protocol-specific
│   ├── CognitoOidcLoginResource.java
│   ├── CognitoOidcCallbackResource.java
│   ├── LinkedInOidcLoginResource.java
│   ├── LinkedInOidcCallbackResource.java
│   ├── CognitoSecurityIdentityAugmentor.java
│   └── OidcTenantResolver.java
└── runtime/
    └── StartupBanner.java               # ✓ Consistent
```

**Status**: Good structure, follows Clean Architecture patterns.

#### ⚠️ match-service (Needs Standardization)
```
tech.eagledrive.services.match/
├── MatchResource.java                   # ⚠️ Should be in presentation/
├── domain/
│   └── dto/
│       └── MatchRequest.java            # ✓ Domain DTO
└── runtime/
    └── StartupBanner.java               # ✓ Consistent
```

**Issues**:
- REST resource at root level (should be `presentation/rest/`)
- Missing infrastructure layer for any adapters/config

#### ⚠️ parse-service (Needs Standardization)
```
tech.eagledrive.services.document/
├── ParseResource.java                   # ⚠️ Should be in presentation/
├── domain/
│   ├── SourceDocumentService.java      # ✓ Domain service
│   └── TextkernelTxParserService.java  # ✓ Domain service
├── dto/
│   └── SourceDocumentUploadResult.java # ⚠️ Should be domain/dto/
├── persistence/
│   └── SourceDocumentUploadRepository.java # ⚠️ Should be infrastructure/persistence/
└── runtime/
    └── StartupBanner.java               # ✓ Consistent
```

**Issues**:
- REST resource at root level (should be `presentation/rest/`)
- `dto/` separate from `domain/` (should be `domain/dto/`)
- `persistence/` should be `infrastructure/persistence/`

### Libraries Module

#### ✅ security (Fully Aligned)
```
tech.eagledrive.security/
├── domain/
│   ├── dto/                             # ✓
│   ├── exception/                       # ✓
│   ├── JwtAuthenticationProvider.java
│   └── UserPoolAuthenticationProvider.java
├── infrastructure/
│   ├── config/                           # ✓
│   └── vertx/                            # ✓
├── presentation/
│   └── rest/                             # ✓
│       ├── exception/                     # ✓
│       └── [REST resources & filters]
└── adapters/
    └── cognito/                          # ✓
```

**Status**: Perfect example of Clean Architecture. ✅

#### ⚠️ textkernel-api (Needs Standardization)
```
tech.eagledrive.textkernel/
├── client/                               # ⚠️ Should be adapters/client/ or infrastructure/client/
│   ├── DummyClient.java
│   ├── TextkernelClient.java
│   └── TextkernelTxClient.java
├── config/                               # ⚠️ Should be infrastructure/config/
│   └── TextkernelClientFactory.java
└── persistence/                          # ⚠️ Should be infrastructure/persistence/
    ├── JobSpecRecord.java
    ├── MatchRecord.java
    ├── ResumeRecord.java
    └── [Repositories]
```

**Issues**:
- `client/` should be `adapters/client/` or `infrastructure/client/`
- `config/` should be `infrastructure/config/`
- `persistence/` should be `infrastructure/persistence/`

#### ⚠️ aws-api (Needs Standardization)
```
tech.eagledrive.aws/
└── client/                               # ⚠️ Should be infrastructure/client/ or adapters/
    ├── dynamodb/
    │   ├── DynamoDbClientProducer.java
    │   └── DynamoDbEnhancedClientProducer.java
    └── s3/
        └── S3ClientProducer.java
```

**Issues**:
- `client/` should be `infrastructure/client/` or `adapters/aws/`
- These are infrastructure concerns (client producers)

#### ✅ auth-client (Good)
```
tech.eagledrive.client.auth/
└── AuthServiceClient.java               # ✓ REST client interface
```

**Status**: Good - client libraries can use `client/` namespace.

#### ✅ parse-client (Good)
```
tech.eagledrive.client.document/
└── ParseServiceClient.java              # ✓ REST client interface
```

**Status**: Good - client libraries can use `client/` namespace.

#### ⚠️ domain-dtos (Has Duplication)
```
tech.eagledrive.domain.dto.auth/         # ✓ Preferred location
├── LoginRequest.java
├── RegisterRequest.java
└── RefreshRequest.java

tech.eagledrive.services.auth.domain.dto/ # ⚠️ DUPLICATE - should be removed
├── LoginRequest.java
├── RegisterRequest.java
└── RefreshRequest.java
```

**Issues**:
- **CRITICAL**: Duplicate DTOs in two locations
- Should consolidate to `tech.eagledrive.domain.dto.auth/`

#### ✅ common (Good)
```
tech.eagledrive.common.toggle/
└── AugmentMatchesToggle.java            # ✓ Utility/feature toggle
```

**Status**: Good for shared utilities.

### Application Module

#### ⚠️ backend-candidate (Needs Standardization)
```
tech.eagledrive.application.backend/
├── AuthController.java                  # ⚠️ Should be presentation/rest/
├── ResumeController.java                 # ⚠️ Should be presentation/rest/
├── DemoAdminController.java              # ⚠️ Should be presentation/rest/
└── runtime/
    └── StartupBanner.java               # ✓ Consistent
```

**Issues**:
- Controllers at root level (should be `presentation/rest/`)
- Inconsistent naming: uses "Controller" vs services use "Resource"

## Recommendations

### 1. Standardize REST Resource/Controller Naming

**Current Inconsistencies**:
- Services use `*Resource` suffix
- Applications use `*Controller` suffix
- Some in root package, some in sub-packages

**Recommendation**:
- **Services**: Use `*Resource` suffix, place in `presentation/rest/` package
- **Applications**: Use `*Controller` suffix, place in `presentation/rest/` package
- **Exception**: Protocol-specific resources (OIDC) can stay in protocol packages (`oidc/`)

**Standard Pattern**:
```
tech.eagledrive.services.{service}/
└── presentation/
    └── rest/
        └── {Service}Resource.java

tech.eagledrive.application.{app}/
└── presentation/
    └── rest/
        └── {Feature}Controller.java
```

### 2. Standardize Package Structure for Services

**Recommended Structure** (based on Clean Architecture):
```
tech.eagledrive.services.{service}/
├── domain/                               # Business logic
│   ├── dto/                              # Domain DTOs
│   ├── exception/                        # Domain exceptions
│   └── [Domain services/interfaces]
├── infrastructure/                       # External concerns
│   ├── config/                           # Configuration producers
│   ├── persistence/                      # Database repositories
│   └── [Other infrastructure]
├── presentation/                         # HTTP/REST layer
│   └── rest/                             # JAX-RS resources
│       ├── exception/                     # Exception mappers
│       └── [REST resources]
├── adapters/                             # External system adapters (optional)
│   └── [Adapter implementations]
└── runtime/                              # Runtime utilities
    └── StartupBanner.java
```

### 3. Standardize Library Package Structure

**For Infrastructure Libraries** (aws-api, textkernel-api):
```
tech.eagledrive.{library}/
├── infrastructure/
│   ├── config/                           # Client producers
│   ├── client/                           # Client implementations
│   └── persistence/                      # Repository implementations
└── domain/                               # Interfaces (if any)
    └── [Domain interfaces]
```

**For Client Libraries** (auth-client, parse-client):
```
tech.eagledrive.client.{service}/         # ✓ Current structure is fine
└── {Service}Client.java
```

### 4. Fix Domain DTOs Duplication

**Action Required**:
- Remove `tech.eagledrive.services.auth.domain.dto.*` packages
- Consolidate all DTOs in `tech.eagledrive.domain.dto.*`
- Update all imports across codebase

### 5. Standardize Naming Conventions

**REST Resources**:
- Services: `{Service}Resource.java` (e.g., `MatchResource`, `ParseResource`)
- Applications: `{Feature}Controller.java` (e.g., `AuthController`, `ResumeController`)

**Domain Services**:
- Use descriptive names: `{Purpose}Service.java` (e.g., `SourceDocumentService`)

**Repositories**:
- Use `{Entity}Repository.java` (e.g., `SourceDocumentUploadRepository`)

**Client Producers**:
- Use `{Service}ClientProducer.java` (e.g., `S3ClientProducer`, `DynamoDbClientProducer`)

## Priority Refactoring Tasks

### High Priority

1. **Remove duplicate DTOs** in `libs/domain-dtos`
   - Remove `tech.eagledrive.services.auth.domain.dto.*`
   - Update all imports to use `tech.eagledrive.domain.dto.auth.*`

2. **Standardize match-service structure**
   - Move `MatchResource` → `presentation/rest/MatchResource`

3. **Standardize parse-service structure**
   - Move `ParseResource` → `presentation/rest/ParseResource`
   - Move `dto/` → `domain/dto/`
   - Move `persistence/` → `infrastructure/persistence/`

### Medium Priority

4. **Standardize textkernel-api structure**
   - Move `client/` → `infrastructure/client/` or `adapters/client/`
   - Move `config/` → `infrastructure/config/`
   - Move `persistence/` → `infrastructure/persistence/`

5. **Standardize aws-api structure**
   - Move `client/` → `infrastructure/client/` or `adapters/aws/`

6. **Standardize backend-candidate structure**
   - Move controllers → `presentation/rest/`
   - Consider renaming to match service pattern (or keep Controller suffix for applications)

### Low Priority

7. **Consider consolidating runtime packages**
   - All modules have `runtime/StartupBanner.java`
   - Could be moved to a shared library if identical

## Implementation Notes

- All refactorings should maintain backward compatibility where possible
- Update imports systematically across the codebase
- Update documentation (README files) to reflect new structure
- Consider creating a shared base structure template for new services

## Success Criteria

✅ All services follow the same Clean Architecture package structure
✅ All REST resources/controllers are in `presentation/rest/` packages
✅ All infrastructure concerns are in `infrastructure/` packages
✅ No duplicate DTOs across modules
✅ Consistent naming conventions (Resource vs Controller)
✅ Clear separation of domain, infrastructure, and presentation layers

