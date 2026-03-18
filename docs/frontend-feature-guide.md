# Frontend implementation guide

This document distills what the PestScout backend expects from the web/mobile clients. Use it as a backlog for UI/UX work and as a contract for how the frontend should call the APIs.

## Authentication & session management
- Endpoints: `POST /api/auth/login`, `POST /api/auth/register`, `POST /api/auth/refresh`, `GET /api/auth/me`.
- Store both **access** and **refresh** tokens; attach the access token as `Authorization: Bearer <token>` on every API call.
- After login/refresh, hydrate the current user (`/api/auth/me`) to know the caller's role (super admin, farm admin/manager, scout) and gate UI accordingly.
- The authenticated user payload includes `passwordExpiresAt` and `passwordExpired`; use those fields to warn the user
  before expiry and to force a reset flow when login or refresh is rejected.

## Password reset flow

- Request a reset with `POST /api/auth/forgot-password` using `{ "email": "user@example.com" }`.
- Complete a reset with `POST /api/auth/reset-password`.
- Preferred reset payload:

```json
{
  "token": "<reset-token>",
  "password": "<new-password>",
  "verificationChannel": "EMAIL"
}
```

- Compatibility behavior also accepted by the backend:
  - `newPassword` is accepted as an alias for `password`.
  - `resetToken` is accepted as an alias for `token`.
  - `verificationChannel` may be omitted for normal email resets and will default to `EMAIL`.
  - The token may be sent as `POST /api/auth/reset-password?token=<reset-token>` when the body omits it.
- Phone-assisted resets still require `verificationChannel: "PHONE_CALL"` and the extra verification fields already
  defined by the API.
- Password policy enforced by the backend:
  - Passwords expire after 90 days.
  - A new password cannot match the previous six passwords.
  - A new password cannot contain the user's first or last name.
- Frontend expectations:
  - Parse the `token` from the reset page URL and include it in the API request.
  - Keep confirm-password validation client-side; the backend only validates the final password value.
  - When the backend returns `400`, show `message` and the first item in `details` instead of a generic
    `Request validation failed` banner.

## Farm management & licensing
- **List/view farms**: `GET /api/farms` and `GET /api/farms/{farmId}`. Scout role receives licensing fields as `null`; managers/admins get full data including license status flags.
- **Create farm** (super admin only): `POST /api/farms` with licensing, billing, and structural defaults (bay/bench/spot-check counts).
- **Update farm** (super admin + managers): `PUT /api/farms/{farmId}`.
  - Managers can edit general/contact/default structure fields.
  - **Super admins additionally control license & billing:**
    - Subscription fields: `subscriptionStatus`, `subscriptionTier`, `billingEmail`.
    - Licensed capacity: `licensedAreaHectares`, `licensedUnitQuota`, `quotaDiscountPercentage` (0–100%), `licenseExpiryDate`, `licenseGracePeriodEnd`, `licenseArchivedDate`, `autoRenewEnabled`, `isArchived`.
    - Location overrides: `latitude`, `longitude`.
  - Show/allow clearing (send `null`) for optional license fields so archives/discounts can be removed.
- Surface the farm's derived license flag (`accessLocked`) to hint when the backend considers the farm expired, in grace, or archived for non-scout roles.

## Licensing rules that affect scouting
- Before creating a session the backend enforces:
  - Farm must not be archived or beyond grace/expiry.
- Requested area must be **within licensed hectares after discount**: effective area = `licensedAreaHectares * (1 - quotaDiscountPercentage/100)` floored at zero.
- Session request targets declare which bays/benches are covered:
  - Each target points to either a `greenhouseId` **or** a `fieldBlockId` (not both).
  - `includeAllBays`/`includeAllBenches` default to `true`; if `false`, `bayTags`/`benchTags` are required.
  - The backend computes requested area as the count of selected bays (all bays or `bayTags` length). If this exceeds the effective licensed area, the request fails with `Requested scouting area exceeds licensed hectares.`
- Frontend implications:
  - Pre-compute selected bays based on greenhouse/field metadata to warn users before submission.
  - Surface license status in session creation UI (expiry, grace, archived) and disable submit when obviously invalid.
  - Preserve the backend error text to keep behavior consistent when the server rejects overage or archived farms.

## Scouting session lifecycle
- **Create**: `POST /api/scouting/sessions` (admins/managers). Scout is assigned from the farm; client should not pass a scout ID.
- **Update metadata**: `PUT /api/scouting/sessions/{sessionId}` (admins/managers). Status must not be `COMPLETED` unless reopened.
- **Status transitions**:
  - `POST /{id}/start` is only for the assigned scout.
  - `POST /{id}/remote-start-request` is super-admin only. It does not start the session; it creates a pending consent
    request for the assigned scout.
  - `POST /{id}/accept-remote-start` is scout-only. The scout may also ignore the request and use the normal
    `POST /{id}/start` action instead.
  - `POST /{id}/submit` is scout-only.
  - `POST /{id}/complete` is scout-only and requires confirmation before completion.
  - `POST /{id}/reopen` is admin-only and is only valid for completed sessions.
- **Observations** (scout only):
  - Single upsert: `POST /api/scouting/sessions/{id}/observations`
  - Bulk upsert: `POST /api/scouting/sessions/{id}/observations/bulk` (payload `sessionId` must match path)
  - Delete: `DELETE /api/scouting/sessions/{id}/observations/{observationId}`
- **Photos** (scout only):
  - Register metadata: `POST /api/scouting/photos/register`
  - Confirm upload: `POST /api/scouting/photos/confirm`
- **Sync** for offline clients: `GET /api/scouting/sessions/sync?farmId={id}&since=<ISO>&includeDeleted=<bool>` returns session and observation deltas since a timestamp.
- Enforce target membership in the UI: when `includeAllBays/Benches` is false, limit observation editing to the allowed `bayTags`/`benchTags`; backend will reject mismatches.
- Remote-start consent fields are included on every session payload:
  - `remoteStartConsentRequired`
  - `remoteStartRequestedAt`
  - `remoteStartRequestedByName`
- Frontend consent flow:
  - If the current user is the assigned scout and `remoteStartConsentRequired` is `true`, flash a blocking consent
    banner across the session list/detail screen.
  - Use the exact banner copy requested by product: `remoted accessed session start`.
  - The accept CTA should call `POST /api/scouting/sessions/{id}/accept-remote-start` with at least
    `{ "version": <session.version> }`.
  - Keep the normal scout `Start Session` action available as an alternative; calling
    `POST /api/scouting/sessions/{id}/start` should clear the pending remote-start request.
  - Keep the banner visible until the scout accepts, starts normally, or sync returns
    `remoteStartConsentRequired: false`.
- Role-based visibility:
  - `SCOUT` sees assigned sessions and can still edit `INCOMPLETE` sessions.
  - `SUPER_ADMIN` must first select a farm on the dashboard before loading sessions for that farm.
  - `SUPER_ADMIN` should only surface the per-farm planning and post-work states: `DRAFT`, `NEW`, `COMPLETED`,
    `INCOMPLETE`, and `REOPENED`.
  - `FARM_ADMIN` and `MANAGER` can view all sessions created on their farm, including `IN_PROGRESS`, `SUBMITTED`, and
    `REOPENED`.
- Reopen UX:
  - Reopen is for admin roles after a session is completed.
  - Show the reopen actor in the audit trail UI by reading `GET /api/scouting/sessions/{id}/audits` and highlighting the
    `SESSION_REOPENED` event.
- Completion UX:
  - Before calling `POST /api/scouting/sessions/{id}/complete`, show a blocking warning modal that the session will
    become non-editable once completed.
  - Send `confirmationAcknowledged: true` only after the scout explicitly confirms in that modal.

## Discounts & billing context
- Discount math is percentage-based: `applyDiscount(amount, quotaDiscountPercentage)` caps discounts between 0–100% and rounds to two decimals.
- The same calculation determines effective licensed hectares, so show discounted values wherever you present licensed capacity or billing summaries.

## UX recommendations
- Role-aware navigation: hide license edit controls from non–super admins; hide pricing/billing panels from scouts.
- Provide inline validation mirroring backend rules (required tags when include flags are false; non-negative hectares/discounts; mutually exclusive greenhouse/field block IDs).
- Keep Swagger UI (`/swagger-ui.html`) available in dev to explore the full schemas as you build forms and clients.
