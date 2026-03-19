package mofo.com.pestscout.farm.dto;

import java.math.BigDecimal;

public final class LatitudeDeserializer extends DirectionalCoordinateDeserializer {

    @Override
    protected BigDecimal parse(String rawValue) {
        return CoordinateFormatSupport.parseLatitude(rawValue);
    }

    @Override
    protected BigDecimal validate(BigDecimal rawValue) {
        return CoordinateFormatSupport.validateLatitude(rawValue);
    }
}
