package mofo.com.pestscout.farm.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import mofo.com.pestscout.common.exception.BadRequestException;

import java.io.IOException;
import java.math.BigDecimal;

abstract class DirectionalCoordinateDeserializer extends JsonDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();

        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
            try {
                return validate(parser.getDecimalValue());
            } catch (BadRequestException ex) {
                throw InvalidFormatException.from(parser, ex.getMessage(), parser.getDecimalValue(), BigDecimal.class);
            }
        }
        if (token == JsonToken.VALUE_STRING) {
            String rawValue = parser.getValueAsString();
            if (rawValue == null || rawValue.isBlank()) {
                return null;
            }
            try {
                return parse(rawValue);
            } catch (BadRequestException ex) {
                throw InvalidFormatException.from(parser, ex.getMessage(), rawValue, BigDecimal.class);
            }
        }

        throw InvalidFormatException.from(
                parser,
                "Coordinate must be a decimal number or a directional string.",
                parser.getText(),
                BigDecimal.class
        );
    }

    protected abstract BigDecimal parse(String rawValue);

    protected abstract BigDecimal validate(BigDecimal rawValue);
}
