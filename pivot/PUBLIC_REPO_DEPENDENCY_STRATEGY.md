# Public Repository Dependency Strategy

## Recommendation: **Hybrid Approach**

Use **real Maven dependencies** for annotations and interfaces, but keep them as **minimal, standalone artifacts** that don't reveal implementations.

---

## Strategy Overview

### ✅ **Real Dependencies** (Publish as Maven Artifacts)

**What**: Annotations and pure interfaces that define contracts

**Why**:
- Maximum value demonstration - people can actually use them
- Shows you're serious about the platform
- Creates a "try before you buy" experience
- Builds trust through usable code
- Can be published to Maven Central or private repository

**Examples**:
- `@Secured` annotation
- `@AllowedServices` annotation  
- `@ServiceMetrics` annotation
- `TokenValidator` interface
- `MetricsRecorder` interface
- `RateLimiter` interface
- `MetricsResultIndicator` interface

### 📄 **Reference Only** (Documentation/Examples)

**What**: Example code, documentation, ADRs, architecture docs

**Why**:
- No maintenance burden
- No versioning complexity
- Clear demonstration without commitment
- Can evolve independently

**Examples**:
- Example resources showing usage
- Architecture documentation
- ADRs
- Configuration templates
- CI/CD workflow examples

---

## Implementation Approach

### Option 1: Minimal Public Artifacts (Recommended)

Create **new, minimal Maven modules** in the public repo that contain only:

1. **Annotations** (no implementation dependencies)
2. **Interfaces** (pure contracts)
3. **Exception classes** (if they're domain exceptions, not infrastructure)

**Structure**:
```
public-repo/
├── contracts/
│   ├── security-contracts/
│   │   ├── pom.xml
│   │   └── src/main/java/
│   │       └── tech/eagledrive/security/contracts/
│   │           ├── Secured.java
│   │           ├── AllowedServices.java
│   │           ├── TokenValidator.java
│   │           └── RateLimiter.java
│   └── metrics-contracts/
│       ├── pom.xml
│       └── src/main/java/
│           └── tech/eagledrive/metrics/contracts/
│               ├── ServiceMetrics.java
│               ├── MetricsRecorder.java
│               └── MetricsResultIndicator.java
```

**Key Points**:
- **No implementation dependencies** - these are pure contracts
- **Minimal dependencies** - only what's needed for annotations (Jakarta/Jakarta EE)
- **Separate package names** - `tech.eagledrive.security.contracts` vs `tech.eagledrive.security` (private)
- **Can be published** to Maven Central or private Maven repository
- **Version independently** from private repo

### Option 2: Copy for Reference (Simpler, Less Value)

Just copy the files to the public repo for documentation purposes.

**Pros**:
- No maintenance
- No versioning
- Simple

**Cons**:
- Less compelling
- Can't actually use them
- Might seem like "vaporware"

---

## Recommended: Option 1 - Minimal Public Artifacts

### Why This Works Best

1. **Maximum Value**: People can actually import and use your annotations
   ```java
   // They can do this:
   import tech.eagledrive.security.contracts.Secured;
   
   @Secured
   public Response getData() { ... }
   ```

2. **Clear Separation**: 
   - Public: `tech.eagledrive.security.contracts.*` (contracts only)
   - Private: `tech.eagledrive.security.*` (full implementation)

3. **No Implementation Leakage**: 
   - Contracts have no implementation code
   - No dependencies on your private implementations
   - Can't accidentally expose business logic

4. **Versioning Strategy**:
   - Public contracts: Version independently (e.g., `1.0.0`, `1.1.0`)
   - Private implementation: Uses contracts as dependency
   - Clear upgrade path for customers

5. **Monetization Path**:
   - Public contracts: Free/open (creates adoption)
   - Private implementation: Commercial license
   - Customers use contracts, license implementation

### Example: Public Contract Module

**`contracts/security-contracts/pom.xml`**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>tech.eagledrive</groupId>
    <artifactId>security-contracts</artifactId>
    <version>1.0.0</version>
    
    <properties>
        <java.version>25</java.version>
        <jakarta.version>4.0.0</jakarta.version>
    </properties>
    
    <dependencies>
        <!-- Only annotation dependencies - no implementation -->
        <dependency>
            <groupId>jakarta.interceptor</groupId>
            <artifactId>jakarta.interceptor-api</artifactId>
            <version>${jakarta.version}</version>
        </dependency>
        <dependency>
            <groupId>jakarta.ws.rs</groupId>
            <artifactId>jakarta.ws.rs-api</artifactId>
            <version>${jakarta.version}</version>
        </dependency>
    </dependencies>
</project>
```

**`contracts/security-contracts/src/main/java/tech/eagledrive/security/contracts/Secured.java`**:
```java
package tech.eagledrive.security.contracts;

import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark JAX-RS resource methods that require user authentication.
 * 
 * <p>This is a contract definition. The actual implementation is provided
 * by the commercial platform.
 * 
 * <p>Example usage:
 * <pre>
 * {@code
 * @Path("/api/resource")
 * public class MyResource {
 *     @GET
 *     @Secured
 *     public Response getResource() {
 *         return Response.ok().build();
 *     }
 * }
 * }
 * </pre>
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Secured
{
}
```

### Private Repo Integration

Your private repo would then depend on the public contracts:

**`libs/security/pom.xml`** (private):
```xml
<dependencies>
    <!-- Public contract -->
    <dependency>
        <groupId>tech.eagledrive</groupId>
        <artifactId>security-contracts</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- Private implementation dependencies -->
    <dependency>
        <groupId>tech.eagledrive</groupId>
        <artifactId>cache</artifactId>
        <version>${project.version}</version>
    </dependency>
    <!-- ... other private dependencies ... -->
</dependencies>
```

**`libs/security/src/main/java/tech/eagledrive/security/presentation/rest/Secured.java`** (private):
```java
package tech.eagledrive.security.presentation.rest;

// Re-export the public contract for convenience
// OR use the contract directly in your code
import tech.eagledrive.security.contracts.Secured;

// Your implementation uses the contract
// The actual interceptor implementation stays private
```

**OR** keep your private `@Secured` and have it extend/use the public contract:

```java
package tech.eagledrive.security.presentation.rest;

import tech.eagledrive.security.contracts.Secured as SecuredContract;

// Your private implementation that provides the actual functionality
// but uses the public contract as the base
```

---

## Publishing Strategy

### Option A: Maven Central (Maximum Visibility)

1. **Publish contracts to Maven Central**
   - Group ID: `tech.eagledrive`
   - Artifact IDs: `security-contracts`, `metrics-contracts`
   - Version: Semantic versioning (1.0.0, 1.1.0, etc.)

2. **Benefits**:
   - Maximum discoverability
   - Standard dependency management
   - Builds trust through transparency

3. **Requirements**:
   - Sonatype OSSRH account
   - GPG signing
   - Follow Maven Central guidelines

### Option B: GitHub Packages (Simpler)

1. **Publish to GitHub Packages**
   - Free for public repos
   - Integrated with GitHub
   - Easy to set up

2. **Usage**:
   ```xml
   <repositories>
       <repository>
           <id>github</id>
           <url>https://maven.pkg.github.com/yourusername/your-public-repo</url>
       </repository>
   </repositories>
   ```

### Option C: Private Maven Repository (Maximum Control)

1. **Host your own Maven repository**
   - Nexus, Artifactory, or simple HTTP server
   - Full control over access
   - Can require authentication for some artifacts

---

## Versioning Strategy

### Public Contracts

- **Semantic versioning**: `1.0.0`, `1.1.0`, `2.0.0`
- **Breaking changes**: Major version bump
- **New annotations/interfaces**: Minor version bump
- **Documentation fixes**: Patch version bump

### Private Implementation

- **Can use public contracts**: `security-contracts:1.0.0`
- **Independent versioning**: Your internal versioning scheme
- **Upgrade contracts**: When you add new contracts, bump public version

---

## Migration Path

### Step 1: Extract Contracts

1. Create new `contracts/` directory in public repo
2. Create minimal Maven modules for each contract type
3. Copy only annotations and interfaces (no implementations)
4. Remove all implementation dependencies
5. Use separate package names (`*.contracts.*`)

### Step 2: Publish Contracts

1. Set up publishing (Maven Central, GitHub Packages, or private)
2. Publish initial version (e.g., `1.0.0`)
3. Document in public repo README

### Step 3: Update Private Repo

1. Add public contracts as dependencies
2. Refactor private code to use public contracts
3. Keep all implementations private

### Step 4: Documentation

1. Update public repo README with Maven coordinates
2. Add usage examples
3. Link to commercial licensing

---

## Example: Complete Flow

### Public Repo Structure

```
public-repo/
├── contracts/
│   ├── security-contracts/
│   │   ├── pom.xml
│   │   └── src/main/java/.../contracts/
│   │       ├── Secured.java
│   │       ├── AllowedServices.java
│   │       └── TokenValidator.java
│   └── metrics-contracts/
│       ├── pom.xml
│       └── src/main/java/.../contracts/
│           ├── ServiceMetrics.java
│           └── MetricsRecorder.java
├── examples/
│   └── usage-examples.java
└── README.md
```

### Public Repo README

```markdown
# Platform Contracts

## Maven Dependency

```xml
<dependency>
    <groupId>tech.eagledrive</groupId>
    <artifactId>security-contracts</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Usage

```java
import tech.eagledrive.security.contracts.Secured;

@Path("/api/resource")
public class MyResource {
    @GET
    @Secured
    public Response getResource() {
        return Response.ok().build();
    }
}
```

## Commercial License

The contracts are open source. The full implementation is available under commercial license.
Contact us for licensing information.
```

### Private Repo Usage

```java
// Private implementation uses public contracts
import tech.eagledrive.security.contracts.Secured;
import tech.eagledrive.security.contracts.TokenValidator;

// Your private implementation
@ApplicationScoped
public class CognitoTokenValidator implements TokenValidator {
    // Private implementation
}
```

---

## Decision Matrix

| Aspect | Real Dependencies | Reference Only |
|--------|------------------|----------------|
| **Value Demonstration** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| **Maintenance** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Versioning Complexity** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Monetization Potential** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| **Trust Building** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| **Implementation Risk** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |

**Recommendation**: **Real Dependencies** (Option 1) for maximum value, with careful separation to prevent implementation leakage.

---

## Next Steps

1. ✅ **Decide on publishing target** (Maven Central, GitHub Packages, or private)
2. ✅ **Create minimal contract modules** in public repo
3. ✅ **Extract annotations and interfaces** (no implementations)
4. ✅ **Set up publishing** (Maven Central setup or GitHub Packages)
5. ✅ **Publish initial version** (1.0.0)
6. ✅ **Update private repo** to use public contracts as dependencies
7. ✅ **Document in public repo** with usage examples
