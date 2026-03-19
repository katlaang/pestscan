package mofo.com.pestscout.scouting.dto;

public record ScoutingSectionCoverageDto(
        Integer coveredBayCount,
        Integer totalBayCount,
        Integer coveredBedCount,
        Integer totalBedCount,
        boolean fullyCovered
) {
}
