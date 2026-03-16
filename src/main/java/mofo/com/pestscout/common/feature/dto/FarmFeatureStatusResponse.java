package mofo.com.pestscout.common.feature.dto;

public record FarmFeatureStatusResponse(
        String key,
        String displayName,
        boolean globalEnabled,
        boolean tierAllowed,
        Boolean overrideEnabled,
        boolean effectiveEnabled
) {
}
