# Notification Service Design Specification

**Date:** 2026-01-23  
**Status:** Design Phase  
**Context:** Design specification for the Notification Service - a centralized notification delivery service for the commercial platform

---

## Executive Summary

The Notification Service provides centralized, multi-channel notification delivery (email, SMS, push notifications) with provider abstraction, template management, delivery tracking, and compliance features. This service is the #1 priority for commercializing the platform.

**Key Design Principles:**
- **Stateless** - Fully stateless service (following ADR-0011), enabling horizontal scaling
- **Fire-and-Forget / Asynchronous Messaging** - Clients receive immediate acknowledgment that notification was accepted, but don't wait for delivery. Processing happens asynchronously at a later time, decoupling clients from delivery.
- **Eventually-Consistent** - Notifications are guaranteed to be delivered eventually, but not immediately
- **Priority-Based** - Priority system ensures critical notifications (e.g., password resets) are processed before low-priority (e.g., marketing)

**Target Deployment:** AWS (leveraging AWS SES for email)  
**Local Development:** LocalStack (with SES support)  
**API Design:** RESTful (following ADR-0010)  
**Architecture Pattern:** Clean Architecture (following ADR-0012)  

---

## Table of Contents

1. [Service Overview](#service-overview)
2. [Architecture](#architecture)
3. [API Design](#api-design)
4. [Domain Model](#domain-model)
5. [Provider Abstraction](#provider-abstraction)
6. [Template Management](#template-management)
7. [Delivery Tracking](#delivery-tracking)
8. [Compliance Features](#compliance-features)
9. [Infrastructure & Dependencies](#infrastructure--dependencies)
10. [LocalStack Support](#localstack-support)
11. [Decision Points](#decision-points)
12. [Implementation Phases](#implementation-phases)
13. [Open Questions](#open-questions)

---

## Service Overview

### Purpose

Centralized notification delivery service that:
- Abstracts notification providers (AWS SES, SendGrid, Twilio, etc.)
- Manages templates and content
- Tracks delivery status and retries
- Handles compliance (unsubscribe, opt-out)
- Provides rate limiting and cost optimization

### Key Features

1. **Multi-Channel Support**
   - Email (primary, via AWS SES)
   - SMS (future, via Twilio/SNS)
   - Push Notifications (future, via FCM/APNS)

2. **Template Management**
   - HTML/text email templates
   - Variable substitution
   - Template versioning
   - Multi-language support (future)

3. **Delivery Tracking**
   - Delivery status (sent, delivered, bounced, failed)
   - Retry logic with exponential backoff
   - Delivery webhooks (SES SNS notifications)
   - Delivery history

4. **Provider Abstraction**
   - Switch providers without changing consumers
   - Multi-provider support (failover, load balancing)
   - Provider-specific configuration

5. **Compliance**
   - Unsubscribe management
   - Opt-out tracking
   - Delivery preferences
   - Compliance reporting

6. **Rate Limiting**
   - Per-channel rate limits
   - Per-provider rate limits
   - Per-tenant rate limits (future)

---

## Architecture

### Core Principles

#### Stateless Design

The Notification Service is **fully stateless** (following ADR-0011: Stateless JWT Authentication). This means:

1. **No Server-Side State**
   - No in-memory session storage
   - No sticky sessions required
   - All state persisted in database (PostgreSQL) or external services (SES)

2. **Horizontal Scalability**
   - Any service instance can handle any request
   - No session affinity or shared state between instances
   - Stateless REST API enables true horizontal scaling

3. **State Persistence**
   - Notification records stored in PostgreSQL
   - Templates stored in PostgreSQL (or S3)
   - Delivery status tracked in database
   - Unsubscribe records in database

4. **Authentication**
   - Service-to-service authentication via JWT tokens (`@AllowedServices`)
   - No session cookies or server-side session storage
   - Tokens validated on each request

5. **Provider State**
   - AWS SES is stateless (each API call is independent)
   - No provider connection pooling or stateful sessions
   - Provider clients can be created per-request or pooled (stateless connection pool)

**Benefits:**
- True horizontal scaling without sticky sessions
- Simplified deployment and load balancing
- Better fault tolerance (any instance can handle any request)
- Consistent with existing platform architecture (ADR-0011)

**Tradeoffs:**
- All state must be persisted (database lookups required)
- No in-memory caching of user sessions (acceptable - we use database caching for templates)
- Provider clients may need to be recreated (mitigated by connection pooling)

#### Fire-and-Forget / Asynchronous Messaging Pattern

The Notification Service follows the **Fire-and-Forget** (also known as **Asynchronous Messaging**) pattern. See [ADR-0015](./decisions/0015-notification-service-fire-and-forget-pattern.md) for detailed rationale and implementation.

This means:

1. **Immediate Acknowledgment**
   - Client sends notification request
   - Service immediately returns `201 Created` with notification ID
   - Client does **not** wait for delivery confirmation
   - Client only receives confirmation that the notification was **accepted** by the system

2. **Asynchronous Processing**
   - Notifications are queued and processed asynchronously
   - Processing happens at a later time (decoupled from client request)
   - Delivery time depends on system load, priority, and provider availability

3. **Decoupled System**
   - Client and notification processing are decoupled
   - Client can continue processing without waiting
   - System processes notifications independently
   - Enables better scalability and fault tolerance

#### Eventually-Consistent Design

The Notification Service provides **eventual consistency** - notifications are guaranteed to be delivered eventually, but not immediately. This means:

1. **No Immediate Delivery Guarantee**
   - API returns `201 Created` when notification is queued (not when delivered)
   - Delivery status must be checked via status endpoint
   - No synchronous waiting for delivery confirmation

3. **Priority-Based Processing**
   - High-priority notifications (e.g., password resets) processed before low-priority (e.g., marketing)
   - When system approaches capacity, low-priority notifications may be delayed
   - Priority ensures critical notifications are not blocked by bulk operations

4. **Retry Logic**
   - Failed notifications are retried with exponential backoff
   - Retries respect priority (high-priority retried more aggressively)
   - Eventually all notifications will be delivered or marked as failed

5. **Rate Limiting**
   - System may throttle low-priority notifications to protect high-priority throughput
   - Rate limits applied per priority level
   - High-priority notifications bypass or have higher rate limits

**Priority Levels:**
- **CRITICAL** - Security-critical (password resets, account lockouts) - processed immediately
- **HIGH** - Transactional (order confirmations, activation emails) - processed within seconds
- **NORMAL** - Standard notifications (welcome emails, updates) - processed within minutes
- **LOW** - Marketing, newsletters - processed when capacity available

**Benefits:**
- System can handle high load without blocking critical notifications
- Better resource utilization (process important notifications first)
- Graceful degradation under load (low-priority delayed, high-priority continues)

**Tradeoffs:**
- No immediate delivery guarantee (acceptable for most use cases)
- Consumers must check delivery status if immediate confirmation needed
- Low-priority notifications may experience delays during high load

### Package Structure (Clean Architecture)

Following ADR-0012 package structure standards:

### Recommended Enterprise Stack (Balanced)

| Component | Technology | Implementation in Forge |
|-----------|------------|-------------------------|
| **Template Storage** | DynamoDB | `notification-templates` table |
| **Template Assets** | S3 | HTML bodies, images, attachments |
| **Versioning** | DynamoDB + S3 | Version field in DynamoDB item + S3 Object Versioning |
| **Management API** | REST API | Quarkus REST Resources (ADR-0002) |
| **Rendering & Dispatch** | Service Logic | Quarkus Service + SES/SNS/EventBridge |

```
io.forge.services.notification/
├── domain/                               # Business logic
│   ├── dto/                              # Domain DTOs
│   │   ├── SendNotificationRequest.java
│   │   ├── SendNotificationResponse.java
│   │   ├── NotificationStatus.java
│   │   ├── NotificationChannel.java
│   │   └── DeliveryStatus.java
│   ├── exception/                        # Domain exceptions
│   │   ├── NotificationException.java
│   │   ├── TemplateNotFoundException.java
│   │   ├── ProviderException.java
│   │   └── UnsubscribeException.java
│   ├── NotificationService.java          # Core domain service
│   │   ├── sendWelcomeEmail()            # Domain-specific method
│   │   ├── sendPasswordResetEmail()      # Domain-specific method
│   │   └── [other domain methods]        # sendXxxEmail(), sendXxxSms(), etc.
│   ├── TemplateService.java              # Template management
│   └── DeliveryTrackingService.java     # Delivery tracking
├── infrastructure/                       # External concerns
│   ├── config/                           # Configuration producers
│   │   └── SesClientProducer.java
│   ├── persistence/                      # Database repositories
│   │   ├── NotificationRecord.java
│   │   ├── NotificationRepository.java
│   │   ├── TemplateRecord.java
│   │   ├── TemplateRepository.java
│   │   ├── DeliveryStatusRecord.java
│   │   ├── DeliveryStatusRepository.java
│   │   ├── UnsubscribeRecord.java
│   │   └── UnsubscribeRepository.java
│   ├── provider/                         # Provider implementations
│   │   ├── NotificationProvider.java     # Interface
│   │   ├── NotificationProviderService.java  # Generic sendNotification() wrapper
│   │   ├── ses/
│   │   │   ├── SesEmailProvider.java
│   │   │   └── SesConfiguration.java
│   │   ├── localstack/
│   │   │   └── LocalStackEmailProvider.java
│   │   └── mock/
│   │       └── MockNotificationProvider.java
│   ├── mapper/                           # Entity/DTO mappers
│   │   ├── NotificationMapper.java
│   │   └── TemplateMapper.java
│   ├── health/                           # Health checks
│   │   └── NotificationServiceHealthChecks.java
│   ├── metrics/                          # Metrics recording
│   │   └── NotificationMetricsRecorder.java
│   └── retry/                            # Retry logic
│       └── NotificationRetryHandler.java
├── presentation/                         # HTTP/REST layer
│   └── rest/                             # JAX-RS resources
│       ├── NotificationResource.java
│       ├── TemplateResource.java
│       ├── DeliveryStatusResource.java
│       ├── UnsubscribeResource.java
│       └── exception/                     # Exception mappers
│           └── NotificationExceptionMapper.java
└── runtime/                              # Runtime utilities
    └── StartupBanner.java
```

### Service Dependencies

**Reuses Existing Libraries:**
- `libs/security` - Service-to-service authentication (`@AllowedServices`)
- `libs/health` - Health check base classes
- `libs/metrics` - Metrics collection patterns
- `libs/cache` - Caching patterns (for template caching)
- `libs/aws-api` - AWS client producers (SES, DynamoDB)
- `libs/domain-clients` - Client interfaces (will add `NotificationServiceClient`)

**New Dependencies:**
- AWS SDK v2 for SES (`software.amazon.awssdk:ses`)
- AWS SDK v2 for DynamoDB (`software.amazon.awssdk:dynamodb`)
- Quarkus Qute (`quarkus-qute`) - Template engine

**Client Library (`libs/domain-clients`):**

Following the existing pattern (like `ActorServiceClient`, `DocumentServiceClient`), add:

```java
@RegisterRestClient
@Path("/notifications")
public interface NotificationServiceClient {
    // Domain-specific methods (primary API)
    @POST
    @Path("/welcome-email/{actorId}")
    Response sendWelcomeEmail(@PathParam("actorId") String actorId, Map<String, Object> variables);
    
    @POST
    @Path("/password-reset-email/{actorId}")
    Response sendPasswordResetEmail(@PathParam("actorId") String actorId, Map<String, Object> variables);
    
    // Generic method (for advanced use cases)
    @POST
    Response sendNotification(SendNotificationRequest request);
}
```

**Note:** Domain-specific methods hide templateId, actor resolution, and other domain knowledge from clients.

---

## API Design

Following ADR-0010 REST API Design Standards.

### Base Path

```
/notifications
```

### Endpoints

#### 1. Send Notification (Generic)

**POST** `/notifications`

Send a notification (email, SMS, push). Generic endpoint that requires full request details.

**Note:** For domain-specific notifications (e.g., welcome email), use domain-specific endpoints like `POST /notifications/welcome-email/{actorId}` which hide templateId and actor resolution.

**Request:**
```json
{
  "channel": "EMAIL",
  "templateId": "welcome-email-v1",
  "recipient": {
    "email": "user@example.com",
    "name": "John Doe"
  },
  "variables": {
    "firstName": "John",
    "activationLink": "https://example.com/activate?token=abc123"
  },
  "metadata": {
    "source": "auth-service",
    "event": "user-registered",
    "actorId": "uuid-here"
  },
  "priority": "HIGH",  // CRITICAL, HIGH, NORMAL, LOW (default: NORMAL)
  "scheduledAt": "2026-01-23T10:00:00Z"  // Optional, for future scheduling
}
```

**Response:** `201 Created`
```json
{
  "notificationId": "uuid-here",
  "status": "QUEUED",
  "channel": "EMAIL",
  "recipient": {
    "email": "user@example.com"
  },
  "estimatedDelivery": "2026-01-23T10:00:05Z"
}
```

**Security:** `@AllowedServices({"auth-service", "actor-service", "backend-actor"})`

**Error Responses:**
- `400 Bad Request` - Invalid request (missing required fields, invalid email)
- `404 Not Found` - Template not found
- `422 Unprocessable Entity` - Validation errors (unsubscribed user, invalid template variables)
- `429 Too Many Requests` - Rate limit exceeded (low-priority notifications may be throttled)
- `500 Internal Server Error` - Provider failure

**Note:** This endpoint follows the **Fire-and-Forget** pattern - it returns `201 Created` when the notification is **accepted and queued**, not when it's delivered. The client receives immediate acknowledgment and does not wait for delivery. The service processes notifications asynchronously, providing eventual consistency - notifications are guaranteed to be delivered eventually, but not immediately. Check delivery status via the status endpoint.

#### 1a. Send Welcome Email (Domain-Specific)

**POST** `/notifications/welcome-email/{actorId}`

Send a welcome email to an actor. Domain-specific endpoint that hides templateId and actor resolution.

**Request:** (optional body for variable overrides)
```json
{
  "variables": {  // Optional - override template defaults
    "firstName": "John"
  },
  "priority": "HIGH"  // Optional, defaults to HIGH
}
```

**Response:** `201 Created`
```json
{
  "notificationId": "uuid-here",
  "status": "QUEUED",
  "channel": "EMAIL",
  "recipient": {
    "email": "user@example.com"
  },
  "estimatedDelivery": "2026-01-23T10:00:05Z"
}
```

**Security:** `@AllowedServices({"auth-service", "actor-service", "backend-actor"})`

**Implementation:** 
- `NotificationService.sendWelcomeEmail(actorId)` resolves actor, maps to templateId, calls infrastructure
- Infrastructure `NotificationProviderService.sendNotification()` handles provider abstraction

**Other Domain-Specific Endpoints:**
- `POST /notifications/password-reset-email/{actorId}` - Password reset email (CRITICAL priority)
- `POST /notifications/activation-email/{actorId}` - Account activation email (HIGH priority)
- `POST /notifications/xxx-email/{actorId}` - Other domain-specific emails
- `POST /notifications/xxx-sms/{actorId}` - SMS variants (future)

**Architecture Decision:** See "Domain-Specific Notification Logic" section below for detailed options and recommendation.

---

#### 2. Get Notification Status

**GET** `/notifications/{notificationId}`

Get delivery status for a notification.

**Response:** `200 OK`
```json
{
  "notificationId": "uuid-here",
  "channel": "EMAIL",
  "status": "DELIVERED",
  "recipient": {
    "email": "user@example.com"
  },
  "sentAt": "2026-01-23T10:00:05Z",
  "deliveredAt": "2026-01-23T10:00:07Z",
  "provider": "AWS_SES",
  "providerMessageId": "0100018a-1234-5678-9abc-def012345678",
  "events": [
    {
      "event": "SENT",
      "timestamp": "2026-01-23T10:00:05Z",
      "providerResponse": "Message accepted"
    },
    {
      "event": "DELIVERED",
      "timestamp": "2026-01-23T10:00:07Z",
      "providerResponse": "Message delivered"
    }
  ]
}
```

**Security:** `@AllowedServices({"auth-service", "actor-service", "backend-actor"})`

---

#### 3. List Notifications

**GET** `/notifications`

List notifications with filtering and pagination.

**Query Parameters:**
- `recipient` (optional) - Filter by recipient email/phone
- `channel` (optional) - Filter by channel (EMAIL, SMS, PUSH)
- `status` (optional) - Filter by status (QUEUED, SENT, DELIVERED, FAILED, BOUNCED)
- `templateId` (optional) - Filter by template
- `from` (optional) - Filter by date range start (ISO 8601)
- `to` (optional) - Filter by date range end (ISO 8601)
- `page` (optional, default: 0) - Page number
- `size` (optional, default: 20) - Page size

**Response:** `200 OK`
```json
{
  "notifications": [
    {
      "notificationId": "uuid-here",
      "channel": "EMAIL",
      "status": "DELIVERED",
      "recipient": {
        "email": "user@example.com"
      },
      "templateId": "welcome-email-v1",
      "sentAt": "2026-01-23T10:00:05Z"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8
  }
}
```

**Security:** `@AllowedServices({"auth-service", "actor-service", "backend-actor"})`

---

#### 4. Create Template

**POST** `/notifications/templates`

Create a new notification template.

**Request:**
```json
{
  "templateId": "welcome-email-v1",
  "channel": "EMAIL",
  "subject": "Welcome to {{platformName}}!",
  "htmlBody": "<html><body><h1>Welcome {{firstName}}!</h1><p>Click <a href=\"{{activationLink}}\">here</a> to activate.</p></body></html>",
  "textBody": "Welcome {{firstName}}! Click {{activationLink}} to activate.",
  "variables": ["firstName", "activationLink", "platformName"],
  "description": "Welcome email sent after user registration"
}
```

**Response:** `201 Created`
```json
{
  "templateId": "welcome-email-v1",
  "channel": "EMAIL",
  "version": 1,
  "createdAt": "2026-01-23T10:00:00Z"
}
```

**Security:** `@AllowedServices({"xxxx"})` (admin-only for now; but there is no admin-tenant service)

---

#### 5. Get Template

**GET** `/notifications/templates/{templateId}`

Get template details.

**Response:** `200 OK`
```json
{
  "templateId": "welcome-email-v1",
  "channel": "EMAIL",
  "subject": "Welcome to {{platformName}}!",
  "htmlBody": "<html>...</html>",
  "textBody": "Welcome...",
  "variables": ["firstName", "activationLink", "platformName"],
  "version": 1,
  "createdAt": "2026-01-23T10:00:00Z",
  "updatedAt": "2026-01-23T10:00:00Z"
}
```

**Security:** `@AllowedServices({"auth-service", "actor-service", "backend-actor"})`

---

#### 6. List Templates

**GET** `/notifications/templates`

List all templates.

**Query Parameters:**
- `channel` (optional) - Filter by channel
- `page` (optional, default: 0)
- `size` (optional, default: 20)

**Response:** `200 OK`
```json
{
  "templates": [
    {
      "templateId": "welcome-email-v1",
      "channel": "EMAIL",
      "version": 1,
      "createdAt": "2026-01-23T10:00:00Z"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 10,
    "totalPages": 1
  }
}
```

**Security:** `@AllowedServices({"auth-service", "actor-service", "backend-actor"})`

---

#### 7. Unsubscribe

**POST** `/notifications/unsubscribe`

Unsubscribe a recipient from notifications.

**Request:**
```json
{
  "recipient": {
    "email": "user@example.com"
  },
  "channel": "EMAIL",  // Optional, null = all channels
  "reason": "USER_REQUEST"  // Optional
}
```

**Response:** `200 OK`
```json
{
  "success": true,
  "recipient": {
    "email": "user@example.com"
  },
  "unsubscribedChannels": ["EMAIL"]
}
```

**Security:** `@PermitAll` (public endpoint for unsubscribe links)

**Note:** This endpoint should also support GET with query parameters for unsubscribe links in emails:
```
GET /notifications/unsubscribe?email=user@example.com&token=verification-token&channel=EMAIL
```

---

#### 8. Check Subscription Status

**GET** `/notifications/subscription-status`

Check if a recipient is subscribed.

**Query Parameters:**
- `email` (required) - Recipient email
- `channel` (optional) - Channel to check, null = all channels

**Response:** `200 OK`
```json
{
  "recipient": {
    "email": "user@example.com"
  },
  "subscribed": true,
  "channels": {
    "EMAIL": true,
    "SMS": false,
    "PUSH": false
  }
}
```

**Security:** `@AllowedServices({"auth-service", "actor-service", "backend-actor"})`

---

#### 9. Delivery Status Webhook (SES SNS)

**POST** `/notifications/webhooks/ses`

Webhook endpoint for AWS SES delivery status notifications (bounces, complaints, deliveries).

**Request:** (SES SNS notification format)
```json
{
  "Type": "Notification",
  "Message": "{\"notificationType\":\"Bounce\",\"bounce\":{...}}"
}
```

**Response:** `200 OK`

**Security:** `@PermitAll` (validated via SNS signature verification)

**Note:** This endpoint validates SNS message signatures to ensure authenticity.

---

### Domain-Specific Notification Logic

**Problem:** The generic API (`POST /notifications`) requires clients to know:
- Template IDs (e.g., `"welcome-email-v1"`)
- How to resolve actor email from `actorId`
- Template variable mappings

This exposes internal domain knowledge to clients.

**Architectural Options:**

**Option A: Domain-Specific Methods in NotificationServiceClient (Client Library)**
- `NotificationServiceClient` has methods like `sendWelcomeEmail(actorId)`
- Client library handles templateId mapping and actor resolution
- **Pros:** Clean API, domain logic in client library
- **Cons:** Client library needs to know domain logic (template names, actor-service calls), harder to evolve

**Option B: Domain-Specific Methods in NotificationService (Domain Layer) - RECOMMENDED**
- `NotificationService` has domain methods: `sendWelcomeEmail(actorId)`, `sendPasswordResetEmail(actorId)`, etc.
- These methods internally:
  - Resolve actor email from `actor-service` via `ActorServiceClient`
  - Map to templateId (domain knowledge in service)
  - Call internal `sendNotification()` method
- `NotificationResource` exposes both:
  - Generic: `POST /notifications` (for flexibility)
  - Domain-specific: `POST /notifications/welcome-email/{actorId}` (for convenience)
- `NotificationServiceClient` mirrors the domain-specific endpoints
- **Pros:** 
  - Follows ActorService pattern (domain logic in service layer)
  - Domain knowledge stays in notification-service
  - Flexible: clients can use generic or domain-specific APIs
  - Easy to evolve (add new domain methods without breaking clients)
- **Cons:** More API endpoints to maintain

**Option C: Event-Driven (Future)**
- Services emit domain events: `UserRegisteredEvent`, `PasswordResetRequestedEvent`
- Notification service subscribes and maps events to notifications
- **Pros:** Fully decoupled, event-driven architecture
- **Cons:** More complex, requires event infrastructure (SQS/EventBridge)

**Recommendation:** **Option B - Domain-Specific Methods in NotificationService**

This follows the existing `ActorService` pattern where:
- Domain service contains business logic (`ActorService.getProfile()`, `ActorService.getActorPartial()`)
- Resource layer exposes domain-oriented endpoints (`GET /actors/{actorId}`, `GET /actors/{actorId}/partial`)
- Client library mirrors the API (`ActorServiceClient.getActor()`, `ActorServiceClient.getActorPartial()`)

**Implementation:**

**Infrastructure Layer** (`infrastructure/provider/NotificationProviderService.java`):
```java
// Infrastructure - Generic provider wrapper
@ApplicationScoped
public class NotificationProviderService {
    @Inject NotificationProvider provider;  // SES, Twilio, etc.
    @Inject TemplateService templateService;
    @Inject NotificationRepository repository;
    
    // Generic low-level method - wraps provider classes
    public SendNotificationResponse sendNotification(SendNotificationRequest request) {
        // Resolve template, render with Qute, call provider, persist, etc.
        Template template = templateService.getTemplate(request.getTemplateId());
        String renderedContent = renderTemplate(template, request.getVariables());
        ProviderResponse providerResponse = provider.send(renderedContent, request.getRecipient());
        NotificationRecord record = persistNotification(request, providerResponse);
        return mapToResponse(record);
    }
}
```

**Domain Layer** (`domain/NotificationService.java`):
```java
// Domain Service - Business logic
@ApplicationScoped
public class NotificationService {
    @Inject ActorServiceClient actorServiceClient;
    @Inject NotificationProviderService providerService;  // Infrastructure wrapper
    
    // Domain-specific high-level methods - exposed to clients
    public SendNotificationResponse sendWelcomeEmail(String actorId) {
        ActorResponse actor = resolveActor(actorId);
        return providerService.sendNotification(SendNotificationRequest.builder()
            .templateId("welcome-email-v1")
            .recipient(Recipient.fromActor(actor))
            .variables(Map.of("firstName", actor.firstName()))
            .priority(NotificationPriority.HIGH)
            .build());
    }
    
    public SendNotificationResponse sendPasswordResetEmail(String actorId, String resetToken) {
        ActorResponse actor = resolveActor(actorId);
        return providerService.sendNotification(SendNotificationRequest.builder()
            .templateId("password-reset-email-v1")
            .recipient(Recipient.fromActor(actor))
            .variables(Map.of(
                "firstName", actor.firstName(),
                "resetLink", buildResetLink(resetToken)
            ))
            .priority(NotificationPriority.CRITICAL)
            .build());
    }
    
    private ActorResponse resolveActor(String actorId) {
        // Call actor-service to resolve actor email
        return actorServiceClient.getActorPartial(actorId);
    }
}
```

**Resource Layer** (`presentation/rest/NotificationResource.java`):
```java
// Exposes both generic and domain-specific endpoints
@Path("/notifications")
public class NotificationResource {
    @Inject NotificationService notificationService;  // Domain service
    @Inject NotificationProviderService providerService;  // For generic endpoint
    
    // Generic endpoint
    @POST
    public Response sendNotification(SendNotificationRequest request) {
        return Response.status(201)
            .entity(providerService.sendNotification(request))
            .build();
    }
    
    // Domain-specific endpoints
    @POST
    @Path("/welcome-email/{actorId}")
    public Response sendWelcomeEmail(@PathParam("actorId") String actorId) {
        return Response.status(201)
            .entity(notificationService.sendWelcomeEmail(actorId))
            .build();
    }
}
```

### API Design Decisions

1. **Resource-Based URLs**: Following ADR-0010, use `/notifications` (plural) for the resource collection
2. **HTTP Methods**: POST for creation, GET for retrieval, following REST principles
3. **Status Codes**: 201 for creation, 200 for retrieval, following ADR-0010 standards
4. **Hybrid API**: Both generic (`POST /notifications`) and domain-specific (`POST /notifications/welcome-email/{actorId}`) endpoints for flexibility and convenience
5. **Fire-and-Forget / Asynchronous Messaging**: API follows the Fire-and-Forget pattern - client receives immediate acknowledgment (`201 Created`) that notification was accepted, but does not wait for delivery. Processing happens asynchronously, decoupling the client from delivery.
6. **Eventually-Consistent**: API returns `201 Created` when notification is queued, not when delivered. Delivery is eventually-consistent - guaranteed to be delivered eventually, but not immediately
7. **Priority Support**: All notifications include priority (CRITICAL, HIGH, NORMAL, LOW) to ensure critical notifications are processed first
8. **Error Responses**: Consistent error format with `{"error": "message"}` structure
9. **Pagination**: Standard page/size pagination for list endpoints
10. **Security**: Service-to-service auth via `@AllowedServices`, public endpoints use `@PermitAll` with additional validation

---

## Domain Model

### Core Entities

#### Notification

Represents a notification request and its delivery status.

```java
public class NotificationRecord {
    private UUID notificationId;
    private NotificationChannel channel;
    private String templateId;
    private Recipient recipient;
    private Map<String, String> variables;
    private NotificationPriority priority;  // CRITICAL, HIGH, NORMAL, LOW
    private NotificationStatus status;
    private DeliveryStatus deliveryStatus;
    private String provider;
    private String providerMessageId;
    private Instant createdAt;
    private Instant sentAt;
    private Instant deliveredAt;
    private Instant failedAt;
    private String failureReason;
    private int retryCount;
    private Map<String, Object> metadata;
}
```

#### Template

Represents a notification template.

```java
public class TemplateRecord {
    private String templateId;
    private NotificationChannel channel;
    private String subject;  // For email
    private String htmlBody;
    private String textBody;
    private List<String> variables;
    private String description;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;
}
```

#### DeliveryStatus

Tracks delivery events for a notification.

```java
public class DeliveryStatusRecord {
    private UUID deliveryStatusId;
    private UUID notificationId;
    private DeliveryEvent event;  // SENT, DELIVERED, BOUNCED, COMPLAINT, FAILED
    private Instant timestamp;
    private String providerResponse;
    private Map<String, Object> providerMetadata;
}
```

#### Unsubscribe

Tracks unsubscribed recipients.

```java
public class UnsubscribeRecord {
    private UUID unsubscribeId;
    private String email;
    private String phone;  // For SMS
    private Set<NotificationChannel> unsubscribedChannels;
    private UnsubscribeReason reason;
    private Instant unsubscribedAt;
}
```

### Enums

```java
public enum NotificationChannel {
    EMAIL,
    SMS,      // Future
    PUSH      // Future
}

public enum NotificationPriority {
    CRITICAL,  // Security-critical (password resets, account lockouts)
    HIGH,      // Transactional (order confirmations, activation emails)
    NORMAL,    // Standard notifications (welcome emails, updates)
    LOW        // Marketing, newsletters
}

public enum NotificationStatus {
    QUEUED,
    SENT,
    DELIVERED,
    FAILED,
    BOUNCED,
    COMPLAINT
}

public enum DeliveryEvent {
    SENT,
    DELIVERED,
    BOUNCED,
    COMPLAINT,
    FAILED
}

public enum UnsubscribeReason {
    USER_REQUEST,
    BOUNCE,
    COMPLAINT,
    ADMIN_ACTION
}
```

---

## Provider Abstraction

### Provider Interface

```java
public interface NotificationProvider {
    String getName();
    NotificationChannel getSupportedChannel();
    SendNotificationResponse send(SendNotificationRequest request) throws ProviderException;
    boolean isAvailable();
    ProviderHealth getHealth();
}
```

### AWS SES Provider

**Primary provider for email notifications.**

**Configuration:**
- AWS Region (from `aws.properties`)
- From email address (verified in SES)
- Reply-to email address (optional)
- Configuration set (optional, for tracking)

**Features:**
- HTML and text email support
- Attachment support (future)
- SES Configuration Sets for tracking
- SNS notifications for delivery status

**LocalStack Support:**
- Uses LocalStack SES endpoint when `LOCALSTACK_ENDPOINT` is set
- Email verification required (same as real SES)
- Delivery tracking via LocalStack SES API

### LocalStack Provider (Development)

**For local development when LocalStack is available.**

- Intercepts SES calls and routes to LocalStack
- Provides email inspection via LocalStack API
- Supports same API as AWS SES provider

### Mock Provider (Testing)

**For unit tests and integration tests.**

- In-memory provider
- No actual email sending
- Tracks sent notifications for assertions

---

## Template Management

### Template Storage

**Decision Point:** Template storage location (see Decision Points)

**Selected Option: AWS DynamoDB + Versioning Metadata**

**Pattern:**
Store templates as items in a DynamoDB table with fields for:
- Template ID (PK)
- Version (SK for version history, or separate attribute)
- Channel (email/SMS/push)
- Activation status (ACTIVE/DRAFT/ARCHIVED)
- Metadata (tags, owner, last modified)
- Template body (Text content / placeholders)
- **S3 Reference:** Pointer to S3 object for large HTML bodies/assets (optional)

**Why Enterprise-Grade:**
- **Scalability:** Fully managed, horizontally scalable NoSQL
- **Security:** Fine-grained AWS IAM permissions for access control
- **Concurrency:** Supports conditional writes to prevent overwrite conflicts
- **Integration:** Integrates natively with Lambda, AppConfig, API Gateway
- **Reliability:** Backups via Point-in-Time Recovery (PITR)

**Recommendation:** **DynamoDB is the preferred choice** for high throughput, microservice architectures, and multi-region deployments. Use S3 for large assets (HTML, images).

**Option 2: PostgreSQL (Alternative)**
- Store templates in relational database
- Good for complex queries/filtering
- Simpler if DynamoDB expertise is lacking

**Option 3: S3 (Large Content)**
- Store large assets/templates in S3
- Metadata in DynamoDB referencing S3 location

### Template Engine

**Decision:** See [ADR-0018](./decisions/0018-notification-service-template-engine.md) for detailed rationale.

**Selected Option: Qute (Quarkus Native)**

**Why Qute:**
- **Native Integration:** Built-in to Quarkus, no extra dependencies
- **Performance:** Designed for high throughput, low memory footprint
- **Type Safety:** Validates templates at build time (if using typed templates) or efficient runtime rendering
- **Async Support:** Fully non-blocking, perfect for reactive applications
- **Simplicity:** Mustache-like syntax but more powerful (loops, conditionals)

**Implementation:**
- Retrieve template string from DynamoDB/S3
- Parse and cache using `Qute.fmt(templateString)`
- Render with data object map

**Option 2: Simple String Replacement**
- Good for very simple templates
- Hard to maintain for complex logic (loops, if/else)

**Recommendation:** **Use Qute**. It is the idiomatic choice for Quarkus and supports the performance goals of the platform.

### Template Variables

Templates support variable substitution using `{{variableName}}` syntax:

```
Subject: Welcome to {{platformName}}!
Body: Hello {{firstName}}, click {{activationLink}} to activate.
```

Variables are validated against template definition before sending.

---

## Delivery Tracking

### Delivery Status Flow

1. **QUEUED** - Notification created, waiting to be sent
2. **SENT** - Notification sent to provider (SES)
3. **DELIVERED** - Provider confirms delivery (via SNS webhook)
4. **BOUNCED** - Email bounced (via SNS webhook)
5. **COMPLAINT** - Recipient marked as spam (via SNS webhook)
6. **FAILED** - Provider error or retry exhaustion

### Retry Logic

**Exponential Backoff (Priority-Aware):**
- Initial retry: 
  - CRITICAL: 10 seconds
  - HIGH: 30 seconds
  - NORMAL: 1 minute
  - LOW: 5 minutes
- Max retries:
  - CRITICAL: 5 retries
  - HIGH: 3 retries
  - NORMAL: 3 retries
  - LOW: 2 retries
- Backoff multiplier: 2x
- Max delay: 
  - CRITICAL: 5 minutes
  - HIGH: 10 minutes
  - NORMAL: 30 minutes
  - LOW: 2 hours

**Retry Conditions:**
- Transient provider errors (5xx, rate limits)
- Network timeouts
- Not retried: Invalid recipient, template errors, unsubscribe

**Priority-Based Retry:**
- High-priority notifications retried more aggressively
- Low-priority notifications may be delayed during high load
- Retry queue processed in priority order (CRITICAL → HIGH → NORMAL → LOW)

### Priority-Based Processing

**Processing Order:**
Notifications are processed in priority order when system approaches capacity:

1. **CRITICAL** - Processed immediately, bypass rate limits
2. **HIGH** - Processed within seconds, higher rate limits
3. **NORMAL** - Processed within minutes, standard rate limits
4. **LOW** - Processed when capacity available, may be throttled

**Query Strategy:**
When selecting notifications to process, use priority-ordered query:
```sql
SELECT * FROM notifications 
WHERE status = 'QUEUED' 
ORDER BY 
  CASE priority 
    WHEN 'CRITICAL' THEN 1
    WHEN 'HIGH' THEN 2
    WHEN 'NORMAL' THEN 3
    WHEN 'LOW' THEN 4
  END,
  created_at ASC
LIMIT 100;
```

**Rate Limiting by Priority:**
- **CRITICAL**: No rate limiting (or very high limit, e.g., 1000/min)
- **HIGH**: High rate limit (e.g., 500/min)
- **NORMAL**: Standard rate limit (e.g., 100/min)
- **LOW**: Lower rate limit (e.g., 50/min), may be throttled further under load

**Capacity Management:**
- When system load > 80%, throttle LOW priority notifications
- When system load > 95%, pause LOW priority processing
- CRITICAL and HIGH always processed regardless of load
- NORMAL may be slightly delayed but not blocked

### Webhook Integration

**AWS SES SNS Notifications:**
- Configure SES to send SNS notifications for bounces, complaints, deliveries
- SNS topic subscribed to `/notifications/webhooks/ses` endpoint
- Validates SNS message signatures
- Updates delivery status in database

**LocalStack:**
- LocalStack SES tracks sent emails
- Can query LocalStack API for delivery status
- No SNS webhooks in LocalStack (use polling or direct API calls)

---

## Compliance Features

### Unsubscribe Management

**Features:**
- Track unsubscribed recipients per channel
- Check unsubscribe status before sending
- Unsubscribe links in all emails
- Admin interface for managing unsubscribes

**Unsubscribe Token Security:**

**Decision:** See [ADR-0019](./decisions/0019-notification-service-unsubscribe-token-security.md) for detailed rationale.

**Selected Approach: Opaque Token + Server Lookup**

**Summary:**
- Generate random opaque token (UUID or ULID) for each unsubscribe link
- Store token server-side in DynamoDB with recipient, scope, expiry, usage count, metadata
- Include token in unsubscribe URL: `/notifications/unsubscribe?token={opaque-token}`
- Validate by server-side lookup before processing

**Why Opaque Token (Not JWT or HMAC-Signed URL):**
- **Security:** Token reveals nothing if leaked (no embedded data)
- **Revocability:** Easy to revoke or rotate server-side
- **Auditability:** Track usage, IP, timestamp for compliance
- **Compliance:** Excellent audit trail for enterprise requirements
- **Flexibility:** Can evolve behavior without breaking old links
- **Single-Use:** Can enforce single-use semantics to prevent replay attacks

**Security Considerations:**
- Single-purpose scope (unsubscribe only)
- Idempotent operations (safe to click multiple times)
- Rate limiting on unsubscribe endpoint
- HTTPS only
- No PII in URLs
- Graceful success page (no error leakage)
- Do not require login

**Storage:**
- DynamoDB table for unsubscribe tokens
- PostgreSQL table for unsubscribed recipients (indexed by email/phone for fast lookup)
- TTL for old tokens (expiry-based cleanup)

### Opt-Out Tracking

**Per-Channel Opt-Out:**
- Users can opt out of specific channels (email, SMS, push)
- Global opt-out option (all channels)
- Opt-out reasons tracked for analytics

### Delivery Preferences

**Future Feature:**
- Per-user delivery preferences
- Frequency limits (max 1 email per day)
- Time-of-day preferences
- Content type preferences

---

## Infrastructure & Dependencies

### Database Schema

**PostgreSQL Tables:**

```sql
-- Notifications
CREATE TABLE notifications (
    notification_id UUID PRIMARY KEY,
    channel VARCHAR(20) NOT NULL,
    template_id VARCHAR(255) NOT NULL,
    recipient_email VARCHAR(255),
    recipient_phone VARCHAR(50),
    recipient_name VARCHAR(255),
    variables JSONB,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',  -- CRITICAL, HIGH, NORMAL, LOW
    status VARCHAR(20) NOT NULL,
    provider VARCHAR(50),
    provider_message_id VARCHAR(255),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    failed_at TIMESTAMP,
    failure_reason TEXT,
    retry_count INT DEFAULT 0
);

CREATE INDEX idx_notifications_recipient ON notifications(recipient_email);
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);
CREATE INDEX idx_notifications_priority_status ON notifications(priority, status, created_at);  -- For priority-based processing

-- Delivery Status
CREATE TABLE delivery_status (
    delivery_status_id UUID PRIMARY KEY,
    notification_id UUID NOT NULL REFERENCES notifications(notification_id),
    event VARCHAR(20) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    provider_response TEXT,
    provider_metadata JSONB
);

CREATE INDEX idx_delivery_status_notification ON delivery_status(notification_id);

-- Unsubscribes
CREATE TABLE unsubscribes (
    unsubscribe_id UUID PRIMARY KEY,
    email VARCHAR(255),
    phone VARCHAR(50),
    unsubscribed_channels TEXT[],  -- Array of channels
    reason VARCHAR(50),
    unsubscribed_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX idx_unsubscribes_email ON unsubscribes(email) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX idx_unsubscribes_phone ON unsubscribes(phone) WHERE phone IS NOT NULL;
```

### DynamoDB Tables

**Templates Table:**
- **Table Name:** `notification-templates`
- **Partition Key:** `template_id` (String)
- **Sort Key:** `version` (Number) (or use composite PK for access patterns)
- **Attributes:**
  - `channel` (String) - EMAIL, SMS, PUSH
  - `activation_status` (String) - ACTIVE, DRAFT, ARCHIVED
  - `metadata` (Map) - tags, owner, last_modified
  - `content` (Map)
    - `subject` (String)
    - `html_body` (String)
    - `text_body` (String)
    - `placeholders` (List<String>)
- **GSI:**
  - `channel-index`: PK=`channel`, SK=`template_id` (for listing by channel)

**Unsubscribe Tokens Table:**
- **Table Name:** `notification-unsubscribe-tokens`
- **Partition Key:** `token` (String) - Opaque UUID/ULID
- **Attributes:**
  - `recipient_email` (String)
  - `recipient_phone` (String)
  - `scope` (String) - Channel or "ALL"
  - `expiry` (Number) - Unix timestamp
  - `usage_count` (Number) - For single-use enforcement
  - `created_at` (Number) - Unix timestamp
  - `metadata` (Map) - IP address, user agent, etc.
- **TTL:** `expiry` attribute for automatic cleanup

### AWS Resources

**Required:**
- AWS SES (Simple Email Service)
  - Verified sender email address
  - Configuration set (optional, for tracking)
  - SNS topic for delivery notifications (optional)

**IAM Permissions:**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ses:SendEmail",
        "ses:SendRawEmail"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ses:GetIdentityVerificationAttributes"
      ],
      "Resource": "*"
    }
  ]
}
```

### Configuration

**application.properties:**
```properties
quarkus.application.name=notification-service
quarkus.http.port=${NOTIFICATION_SERVICE_PORT}

# AWS SES Configuration
notification.ses.from-email=${NOTIFICATION_SES_FROM_EMAIL}
notification.ses.reply-to=${NOTIFICATION_SES_REPLY_TO:}
notification.ses.configuration-set=${NOTIFICATION_SES_CONFIGURATION_SET:}

# Retry Configuration
notification.retry.max-attempts=3
notification.retry.initial-delay-seconds=60
notification.retry.max-delay-seconds=600
notification.retry.multiplier=2.0

# Rate Limiting (Priority-Based)
notification.rate-limit.critical.per-minute=1000
notification.rate-limit.high.per-minute=500
notification.rate-limit.normal.per-minute=100
notification.rate-limit.low.per-minute=50
notification.rate-limit.low.throttle-threshold=80  # Throttle LOW when system load > 80%
notification.rate-limit.low.pause-threshold=95    # Pause LOW when system load > 95%

# Template Configuration
notification.template.cache.enabled=true
notification.template.cache.ttl-seconds=3600
```

### Deployment Considerations

**Stateless Deployment:**
- Service instances are **fully stateless** - any instance can handle any request
- No sticky sessions or session affinity required
- Load balancer can use round-robin or least-connections (no session affinity)
- Horizontal scaling: Add/remove instances without coordination
- All state persisted in PostgreSQL (notifications, templates, delivery status)
- Provider clients (SES) are stateless - can be pooled or created per-request

**Scaling:**
- Scale horizontally by adding more service instances
- Database connection pooling handles concurrent requests
- No shared state between instances
- Stateless design enables auto-scaling based on load

**High Availability:**
- Multiple stateless instances behind load balancer
- No single point of failure (any instance can serve any request)
- Database is the only shared state (PostgreSQL with replication)

---

## LocalStack Support

### LocalStack SES Configuration

**LocalStack SES & DynamoDB Support:**
- ✅ Basic SES V1 APIs supported (send-email, verify-email-identity)
- ✅ Email tracking via LocalStack API (`/_aws/ses`)
- ✅ DynamoDB fully supported (tables, items, GSIs)
- ❌ SNS webhooks not supported (use polling or direct API)
- ❌ SMTP integration requires LocalStack Pro

**Local Development Setup:**

1. **Start LocalStack:**
   ```bash
   docker-compose up localstack
   ```

2. **Verify Email (required for sending):**
   ```bash
   awslocal ses verify-email-identity --email hello@example.com
   ```

3. **Configure Service:**
   ```properties
   aws.ses.endpoint=${LOCALSTACK_ENDPOINT:http://localhost:4566}
   ```

4. **Inspect Sent Emails:**
   ```bash
   curl http://localhost:4566/_aws/ses
   ```

**Decision Point:** Use LocalStack Community (mocked) or LocalStack Pro (real SMTP)?

**Recommendation:** Start with LocalStack Community for development. Consider LocalStack Pro if real email delivery is needed for testing.

### MailHog Extension (Optional)

**For better email inspection during development:**

- LocalStack MailHog extension provides web UI
- View sent emails in browser
- Useful for template testing

**Setup:** (if using MailHog extension)
```yaml
# docker-compose.yml
services:
  localstack:
    environment:
      - SERVICES=ses
      - EXTRA_CORS_ALLOWED_ORIGINS=http://localhost:8025
```

---

## Decision Points

### 1. Template Storage Location ⚠️

**Options:**
- **A) AWS DynamoDB (Preferred)** - Scalable, serverless, versioning support, conditional writes
- **B) PostgreSQL** - Relational, complex queries
- **C) S3** - Large content storage

**Recommendation:** **DynamoDB is the preferred choice** for enterprise-grade scalability, security, and integration capabilities.

**Decision Required:** Confirmed DynamoDB as storage backend.

---

### 2. Template Engine ⚠️

**Options:**
- **A) Qute (Preferred)** - Quarkus native, fast, async, type-safe
- **B) Simple String Replacement** - Custom implementation
- **C) Mustache/Handlebars** - Standard libraries

**Recommendation:** **Qute is the preferred choice**. It is the native templating engine for Quarkus, offering superior performance, async support, and developer experience.

**Decision Required:** Confirmed Qute as template engine.

---

### 3. LocalStack vs Real SES for Local Development ⚠️

**Options:**
- **A) LocalStack Community** - Mocked SES, no real emails
- **B) LocalStack Pro** - Real SMTP, costs money
- **C) Real AWS SES (Sandbox)** - Real emails, free tier, requires AWS account

**Recommendation:** **LocalStack Community for MVP**, consider LocalStack Pro or real SES sandbox if real email delivery needed for testing.

**Decision Required:** Confirmed LocalStack Community for local development.

---

### 4. Delivery Status Webhooks (LocalStack) ⚠️

**Problem:** LocalStack doesn't support SNS webhooks for SES delivery status.

**Options:**
- **A) Polling** - Poll LocalStack API for delivery status
- **B) Direct API Calls** - Query LocalStack SES API after send
- **C) Mock Webhooks** - Simulate webhook calls in tests
- **D) Skip LocalStack Webhooks** - Only support webhooks in AWS

**Recommendation:** **Polling or Direct API for LocalStack**, real SNS webhooks for AWS.

**Decision Required:** Confirmed either Polling or Direct API for LocalStack (we can likeely defer which once the use-cases are clearer, if we have already confirmed LocalStack).

---

### 5. SMS Provider (Future) ⚠️

**Options:**
- **A) AWS SNS SMS** - Native AWS, simple integration
- **B) Twilio** - More features, better deliverability
- **C) Both** - Provider abstraction supports multiple

**Recommendation:** **Defer decision** - Implement email first, add SMS later.

**Decision Required:** Confirm once SMS is implemented.

---

### 6. Push Notification Provider (Future) ⚠️

**Options:**
- **A) AWS SNS Mobile Push** - Native AWS
- **B) Firebase Cloud Messaging (FCM)** - Google's solution
- **C) Apple Push Notification Service (APNS)** - Apple's solution
- **D) Both FCM and APNS** - Provider abstraction supports multiple

**Recommendation:** **Defer decision** - Implement email first, add push later.

**Decision Required:** Confirm once push notifications are implemented.

---

### 7. Rate Limiting Strategy ⚠️

**Decision:** See [ADR-0016](./decisions/0016-notification-service-rate-limiting-strategy.md) for detailed rationale and implementation.

**Selected Approach: Per-Provider Rate Limiting with Priority-Aware Throttling**

**Summary:**
- Each provider (AWS SES, Twilio, etc.) has its own rate limiter
- Provider-specific limits respect external service constraints
- Priority-aware: CRITICAL/HIGH bypass or have higher limits, NORMAL/LOW respect provider limits
- Custom rate limiter in `infrastructure/provider/` (not using existing `libs/security` framework)

**Decision Required:** Confirmed per-provider rate limiting as the strategy.

---

### 8. Async vs Sync Sending ⚠️

**Current:** Synchronous REST API (per ADR-0008)

**Future:** SQS integration for async/batch sends

**Recommendation:** **Start with synchronous API**, design for async-ready (message-oriented boundaries).

**Decision Required:** Confirm once implementing SQS integration (defer to Phase 2).

---

### 9. Template Versioning Strategy ⚠️

**Options:**
- **A) Immutable Templates** - New version = new template ID
- **B) Versioned Templates** - Same template ID, version number
- **C) No Versioning** - Update in place

**Recommendation:** **Immutable templates for MVP** (simpler), add versioning later if needed.

**Decision Required:** Confirm immutable templates and add versioning later if needed.

---

### 10. Multi-Language Support (Future) ⚠️

**Options:**
- **A) Per-Template Locales** - `welcome-email-en`, `welcome-email-es`
- **B) Template with Locale Variable** - Single template, locale in variables
- **C) Translation Service** - External translation, single template

**Recommendation:** **Defer decision** - Implement single language first, add multi-language later.

**Decision Required:** Confirm defer until multi-language is needed.

---

## Implementation Phases

### Phase 1: MVP (Email Only) 🎯

**Goal:** Basic email notification service with AWS SES.

**Features:**
- ✅ Email sending via AWS SES
- ✅ Template management (DynamoDB)
- ✅ Template rendering (Qute)
- ✅ Delivery status tracking (basic)
- ✅ Unsubscribe management
- ✅ Priority-based processing (CRITICAL, HIGH, NORMAL, LOW)
- ✅ Priority-aware retry logic
- ✅ Eventually-consistent design (no immediate delivery guarantee)
- ✅ LocalStack support (mocked SES, DynamoDB)
- ✅ REST API (synchronous, eventually-consistent)
- ✅ Service-to-service authentication
- ✅ Health checks
- ✅ Metrics

**Timeline:** 2-3 weeks

---

### Phase 2: Enhanced Tracking & Retries

**Goal:** Production-ready delivery tracking and retry logic.

**Features:**
- ✅ SES SNS webhook integration
- ✅ Retry logic with exponential backoff
- ✅ Delivery status events
- ✅ Provider health monitoring
- ✅ Enhanced metrics and dashboards

**Timeline:** 1-2 weeks

---

### Phase 3: Advanced Features

**Goal:** Additional channels and advanced features.

**Features:**
- ✅ SMS support (AWS SNS or Twilio)
- ✅ Push notification support (FCM/APNS)
- ✅ Template versioning
- ✅ Scheduled notifications
- ✅ Batch sending
- ✅ SQS integration (async)

**Timeline:** 4-6 weeks

---

### Phase 4: Enterprise Features

**Goal:** Enterprise-grade features.

**Features:**
- ✅ Multi-language templates
- ✅ A/B testing
- ✅ Analytics and reporting
- ✅ Delivery preferences
- ✅ Per-tenant rate limiting
- ✅ Template marketplace (future)

**Timeline:** TBD

---

## Open Questions

1. **Template Management UI:** Do we need a UI for managing templates, or is API-only sufficient for MVP? We will eventually need an admin UI but for now, either API or 'seed' related script (similar to localstack-seed.sh or postgres-seed.sh etc) is fine.

2. **Email Attachments:** Do we need attachment support in MVP? No. Phase X.

3. **Email BCC/CC:** Do we need BCC/CC support? No.  Phase X.

4. **SES Sandbox Limits:** AWS SES sandbox has sending limits. Do we need production SES access for MVP? No.

5. **Template Validation:** How strict should template variable validation be? Fail on missing variables or use defaults? Fail on entities i.e. actorId, templateId etc. We may be able to fallback to defaults, but right now, use-cases are entirely hypothetical, so remaining flexible would be best.

6. **Delivery Status Retention:** How long should we retain delivery status records? I think we must retain forever with the option to migrate to low-cost S3 storage (Glacier etc). Again, this is hypothetical but enterprise clients would expect this. 

7. **Provider Failover:** Do we need multi-provider failover in MVP, or single provider sufficient? No. It's eventually consistent so I doubt we will ever implement this. 

9. **Cost Tracking:** Do we need per-notification cost tracking for billing? No.

10. **Compliance Reporting:** What compliance reports are needed (GDPR, CAN-SPAM)? Unknown. Let's leave this as a potential feature for Phase X.

---

## Next Steps

1. **Review and Approve Design** - Review this specification with the team
2. **Resolve Decision Points** - Make decisions on all ⚠️ marked items
3. **Create ADR** - Convert this design into an ADR (ADR-0015)
4. **Set Up Infrastructure** - AWS SES setup, database schema
5. **Implement Phase 1** - MVP implementation
6. **Testing** - Integration tests with LocalStack
7. **Documentation** - API documentation, deployment guide

---

## References

### Architecture Decision Records (ADRs)

- [ADR-0008: REST vs SQS](./decisions/0008-decoupling-micro-services-transitioning-from-rest-to-sqs.md)
- [ADR-0010: REST API Design Standards](./decisions/0010-rest-api-design-standards.md)
- [ADR-0011: Stateless JWT Authentication](./decisions/0011-stateless-jwt-authentication.md)
- [ADR-0012: Clean Architecture Package Structure](./decisions/0012-clean-architecture-package-structure.md)
- [ADR-0015: Notification Service Fire-and-Forget / Asynchronous Messaging Pattern](./decisions/0015-notification-service-fire-and-forget-pattern.md)
- [ADR-0016: Notification Service Rate Limiting Strategy](./decisions/0016-notification-service-rate-limiting-strategy.md)
- [ADR-0017: Notification Service Template Storage](./decisions/0017-notification-service-template-implementation.md)
- [ADR-0018: Notification Service Template Engine Selection](./decisions/0018-notification-service-template-engine.md)
- [ADR-0019: Notification Service Unsubscribe Token Security](./decisions/0019-notification-service-unsubscribe-token-security.md)

### Other References

- [Commercial Platform Services](./COMMERCIAL_PLATFORM_SERVICES.md)
- [AWS SES Documentation](https://docs.aws.amazon.com/ses/)
- [LocalStack SES Documentation](https://docs.localstack.cloud/aws/services/ses)

---

**Document Owner:** Architecture Team  
**Last Updated:** 2026-01-23  
**Status:** Design Phase - Pending Review
