# JWT Utilities: Cognito-Specific References

## Analysis

### `JwtPayloadUtils.extractServiceId()`

**Issue:** Hardcodes Cognito-specific claim name
- Line 50: `return extractText(node, "custom:service_id");`
- `custom:service_id` is AWS Cognito's format for custom attributes
- The `custom:` prefix is Cognito-specific

**Current Usage:**
- Used in `RateLimitKeyResolver` for service token identification
- Used throughout codebase for service-to-service authentication

**Options for Open-Sourcing:**

#### Option 1: Rename to Indicate Cognito (Recommended)
```java
// Rename class/method
public static String extractCognitoServiceId(final String jwtToken)
// or
public static String extractServiceIdFromCognitoToken(final String jwtToken)
```

**Pros:**
- ✅ Clear that it's Cognito-specific
- ✅ Minimal code changes
- ✅ Honest about implementation

**Cons:**
- ⚠️ Less generic (but it's not generic anyway)

#### Option 2: Make Generic (Accept Claim Name)
```java
public static String extractClaim(final String jwtToken, final String claimName) {
    // ... existing logic but use claimName parameter
    return extractText(node, claimName);
}

// Convenience method for Cognito
public static String extractServiceId(final String jwtToken) {
    return extractClaim(jwtToken, "custom:service_id");
}
```

**Pros:**
- ✅ Generic and reusable
- ✅ Backward compatible (keep convenience method)
- ✅ More useful for OSS consumers

**Cons:**
- ⚠️ More code changes
- ⚠️ Breaking change if removing convenience method

#### Option 3: Keep Name, Document Cognito Requirement
```java
/**
 * Extracts service ID from JWT token.
 * 
 * <p><strong>Note:</strong> This method expects tokens issued by AWS Cognito
 * with a {@code custom:service_id} claim. For other JWT providers, use
 * {@link #extractClaim(String, String)} instead.</p>
 * 
 * @param jwtToken the JWT token (must be Cognito-issued)
 * @return service ID if found, null otherwise
 */
public static String extractServiceId(final String jwtToken)
```

**Pros:**
- ✅ No code changes
- ✅ Clear documentation

**Cons:**
- ⚠️ Misleading name (sounds generic)
- ⚠️ Easy to misuse

---

### `JwtUsernameExtractor.extractUsername()`

**Issue:** Checks Cognito-specific claim first
- Line 28: `"cognito:username"` is first in priority list
- But has fallbacks to standard OIDC fields: `email`, `preferred_username`, `username`, `sub`

**Current Behavior:**
1. Checks `cognito:username` (Cognito-specific)
2. Falls back to `email` (standard OIDC)
3. Falls back to `preferred_username` (standard OIDC)
4. Falls back to `username` (common)
5. Falls back to `sub` (standard OIDC)

**Options for Open-Sourcing:**

#### Option 1: Rename to Indicate Cognito Preference (Recommended)
```java
/**
 * Extracts username from JWT token, with Cognito preference.
 * 
 * <p>Checks Cognito-specific claims first, then falls back to standard OIDC fields.
 * Works with both Cognito tokens and standard OIDC tokens.</p>
 */
public static String extractUsernameWithCognitoPreference(final String jwtToken)
```

**Pros:**
- ✅ Accurate description
- ✅ Still works with non-Cognito tokens (fallbacks)
- ✅ Clear about preference

**Cons:**
- ⚠️ Longer method name

#### Option 2: Make Priority List Configurable
```java
private static final List<String> DEFAULT_USERNAME_FIELDS = List.of(
    "cognito:username",  // Cognito-specific
    "email",
    "preferred_username",
    "username",
    "sub"
);

public static String extractUsername(final String jwtToken) {
    return extractUsername(jwtToken, DEFAULT_USERNAME_FIELDS);
}

public static String extractUsername(final String jwtToken, final List<String> fieldPriority) {
    // Use provided priority list
}
```

**Pros:**
- ✅ Generic and flexible
- ✅ Backward compatible
- ✅ OSS consumers can customize

**Cons:**
- ⚠️ More complex API
- ⚠️ May be overkill

#### Option 3: Keep Name, Document Cognito Preference
```java
/**
 * Extracts username from JWT token.
 * 
 * <p>Checks claims in the following order:
 * <ol>
 *   <li>{@code cognito:username} - AWS Cognito-specific (if using Cognito)</li>
 *   <li>{@code email} - Standard OIDC field</li>
 *   <li>{@code preferred_username} - Standard OIDC field</li>
 *   <li>{@code username} - Common field</li>
 *   <li>{@code sub} - Standard OIDC subject</li>
 * </ol>
 * 
 * Works with both Cognito tokens and standard OIDC tokens.</p>
 */
public static String extractUsername(final String jwtToken)
```

**Pros:**
- ✅ No code changes
- ✅ Clear documentation
- ✅ Accurate (it does work with non-Cognito tokens)

**Cons:**
- ⚠️ Name doesn't indicate Cognito preference

---

## Recommendation

### For `extractServiceId()`: **Option 1 (Rename)**

Rename to clearly indicate Cognito:
```java
public static String extractCognitoServiceId(final String jwtToken)
```

**Rationale:**
- It's hardcoded to Cognito format
- No fallback or generic behavior
- Clear naming prevents misuse

### For `extractUsername()`: **Option 3 (Document)**

Keep the name but document Cognito preference:
```java
/**
 * Extracts username from JWT token.
 * 
 * <p>Optimized for AWS Cognito tokens but works with any OIDC-compliant token.
 * Checks Cognito-specific claims first, then falls back to standard OIDC fields.</p>
 * 
 * <p>Claim priority:
 * <ol>
 *   <li>{@code cognito:username} - Cognito-specific</li>
 *   <li>{@code email} - Standard OIDC</li>
 *   <li>{@code preferred_username} - Standard OIDC</li>
 *   <li>{@code username} - Common</li>
 *   <li>{@code sub} - Standard OIDC subject</li>
 * </ol>
 */
public static String extractUsername(final String jwtToken)
```

**Rationale:**
- Has fallbacks to standard fields
- Works with non-Cognito tokens
- Name is acceptable if documented
- Less disruptive than renaming

---

## Alternative: Create Generic Versions

If you want to provide both Cognito-specific and generic versions:

```java
// Generic version
public static String extractClaim(final String jwtToken, final String claimName) {
    // ... implementation
}

// Cognito-specific convenience methods
public static String extractCognitoServiceId(final String jwtToken) {
    return extractClaim(jwtToken, "custom:service_id");
}

public static String extractCognitoUsername(final String jwtToken) {
    return extractClaim(jwtToken, "cognito:username");
}

// Generic username extractor (no Cognito preference)
public static String extractUsername(final String jwtToken, final List<String> fieldPriority) {
    // Use provided priority list
}

// Cognito-optimized username extractor (current behavior)
public static String extractUsernameWithCognitoPreference(final String jwtToken) {
    return extractUsername(jwtToken, List.of(
        "cognito:username",
        "email",
        "preferred_username",
        "username",
        "sub"
    ));
}
```

This provides maximum flexibility but adds API complexity.

---

## Additional Consideration: Rate Limiting Use Case

### The Problem

`extractServiceId()` is used in `RateLimitKeyResolver` alongside `extractUsername()`:
1. First tries `extractServiceId()` → if found, uses `service:` prefix
2. If not found, tries `extractUsername()` → if found, uses `user:` prefix
3. If neither found, uses `auth:unidentified`

**Question:** If rate limiting must work with any JWT token (not just Cognito), should `extractServiceId()` also have fallbacks to standard OIDC claims?

### Standard OIDC Claims for Service/Client Identification

For non-Cognito OIDC providers, service/client tokens (machine-to-machine) typically use:
- `azp` (Authorized Party) - identifies the client/service that obtained the token
- `client_id` - OAuth client identifier (may appear in token)
- `aud` (Audience) - usually an array, identifies resource servers (not the client)

### The Challenge

**Problem:** `azp` and `client_id` can appear in **both** user tokens and service tokens:
- User tokens: `azp` = the client app the user logged in through
- Service tokens: `azp` = the service/client making the request

**Risk:** If we add fallbacks, we might incorrectly identify user tokens as service tokens.

### Recommendation: Conditional Fallbacks

**Option 1: Add Fallbacks with User Claim Check (Recommended)**

Only use `azp`/`client_id` as service identifier if user claims are NOT present:

```java
public static String extractServiceId(final String jwtToken) {
    // ... existing parsing logic ...
    
    // First try Cognito-specific claim
    String serviceId = extractText(node, "custom:service_id");
    if (serviceId != null) {
        return serviceId;
    }
    
    // Check if this looks like a user token (has user claims)
    boolean hasUserClaims = hasUserClaims(node);
    
    // Only use standard OIDC claims if this doesn't look like a user token
    if (!hasUserClaims) {
        // Try azp (Authorized Party) - standard OIDC
        serviceId = extractText(node, "azp");
        if (serviceId != null) {
            return serviceId;
        }
        
        // Try client_id - some providers include this
        serviceId = extractText(node, "client_id");
        if (serviceId != null) {
            return serviceId;
        }
    }
    
    return null;
}

private static boolean hasUserClaims(final JsonNode node) {
    // Check for common user claims
    return node.has("email") || 
           node.has("preferred_username") || 
           node.has("cognito:username") ||
           node.has("name");
}
```

**Pros:**
- ✅ Works with non-Cognito providers
- ✅ Avoids misclassifying user tokens as service tokens
- ✅ Maintains backward compatibility

**Cons:**
- ⚠️ More complex logic
- ⚠️ Heuristic-based (not perfect)

**Option 2: Keep Cognito-Only, Document Limitation**

Keep current implementation, document that it's Cognito-specific:

```java
/**
 * Extracts service ID from Cognito JWT tokens.
 * 
 * <p><strong>Note:</strong> This method only works with AWS Cognito tokens
 * that include the {@code custom:service_id} claim. For non-Cognito providers,
 * service identification should be handled separately.</p>
 * 
 * <p>For rate limiting purposes, if this returns null, the system will
 * fall back to user identification via {@link JwtUsernameExtractor}.</p>
 */
public static String extractCognitoServiceId(final String jwtToken)
```

**Pros:**
- ✅ Simple and clear
- ✅ No risk of misclassification
- ✅ Honest about limitations

**Cons:**
- ⚠️ Doesn't work with non-Cognito providers for service identification
- ⚠️ Non-Cognito service tokens will fall back to user identification (which may be wrong)

### Final Recommendation

**For open-sourcing:** Use **Option 2** (keep Cognito-only, rename and document).

**Rationale:**
1. Simpler and safer (no misclassification risk)
2. Clear about what it does
3. Non-Cognito providers can implement their own service ID extraction
4. Rate limiting will still work (falls back to user identification or `auth:unidentified`)

**If you want maximum compatibility:** Use **Option 1** (conditional fallbacks), but this adds complexity and risk.

---

## Summary

| Utility | Cognito-Specific? | Recommendation |
|---------|-------------------|----------------|
| `extractServiceId()` | ✅ Yes (hardcoded) | **Rename** to `extractCognitoServiceId()` + document limitation |
| `extractUsername()` | ⚠️ Partial (preference, not requirement) | **Document** Cognito preference, keep name |
