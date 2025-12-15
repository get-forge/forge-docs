# **ADR-0012: Clean Architecture Package Structure Standards**

**Date:** 2025-12-10  
**Status:** Accepted  
**Context:** Standardization of Java package structure across all modules using Clean Architecture principles

---

## **Context**

The codebase had inconsistent package structures across modules:
- Some services had REST resources at root level, others in sub-packages
- Inconsistent naming conventions (Resource vs Controller)
- Infrastructure concerns mixed with domain logic
- Duplicate DTOs across modules
- No clear separation between domain, infrastructure, and presentation layers

This inconsistency made the codebase harder to navigate, understand, and maintain. New developers had to learn different patterns for each module.

---

## **Decision**

We will adopt **Clean Architecture package structure standards** across all modules. All services, applications, and libraries must follow these standards.

### **Core Principles**

1. **Separation of Concerns**: Clear boundaries between domain, infrastructure, and presentation layers
2. **Consistent Structure**: Same package structure across all modules of the same type
3. **Naming Conventions**: Consistent naming patterns for resources, services, and repositories
4. **Single Source of Truth**: DTOs defined once in `libs/domain-dtos`, not duplicated in services

---

## **Package Structure Standards**

### **Services** (`services/{service-name}`)

```
tech.eagledrive.services.{service}/
├── domain/                               # Business logic
│   ├── dto/                              # Domain DTOs (if service-specific)
│   ├── exception/                        # Domain exceptions
│   └── [Domain services/interfaces]
├── infrastructure/                       # External concerns
│   ├── config/                           # Configuration producers
│   ├── persistence/                      # Database repositories
│   ├── mapper/                           # Entity/DTO mappers
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

**Example**: `services/candidate-service`
```
tech.eagledrive.services.candidate/
├── domain/
│   └── CandidateService.java
├── infrastructure/
│   └── persistence/
│       └── CandidateRepository.java
└── presentation/
    └── rest/
        └── CandidateResource.java
```

### **Applications** (`application/{app-name}`)

```
tech.eagledrive.application.{app}/
├── presentation/                         # HTTP/REST layer
│   └── rest/                             # JAX-RS controllers
│       └── [REST controllers]
└── runtime/                              # Runtime utilities
    └── StartupBanner.java
```

**Example**: `application/backend-candidate`
```
tech.eagledrive.application.backend/
└── presentation/
    └── rest/
        ├── AuthController.java
        ├── CandidateController.java
        └── ResumeController.java
```

### **Infrastructure Libraries** (`libs/{library-name}`)

For libraries that provide infrastructure concerns (aws-api, textkernel-api):

```
tech.eagledrive.{library}/
├── infrastructure/
│   ├── config/                           # Client producers
│   ├── client/                           # Client implementations
│   └── persistence/                      # Repository implementations
└── domain/                               # Interfaces (if any)
    └── [Domain interfaces]
```

**Example**: `libs/aws-api`
```
tech.eagledrive.aws/
└── infrastructure/
    ├── config/
    │   └── S3ClientProducer.java
    └── client/
        └── s3/
            └── S3Client.java
```

### **Client Libraries** (`libs/domain-clients`)

For REST client interfaces:

```
tech.eagledrive.client.{service}/
└── {Service}Client.java
```

**Example**: `libs/domain-clients`
```
tech.eagledrive.client.auth/
└── AuthServiceClient.java

tech.eagledrive.client.document/
└── ParseServiceClient.java
```

### **Domain Libraries** (`libs/domain-*`)

For shared domain concerns:

```
tech.eagledrive.domain.{concern}/
└── [Domain types, DTOs, interfaces]
```

**Example**: `libs/domain-dtos`
```
tech.eagledrive.domain.dto.auth/
├── LoginRequest.java
├── RegisterRequest.java
└── AuthResponse.java
```

---

## **Naming Conventions**

### **REST Resources/Controllers**

- **Services**: Use `{Service}Resource.java` suffix, place in `presentation/rest/`
  - Example: `MatchResource`, `CandidateResource`, `ResumeResource`
- **Applications**: Use `{Feature}Controller.java` suffix, place in `presentation/rest/`
  - Example: `AuthController`, `CandidateController`, `ResumeController`
- **Exception**: Protocol-specific resources (OIDC) can stay in protocol packages (`oidc/`)

### **Domain Services**

- Use descriptive names: `{Purpose}Service.java`
- Example: `CandidateService`, `DocumentService`, `MatchService`

### **Repositories**

- Use `{Entity}Repository.java`
- Example: `CandidateRepository`, `ResumeRepository`, `MatchRepository`

### **Client Producers**

- Use `{Service}ClientProducer.java`
- Example: `S3ClientProducer`, `DynamoDbClientProducer`, `TextkernelClientProducer`

### **Mappers**

- Use `{Entity}Mapper.java`
- Example: `CandidateMapper`, `ResumeMapper`, `MatchMapper`

---

## **DTO Standards**

### **Shared DTOs**

All shared DTOs must be defined in `libs/domain-dtos`:
- `tech.eagledrive.domain.dto.auth.*` - Authentication DTOs
- `tech.eagledrive.domain.dto.user.*` - User DTOs
- `tech.eagledrive.domain.dto.textkernel.*` - Textkernel DTOs
- `tech.eagledrive.domain.dto.document.*` - Document DTOs

### **Service-Specific DTOs**

Service-specific DTOs can be defined in `services/{service}/domain/dto/`:
- Example: `services/match-service/domain/dto/MatchRequest.java`

**Rule**: If a DTO is used by multiple services or the frontend, it must be in `libs/domain-dtos`.

---

## **Examples**

### **Good Structure: candidate-service**

```
tech.eagledrive.services.candidate/
├── domain/
│   └── CandidateService.java
├── infrastructure/
│   └── persistence/
│       └── CandidateRepository.java
└── presentation/
    └── rest/
        └── CandidateResource.java
```

### **Good Structure: security library**

```
tech.eagledrive.security/
├── domain/
│   ├── dto/
│   ├── exception/
│   ├── ServiceTokenValidator.java
│   └── UserAuthenticationProvider.java
├── infrastructure/
│   └── config/
│       └── CognitoClientProducer.java
├── presentation/
│   └── rest/
│       ├── exception/
│       ├── Secured.java
│       └── [Other presentation classes]
└── adapters/
    └── cognito/
        └── CognitoUserAuthenticationProvider.java
```

### **Good Structure: backend-candidate**

```
tech.eagledrive.application.backend/
└── presentation/
    └── rest/
        ├── AuthController.java
        ├── CandidateController.java
        └── ResumeController.java
```

---

## **Consequences**

**Positive:**

- **Consistency**: Same structure across all modules makes codebase predictable
- **Maintainability**: Clear separation of concerns reduces cognitive load
- **Onboarding**: New developers learn one pattern, not many
- **Testability**: Clean separation enables easier unit and integration testing
- **Scalability**: Structure supports growth without refactoring

---

## **Implementation**

### **New Modules**

All new modules must follow these standards from creation.

### **Existing Modules**

Existing modules should be refactored to follow these standards when:
- Major feature work is being done
- Significant refactoring is already planned
- Code review identifies structure issues

**Note**: Not all modules need immediate refactoring. Standards apply to new code and major changes.

---

## **Validation**

Code reviews should verify:
- REST resources/controllers are in `presentation/rest/`
- Domain logic is in `domain/`
- Infrastructure concerns are in `infrastructure/`
- DTOs are not duplicated across modules
- Naming conventions are followed

---

**Decision Owner:** Architecture Team

**Review Cycle:** Review annually or when new module types are introduced

---

