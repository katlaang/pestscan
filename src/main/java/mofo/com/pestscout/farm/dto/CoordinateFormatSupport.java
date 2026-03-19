package mofo.com.pestscout.farm.dto;

import mofo.com.pestscout.common.exception.BadRequestException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats farm coordinates in either signed-decimal or cardinal-direction form.
 */
public final class CoordinateFormatSupport {

    private static final Pattern COORDINATE_PATTERN = Pattern.compile(
            "^([+-]?\\d+(?:\\.\\d+)?)\\s*(?:°)?\\s*([A-Za-z])?$"
    );

    private CoordinateFormatSupport() {
    }

    public static BigDecimal parseLatitude(String rawValue) {
        return parse(rawValue, CoordinateAxis.LATITUDE);
    }

    public static BigDecimal parseLongitude(String rawValue) {
        return parse(rawValue, CoordinateAxis.LONGITUDE);
    }

    public static BigDecimal validateLatitude(BigDecimal value) {
        return validate(value, CoordinateAxis.LATITUDE);
    }

    public static BigDecimal validateLongitude(BigDecimal value) {
        return validate(value, CoordinateAxis.LONGITUDE);
    }

    public static String formatLatitude(BigDecimal value) {
        return format(value, CoordinateAxis.LATITUDE);
    }

    public static String formatLongitude(BigDecimal value) {
        return format(value, CoordinateAxis.LONGITUDE);
    }

    private static BigDecimal parse(String rawValue, CoordinateAxis axis) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        Matcher matcher = COORDINATE_PATTERN.matcher(rawValue.trim());
        if (!matcher.matches()) {
            throw new BadRequestException(axis.label + " must be a decimal like -104.0204 or a directional value like 104.0204° " + axis.negativeCardinal + ".");
        }

        String numericToken = matcher.group(1);
        BigDecimal numericValue;
        try {
            numericValue = new BigDecimal(numericToken);
        } catch (NumberFormatException ex) {
            throw new BadRequestException(axis.label + " must contain a valid decimal number.");
        }

        String cardinalGroup = matcher.group(2);
        if (cardinalGroup == null || cardinalGroup.isBlank()) {
            return validate(numericValue, axis);
        }

        String cardinal = cardinalGroup.toUpperCase(Locale.ROOT);
        if (!axis.isAllowedCardinal(cardinal)) {
            throw new BadRequestException(axis.label + " only accepts " + axis.positiveCardinal + " or " + axis.negativeCardinal + " direction suffixes.");
        }

        int expectedSign = axis.isNegativeCardinal(cardinal) ? -1 : 1;
        if (hasConflictingExplicitSign(numericToken, expectedSign)) {
            throw new BadRequestException(axis.label + " sign conflicts with the " + cardinal + " direction suffix.");
        }

        BigDecimal signedValue = numericValue.abs();
        if (expectedSign < 0) {
            signedValue = signedValue.negate();
        }
        return validate(signedValue, axis);
    }

    private static boolean hasConflictingExplicitSign(String numericToken, int expectedSign) {
        if (numericToken == null || numericToken.isBlank()) {
            return false;
        }
        if (numericToken.startsWith("-")) {
            return expectedSign > 0;
        }
        if (numericToken.startsWith("+")) {
            return expectedSign < 0;
        }
        return false;
    }

    private static BigDecimal validate(BigDecimal value, CoordinateAxis axis) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(axis.minimum) < 0 || value.compareTo(axis.maximum) > 0) {
            throw new BadRequestException(axis.label + " must be between " + axis.minimum.toPlainString() + " and " + axis.maximum.toPlainString() + ".");
        }
        return value;
    }

    private static String format(BigDecimal value, CoordinateAxis axis) {
        if (value == null) {
            return null;
        }

        BigDecimal normalized = validate(value, axis);
        String magnitude = normalized.abs().stripTrailingZeros().toPlainString();
        String cardinal = normalized.signum() < 0 ? axis.negativeCardinal : axis.positiveCardinal;
        return magnitude + "° " + cardinal;
    }

    private enum CoordinateAxis {
        LATITUDE("latitude", new BigDecimal("-90"), new BigDecimal("90"), "N", "S"),
        LONGITUDE("longitude", new BigDecimal("-180"), new BigDecimal("180"), "E", "W");

        private final String label;
        private final BigDecimal minimum;
        private final BigDecimal maximum;
        private final String positiveCardinal;
        private final String negativeCardinal;

        CoordinateAxis(
                String label,
                BigDecimal minimum,
                BigDecimal maximum,
                String positiveCardinal,
                String negativeCardinal
        ) {
            this.label = label;
            this.minimum = minimum;
            this.maximum = maximum;
            this.positiveCardinal = positiveCardinal;
            this.negativeCardinal = negativeCardinal;
        }

        private boolean isAllowedCardinal(String cardinal) {
            return positiveCardinal.equals(cardinal) || negativeCardinal.equals(cardinal);
        }

        private boolean isNegativeCardinal(String cardinal) {
            return negativeCardinal.equals(cardinal);
        }
    }
}
