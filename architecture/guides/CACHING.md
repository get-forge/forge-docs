# Caching

Quarkus **Cache** (Caffeine) with shared settings in
[`config/src/main/resources/cache.properties`](https://github.com/get-forge/forge-platform/blob/main/config/src/main/resources/cache.properties).
See [ADR 0014](../decisions/0014-application-caching-strategy.md).

## What is enabled

| Cache name (see `cache.properties`) | Role                                                                                                                   |
|-------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| `token-validation`                  | Outcome of JWT validation in `CompositeTokenValidator` (`@CacheResult` + `TokenCacheKeyGenerator` in `libs/security`). |
| `actor-profiles`                    | Actor profile reads in `ActorService` (`@CacheResult` / `@CacheInvalidate` + `ActorCacheKeyGenerator`).                |
| `parsed-documents`                  | Parsed document reads in `DocumentService` (`@CacheResult` / `@CacheInvalidate` + `ActorCacheKeyGenerator`).           |
| `service-tokens`                    | Service token resolution in `TokenCache` (`auth-service`) with explicit invalidate on exchange.                        |

## Supporting code

- **Key generators**: `libs/cache` — `TokenCacheKeyGenerator`, `ActorCacheKeyGenerator`,
  `ServiceTokenCacheKeyGenerator`.
- **Validation**: `CompositeTokenValidator` — `@CacheResult(cacheName = "token-validation", ...)`.
- **Metrics**: per-cache Caffeine metrics flags in `cache.properties` where set.

## Not in this doc

Distributed cache backends (e.g. Redis) for production multi-node — not wired in the current default `cache.properties`.
