# Frontend implementation guide

This document distills what the PestScout backend expects from the web/mobile clients. Use it as a backlog for UI/UX work and as a contract for how the frontend should call the APIs.

## Authentication & session management
- Endpoints: `POST /api/auth/login`, `POST /api/auth/register`, `POST /api/auth/refresh`, `GET /api/auth/me`.
- Store both **access** and **refresh** tokens; attach the access token as `Authorization: Bearer <token>` on every API call.
- After login/refresh, hydrate the current user (`/api/auth/me`) to know the caller's role (super admin, farm admin/manager, scout) and gate UI accordingly.

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
- **Status transitions**: `/{id}/start`, `/{id}/complete` (requires confirmation flag), `/{id}/reopen` for completed sessions.
- **Observations** (scout + super admin):
  - Single upsert: `POST /api/scouting/sessions/{id}/observations`
  - Bulk upsert: `POST /api/scouting/sessions/{id}/observations/bulk` (payload `sessionId` must match path)
  - Delete: `DELETE /api/scouting/sessions/{id}/observations/{observationId}`
- **Sync** for offline clients: `GET /api/scouting/sessions/sync?farmId={id}&since=<ISO>&includeDeleted=<bool>` returns session and observation deltas since a timestamp.
- Enforce target membership in the UI: when `includeAllBays/Benches` is false, limit observation editing to the allowed `bayTags`/`benchTags`; backend will reject mismatches.

## Discounts & billing context
- Discount math is percentage-based: `applyDiscount(amount, quotaDiscountPercentage)` caps discounts between 0–100% and rounds to two decimals.
- The same calculation determines effective licensed hectares, so show discounted values wherever you present licensed capacity or billing summaries.

## UX recommendations
- Role-aware navigation: hide license edit controls from non–super admins; hide pricing/billing panels from scouts.
- Provide inline validation mirroring backend rules (required tags when include flags are false; non-negative hectares/discounts; mutually exclusive greenhouse/field block IDs).
- Keep Swagger UI (`/swagger-ui.html`) available in dev to explore the full schemas as you build forms and clients.
