package mofo.com.pestscout.farm.dto;

import java.math.BigDecimal;

public final class LongitudeDeserializer extends DirectionalCoordinateDeserializer {

    @Override
    protected BigDecimal parse(String rawValue) {
        return CoordinateFormatSupport.parseLongitude(rawValue);
    }

    @Override
    protected BigDecimal validate(BigDecimal rawValue) {
        return CoordinateFormatSupport.validateLongitude(rawValue);
    }
}
