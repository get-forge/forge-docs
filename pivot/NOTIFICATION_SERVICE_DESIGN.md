# Notification Service

**Status:** Phase 1 & 2 complete (SES SNS webhook remaining). See [Remaining Work (Todo)](#remaining-work-todo).

Centralized notification delivery: email (SES), templates (DynamoDB + Qute), delivery tracking,
unsubscribe, priority-based processing and retries.
Fire-and-forget API (201 = accepted, not delivered).
LocalStack for local dev.
**Code is the source of truth**; this doc only covers implementation status and specs needed for
future features.

---

## Implementation Status

- **Phase 1 (MVP – Email):** Complete. See git history.
- **Phase 2 (Tracking & Retries):** Complete except SES SNS webhook. See git history.

---

## Remaining Work (Todo)

1. **SES SNS webhook** – See [implementation spec](#ses-sns-webhook--implementation-spec) below.
2. **SMS** – AWS SNS or Twilio (Phase 3).
3. **Push** – FCM/APNS (Phase 3).
4. **Template versioning, scheduled notifications, batch sending, SQS integration** (Phase 3).
5. **Multi-language, A/B testing, analytics, delivery preferences, per-tenant rate limiting, template marketplace** (Phase 4).

---

## SES SNS Webhook – Implementation Spec

**Endpoint:** **POST** `/notifications/webhooks/ses` — raw SNS JSON body.
Return **200 OK** for all valid requests (including subscription confirmations and unknown message IDs).
Security: `@PermitAll`; authenticity via SNS signature verification only.

**1. SNS message handling**

- **Type = SubscriptionConfirmation** — HTTP GET `SubscribeURL` to confirm. No DB changes. Return 200.
- **Type = UnsubscribeConfirmation** — Optional GET UnsubscribeURL or ignore. Return 200.
- **Type = Notification** — Verify signature (cert from `SigningCertURL`, canonical string per AWS docs).
  Reject 4xx if invalid.
  Parse `Message` as JSON (SES event).
  Extract `mail.messageId`, `notificationType` (Bounce | Complaint | Delivery).
  If not Bounce/Complaint/Delivery, return 200 and skip.

**2. Linking to our notification**

- Add **NotificationRepository** `findByProviderMessageId(String)` (and index on
  `notifications.provider_message_id` if needed).
  Look up by `mail.messageId`.
  If not found, return 200 and skip (idempotent).

**3. Persisting outcome**

- Map: **Delivery** → `DeliveryEvent.DELIVERED`, `NotificationStatus.DELIVERED`; **Bounce** →
  `DeliveryEvent.BOUNCED`, `NotificationStatus.BOUNCED`; **Complaint** →
  `DeliveryEvent.COMPLAINT`, `NotificationStatus.COMPLAINT`.
  Insert **DeliveryStatusRecord**; update **NotificationRecord.status**.
  Idempotent where possible (e.g. same messageId + event type).

**4. Parsing & DTOs**

- Outer SNS envelope: `Type`, `Message`, `MessageId`, `SigningCertURL`, `SubscribeURL`, etc.
  Inner SES event: `notificationType`, `mail.messageId`, `bounce` / `complaint` / `delivery`.
  Parse `Message` as JSON; handle missing fields safely.

**5. Signature verification**

- Fetch cert from `SigningCertURL` (HTTPS only).
  Build canonical string per AWS SNS docs; verify signature.
  4xx on failure.

**6. Config / AWS**

- SES Configuration Set → SNS Topic → HTTPS subscription to this endpoint.
  Optional config to disable processing (e.g. LocalStack); endpoint still returns 200.

**7. LocalStack**

- No SNS webhooks in LocalStack. Unknown `messageId` or disabled config → 200, no DB update.

**8. Testing**

- Unit: signature valid/invalid, parse Bounce/Complaint/Delivery, map to domain,
  "notification not found".
  Integration: POST sample SNS payloads (SubscriptionConfirmation + Notification),
  assert 200 and DB updates where applicable

---

## LocalStack (minimal)

- Start LocalStack; the notification service registers the configured from-address (default `noreply@<domain.rootZone>`) in dev,
or run `awslocal ses verify-email-identity` for a custom `notification.ses.from-email`.
  Set `aws.ses.endpoint` to LocalStack.
  Inspect sent emails: `curl http://localhost:4566/_aws/ses`.
  SNS webhooks not supported; use polling or no-op for webhook endpoint.

---

## References

- ADRs:
  - [0015](../architecture/decisions/0015-notification-service-fire-and-forget-pattern.md)
    (fire-and-forget)
  - [0016](../architecture/decisions/0016-notification-service-rate-limiting-strategy.md)
    (rate limiting)
  - [0017](../architecture/decisions/0017-notification-service-template-implementation.md)
    (templates)
  - [0018](../architecture/decisions/0018-notification-service-template-engine.md) (Qute)
  - [0019](../architecture/decisions/0019-notification-service-unsubscribe-token-security.md)
    (unsubscribe tokens)
- [AWS SES](https://docs.aws.amazon.com/ses/) · [LocalStack SES](https://docs.localstack.cloud/aws/services/ses)
