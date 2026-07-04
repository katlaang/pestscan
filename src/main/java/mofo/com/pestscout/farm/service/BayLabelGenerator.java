package mofo.com.pestscout.farm.service;

import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.farm.dto.BayNumberingMode;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

final class BayLabelGenerator {

    private BayLabelGenerator() {
    }

    static List<String> generate(BayNumberingMode requestedMode, String firstIdentifier, int count) {
        if (count <= 0) {
            return List.of();
        }

        String first = normalizeFirstIdentifier(firstIdentifier);
        BayNumberingMode mode = requestedMode != null ? requestedMode : inferMode(first);

        if (mode == BayNumberingMode.ALPHABETIC) {
            int start = parseAlphabeticStart(first);
            return IntStream.range(0, count)
                    .mapToObj(offset -> toAlphabeticLabel(start + offset))
                    .toList();
        }

        int start = parseNumericStart(first);
        return IntStream.range(0, count)
                .mapToObj(offset -> String.valueOf(start + offset))
                .toList();
    }

    private static String normalizeFirstIdentifier(String firstIdentifier) {
        if (firstIdentifier == null || firstIdentifier.isBlank()) {
            return null;
        }

        String trimmed = firstIdentifier.trim();
        if (trimmed.regionMatches(true, 0, "Bay ", 0, 4)) {
            trimmed = trimmed.substring(4).trim();
        }
        return trimmed.isBlank() ? null : trimmed;
    }

    private static BayNumberingMode inferMode(String firstIdentifier) {
        if (firstIdentifier == null) {
            return BayNumberingMode.NUMERIC;
        }
        if (firstIdentifier.matches("\\d+")) {
            return BayNumberingMode.NUMERIC;
        }
        if (firstIdentifier.matches("[A-Za-z]+")) {
            return BayNumberingMode.ALPHABETIC;
        }
        throw new BadRequestException("First bay identifier must be numeric or alphabetic.");
    }

    private static int parseNumericStart(String firstIdentifier) {
        String first = firstIdentifier == null ? "1" : firstIdentifier;
        if (!first.matches("\\d+")) {
            throw new BadRequestException("First bay identifier must be numeric for numeric bay numbering.");
        }
        int start = Integer.parseInt(first);
        if (start < 1) {
            throw new BadRequestException("First bay identifier must be at least 1.");
        }
        return start;
    }

    private static int parseAlphabeticStart(String firstIdentifier) {
        String first = firstIdentifier == null ? "A" : firstIdentifier.toUpperCase(Locale.ROOT);
        if (!first.matches("[A-Z]+")) {
            throw new BadRequestException("First bay identifier must be alphabetic for alphabetic bay numbering.");
        }

        int value = 0;
        for (int index = 0; index < first.length(); index++) {
            value = value * 26 + (first.charAt(index) - 'A' + 1);
        }
        return value;
    }

    private static String toAlphabeticLabel(int value) {
        StringBuilder label = new StringBuilder();
        int current = value;
        while (current > 0) {
            current--;
            label.insert(0, (char) ('A' + current % 26));
            current /= 26;
        }
        return label.toString();
    }
}
