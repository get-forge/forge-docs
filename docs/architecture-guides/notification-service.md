---
title: "Notification service"
summary: "Centralized notification delivery: email (AWS SES), templates, delivery tracking, unsubscribe handling,"
---

Centralized notification delivery: email (AWS SES), templates, delivery tracking, unsubscribe handling,
and processing with retries. The API is fire-and-forget (acceptance is not final delivery).
See
[`services/notification-service/README.md`](https://github.com/get-forge/forge-platform/blob/main/services/notification-service/README.md)
for module entry points.

## SES SNS webhook (spec)

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

- Start LocalStack; the notification service registers the configured from-address (default `noreply@<domainRoot>`) in dev,
or run `awslocal ses verify-email-identity` for a custom `notification.ses.from-email`.
  Set `aws.ses.endpoint` to LocalStack.
  Inspect sent emails: `curl http://localhost:4566/_aws/ses`.
  SNS webhooks not supported; use polling or no-op for webhook endpoint.

---

## References

- ADRs:
  - [0015](/docs/0015-notification-service-fire-and-forget-pattern) (fire-and-forget)
  - [0016](/docs/0016-notification-service-rate-limiting-strategy) (rate limiting)
  - [0017](/docs/0017-notification-service-template-implementation) (templates)
  - [0018](/docs/0018-notification-service-template-engine) (Qute)
  - [0019](/docs/0019-notification-service-unsubscribe-token-security) (unsubscribe tokens)
- [AWS SES](https://docs.aws.amazon.com/ses/) · [LocalStack SES](https://docs.localstack.cloud/aws/services/ses)
