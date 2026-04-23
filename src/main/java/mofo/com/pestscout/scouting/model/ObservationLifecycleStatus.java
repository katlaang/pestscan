package mofo.com.pestscout.scouting.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * High-level observation lifecycle used for field workflow and later review.
 * This is separate from transport-level sync status.
 */
@Schema(description = "Observation workflow state used for field capture, sync, analysis, escalation, and closure.")
public enum ObservationLifecycleStatus {
    DRAFT,
    CAPTURED_OFFLINE,
    SYNCED,
    ANALYZED,
    ESCALATED,
    CLOSED
}
