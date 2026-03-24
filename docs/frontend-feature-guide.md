# Frontend implementation guide

This document distills what the PestScout backend expects from the web/mobile clients. Use it as a backlog for UI/UX work and as a contract for how the frontend should call the APIs.

## Authentication & session management
- Endpoints: `POST /api/auth/login`, `POST /api/auth/register`, `POST /api/auth/refresh`, `GET /api/auth/me`.
- Store both **access** and **refresh** tokens; attach the access token as `Authorization: Bearer <token>` on every API call.
- Persist `clientSessionId` from login/refresh responses in `sessionStorage` and send it as `X-Client-Session-Id` on
  authenticated requests when available.
- After login/refresh, hydrate the current user (`/api/auth/me`) to know the caller's role (super admin, farm admin/manager, scout) and gate UI accordingly.
- On app bootstrap, if auth hydration/refresh fails with a `401` session error (`SESSION_INVALID`, `SESSION_EXPIRED`,
  `SESSION_REPLACED`, or similar), clear stored auth state and redirect to the login screen instead of rendering a
  protected page error.
- The authenticated user payload includes `passwordExpiresAt`, `passwordExpired`, `passwordExpiryWarningRequired`,
  `passwordExpiryWarningDaysRemaining`, and `passwordExpiryWarningMessage`.
- Show `passwordExpiryWarningMessage` immediately after login when it is present.
- Treat `passwordChangeRequired=true` as a forced password-change state for both temporary-password users and users
  whose
  90-day password has expired.

## Password reset flow

- Request a reset with `POST /api/auth/forgot-password` using `{ "email": "user@example.com" }`.
- Complete a reset with `POST /api/auth/reset-password`.
- Voluntary password changes use `POST /api/auth/change-password` with the authenticated user's current password.
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
  - When the caller is already authenticated and is being forced to replace a temporary or expired password, the same
    endpoint accepts the password payload without any reset token.
- Temporary-password logins are limited to a 5-minute password-change session. While that flag is active, non-auth
  endpoints are rejected by the backend until the password is changed.
- Expired-password logins follow the same restricted 5-minute password-change session. The user can log in, but only
  auth/reset endpoints are available until the password is changed.
- After a successful forced password change, the current JWT session is invalidated immediately. The user must log in
  again with the new password.
- Authenticated voluntary change payload:

```json
{
  "currentPassword": "<current-password>",
  "newPassword": "<new-password>"
}
```
- Phone-assisted resets still require `verificationChannel: "PHONE_CALL"` and the extra verification fields already
  defined by the API.
- Password policy enforced by the backend:
  - Passwords expire after 90 days.
  - A new password cannot match the previous six passwords.
  - A new password cannot contain the user's first or last name.
- Frontend expectations:
  - Parse the `token` from the reset page URL and include it in the API request for email-link resets.
  - For authenticated forced-change screens, post the new password only and show a success message such as
    `Password updated. It is valid for 90 days.`
  - After that success message, send the user back to the login screen; the old temporary-password session will no
    longer be accepted by the backend.
  - Keep confirm-password validation client-side; the backend only validates the final password value.
  - For user-initiated password updates before expiry, call `POST /api/auth/change-password`.
  - When the backend returns `400`, show `message` and the first item in `details` instead of a generic
    `Request validation failed` banner.

## User profile & user management

- Endpoints already exposed by the backend:
  - `GET /api/auth/me`
  - `GET /api/auth/users/{userId}`
  - `PUT /api/auth/users/{userId}`
  - `GET /api/auth/users?farmId={farmId}`
  - `GET /api/auth/users/role/{role}?farmId={farmId}`
- Frontend rules:
  - `SCOUT` should only have a self-profile screen.
  - `FARM_ADMIN` and `MANAGER` can view users attached to their own farm.
  - On the self-profile screen, render first name, last name, email, role, and farm as read-only.
  - Only `phoneNumber` should be editable for non-super-admin users on their own profile.
  - Non-super-admin users can change their own password from the authenticated password-change flow.
  - Super admin can see the global users directory and edit all user fields.
  - For any scouting-session scout picker, use `GET /api/auth/users/role/SCOUT?farmId={farmId}`.
  - Do not populate the scouting-session assignee picker from `GET /api/auth/users`, because that returns all farm
    users.
- Current backend gap:
  - The backend currently does not fully enforce the "phone-only for self, full edit for super admin only" rule yet.
    Build the frontend to this rule, but the server still needs a follow-up lock-down if you want it guaranteed.

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

- **Create**: `POST /api/scouting/sessions` (admins/managers).
  - If the UI allows explicit assignment, the assignee picker must contain `SCOUT` users only.
  - Use `GET /api/auth/users/role/SCOUT?farmId={farmId}` for that picker.
  - The backend rejects non-scout assignees and scouts from a different farm.
  - The client should prefill session time fields from the device: local date, local time, and IANA timezone.
  - New optional payload field: `observationTimezone` (example: `Africa/Nairobi`, `America/Chicago`).
  - If `observationTime` or `observationTimezone` is omitted, the backend now falls back to `farm.timezone` and then the
    server timezone.
- **Update metadata**: `PUT /api/scouting/sessions/{sessionId}` (admins/managers). Status must not be `COMPLETED` unless reopened.
  - Scouts may also update `observationTime` and `observationTimezone` while the session is active.
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
  - `SCOUT` sees assigned sessions only.
  - `SCOUT` should not see `DRAFT`.
  - `SCOUT` can see assigned sessions in `NEW`, `IN_PROGRESS`, `SUBMITTED`, `REOPENED`, `INCOMPLETE`, and `COMPLETED`.
  - `SCOUT` is the only role that should see data-entry actions for observations, photos, submit, and complete.
  - `SUPER_ADMIN` must first select a farm on the dashboard before loading sessions for that farm.
  - `SUPER_ADMIN` should only surface the per-farm planning and post-work states: `DRAFT`, `NEW`, `COMPLETED`,
    `INCOMPLETE`, and `REOPENED`.
  - `FARM_ADMIN` and `MANAGER` can view sessions and farm data for their own farms only.
  - `FARM_ADMIN` and `MANAGER` must not get cross-farm analytics, sessions, or farm values.
  - `FARM_ADMIN` and `MANAGER` can see `IN_PROGRESS` in the list, but the row should be greyed out and not openable.
  - `FARM_ADMIN` and `MANAGER` can create new scouting sessions but cannot start them.
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

## Latest backend additions

- Multi-farm memberships:
  - `MANAGER` and `FARM_ADMIN` users can now belong to more than one farm.
  - Build the farm switcher and farm dashboard cards from `GET /api/farms`.
  - Do not assume a single farm on login/bootstrap for manager/admin users.
- Dashboard overview:
  - New endpoint: `GET /api/analytics/dashboard/overview`
  - Response contains:
    - `farmCount`
    - `farms[]` with `farmId`, `farmTag`, `farmName`, `licenseExpiryDate`, `daysUntilLicenseExpiry`, `accessLocked`
    - `licenseAlerts[]` with `farmId`, `farmName`, `licenseExpiryDate`, `daysUntilExpiry`, `status`
  - Use this for the manager/admin landing dashboard that shows all attached farms and expiring-license alerts.
- Farm coordinates and layout preview:
  - Farm create/update already accepts `latitude` and `longitude`.
  - New preview endpoint: `POST /api/farms/layout/preview`
  - Request:
    ```json
    {
      "latitude": "43.123456° N",
      "longitude": "80.123456° W",
      "greenhouseCount": 4,
      "greenhouseNames": ["House 1", "House 2", "House 3", "House 4"]
    }
    ```
  - Use the preview response polygons to render the generated farm layout before the farm is saved.
- Session list behavior:
  - `GET /api/scouting/sessions?farmId={farmId}` still lists one farm.
  - `GET /api/scouting/sessions` without `farmId` is now valid for `SUPER_ADMIN` and returns sessions across all farms.
  - Session list payloads now include:
    - `farmName`
    - `weekYear`
    - `weekKey` (example: `2026-W12`)
    - `openRestricted`
  - For `SUPER_ADMIN`, `FARM_ADMIN`, and `MANAGER`, `IN_PROGRESS` sessions stay in the list but come back with
    `openRestricted: true`; render those rows/cards greyed out and block navigation into the detail page.
- Session remark / hotspot photos:
  - `POST /api/scouting/photos/register` still supports observation/cell photos.
  - It now also supports session-level remark photos by omitting `observationId`, `sessionTargetId`, `bayIndex`,
    `benchIndex`, and `spotIndex`.
  - Use this for hotspot/problem photos captured in the session remarks flow instead of forcing one photo per
    observation.
  - Suggested payload:
    ```json
    {
      "sessionId": "<session-id>",
      "localPhotoId": "remark-photo-1",
      "purpose": "Hotspot in remarks",
      "capturedAt": "2026-03-23T10:15:00"
    }
    ```
- Session time defaults:
  - Session payloads now include `observationTimezone`.
  - On create/edit, initialize the timezone picker from `Intl.DateTimeFormat().resolvedOptions().timeZone`.
  - Initialize the time picker from the device clock.
  - Leave both fields editable so the scout can override them before saving.
- Analytics week/year and greenhouse weekly series:
  - `GET /api/analytics/trend/weekly?farmId={farmId}` now returns week labels like `2026-W12` and includes
    `weekNumber` + `year`.
  - `GET /api/analytics/trend/severity?farmId={farmId}` now returns the same week-year metadata.
  - New endpoint: `GET /api/analytics/trend/greenhouse-weekly?farmId={farmId}&year={year}&species={speciesCode}`
  - Use the greenhouse-weekly endpoint to plot greenhouse lines/bars by ISO week for a selected pest.
  - `pestDistribution[]` and `diseaseDistribution[]` now expose `count` as an alias for `value`; use `count` in UI
    labels when showing totals by pest/disease.
- Current frontend expectations from these additions:
  - Manager/admin dashboard:
    - load `GET /api/analytics/dashboard/overview`
    - show one card per farm from `farms[]`
    - show alert banners or tiles from `licenseAlerts[]`
  - Super admin session board:
    - call `GET /api/scouting/sessions` for the all-farm board
    - allow optional farm filtering client-side or by switching to `GET /api/scouting/sessions?farmId=...`
    - grey out any item with `openRestricted: true`
  - Analytics:
    - use `weekKey` / `year` / `weekNumber` for x-axis labels instead of raw `W{n}`
    - use `GET /api/analytics/trend/greenhouse-weekly` for greenhouse-by-week charts
    - use `pestDistribution[].count` for pest-count displays

## Copy Canvas

```md
Frontend canvas

1. Authentication and password changes

- After `POST /api/auth/login`, call `GET /api/auth/me`.
- If `passwordExpiryWarningRequired === true`, show this popup immediately after login:
  `Your password is nearing expiry. Please change your password in the next ${passwordExpiryWarningDaysRemaining} days`
- If `passwordChangeRequired === true`, open the reset/change-password screen immediately.
- For forced password change, call `POST /api/auth/reset-password` with only the new password when the user is already
  authenticated.
- For voluntary password change before expiry, call `POST /api/auth/change-password`.
- After password change succeeds, show:
  `Password updated. It is valid for 90 days.`
- After that success message, redirect to the login page and require the user to sign in again with email + new
  password.

2. Session assignment

- In create/edit session screens, the scout picker must show scouts only.
- Fetch picker options from:
  `GET /api/auth/users/role/SCOUT?farmId={farmId}`
- Do not use:
  `GET /api/auth/users`
- Label the field as `Assigned Scout`.
- Do not render managers, farm admins, or super admins in the assignment dropdown.

3. Session visibility by role

- `SCOUT`
  - Show assigned sessions only.
  - Hide `DRAFT`.
  - Show assigned sessions in `NEW`, `IN_PROGRESS`, `SUBMITTED`, `REOPENED`, `INCOMPLETE`, and `COMPLETED`.
  - Show all data-entry controls only to scout:
    - start
    - accept remote start
    - observations
    - scouting photos
    - submit
    - complete
- `SUPER_ADMIN`
  - Require farm selection first on the dashboard.
  - After farm selection, load sessions for that farm only.
  - Show only: `DRAFT`, `NEW`, `COMPLETED`, `INCOMPLETE`, `REOPENED`.
  - Show `Request Remote Start`.
  - Show `Reopen Session`.
- `FARM_ADMIN` and `MANAGER`
  - Load sessions and analytics for their own farms only.
  - Do not show cross-farm selectors or cross-farm totals.
  - Do not show scout data-entry controls.
  - Show `IN_PROGRESS` as a greyed-out row/card.
  - Do not allow opening `IN_PROGRESS`.
  - Allow create/view/reopen actions for the other visible session states.
  - Do not allow `Start Session`.

4. Remote start

- If `remoteStartConsentRequired === true` for the assigned scout, flash a banner on the scout session screen.
- Banner text must be exactly:
  `remoted accessed session start`
- Show an `Accept` button that calls:
  `POST /api/scouting/sessions/{id}/accept-remote-start`
- Keep the normal scout `Start Session` button visible as well.
- If the scout ignores the remote-start request and starts normally, call:
  `POST /api/scouting/sessions/{id}/start`

5. Completion warning

- Before calling `POST /api/scouting/sessions/{id}/complete`, show a blocking modal:
  `Are you sure you want to complete this scouting session? You will not be able to edit it afterward.`
- Only call complete after the scout confirms.

6. Reopen audit trail

- After reopen, load:
  `GET /api/scouting/sessions/{id}/audits`
- Highlight `SESSION_REOPENED`.
- Show:
  - actor name
  - actor role
  - timestamp
  - comment

7. Profile screens

- Scout profile:
  - Show first name as read-only.
  - Show last name as read-only.
  - Show email as read-only.
  - Show role as read-only.
  - Show farm as read-only.
  - Allow editing `phoneNumber` only.
- Farm admin and manager:
  - Show own profile.
  - Allow change password.
  - Show users attached to their own farm.
  - Do not show users from other farms.
- Super admin user management:
  - Show all users.
  - Allow edit of all fields.

8. Analytics and farm scoping

- `SUPER_ADMIN` can view dashboards across farms, but must choose the farm context before loading farm-specific
  sessions.
- `FARM_ADMIN` and `MANAGER` can only view analytics, sessions, and related farm values for their own farms.
- `SCOUT` should not get analytics dashboards.
```
