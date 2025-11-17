# Heatmap-related changes in latest work

The following files were updated to address heatmap behaviour and scope:

- `src/main/java/mofo/com/pestscout/farm/controller/HeatmapController.java` – added logging and ensured requests delegate to the service for access enforcement.
- `src/main/java/mofo/com/pestscout/farm/service/HeatmapService.java` – reworked weekly aggregation, section building, and severity legend handling while enforcing farm view access.
- `src/main/java/mofo/com/pestscout/farm/dto/HeatmapResponse.java` – includes the severity legend alongside per-section payloads.
- `src/main/java/mofo/com/pestscout/farm/dto/HeatmapSectionResponse.java` – new section DTO capturing target identifiers, structure metadata, and grid cells.
- `src/main/java/mofo/com/pestscout/farm/model/SeverityLevel.java` – codifies the Green→Dark Red severity scale with ranges and hex colours used by the heatmap legend.
