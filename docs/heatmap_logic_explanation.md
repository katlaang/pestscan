# Heatmap logic vs. earlier implementation

## Why the latest code differs

- **Farm overview separated from per-section grids.** The current `HeatmapResponse` exposes a farm-level grid (using farm defaults) plus per-section grids for each target, matching the "Option C" requirement that the UI render both an aggregated overview and distinct greenhouse/field block sections.
- **Session targets drive sectioning.** `HeatmapService` aggregates observations by their `ScoutingSessionTarget`, so data from multiple greenhouses/field blocks inside one session never mixes. Each target becomes a section with its own bay/bench dimensions.
- **Consistent severity legend.** The response now emits a single `List<SeverityLegendEntry>` derived from `SeverityLevel.orderedLevels()`, eliminating the prior duplicate legend fields and supplying the UI with the Green→Dark Red scale once.
- **ISO-week scoping and access control.** Heatmaps fetch sessions within the requested ISO week boundaries and call `farmAccessService.requireViewAccess(farm)` before producing any data, aligning with the role rules (super admin, owner/manager, or assigned scout).
- **Graceful empty-state handling.** If no sessions exist for the week, the service returns metadata plus an empty grid/section list rather than failing, so the UI can still render the legend and headings.

## How this addresses goal

- Supports multiple structures per session through `ScoutingSessionTarget` and per-target aggregation.
- Keeps farm-wide totals visible while preserving section-specific detail for greenhouse/field block dropdowns.
- Provides a single, unambiguous legend that matches the severity scale requirements.
- Ensures only authorized users can view heatmaps for a farm.
