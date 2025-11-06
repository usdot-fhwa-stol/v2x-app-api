package usdot.v2x.app.api.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter for PostgreSQL JSONB columns
 * Converts between String and JSONB for database storage
 */
@Converter
@Component
public class JsonbAttributeConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        // For JSONB, we need to ensure the string is properly formatted JSON
        if (attribute == null) {
            return null;
        }

        // PostgreSQL JSONB expects a valid JSON string
        // The string should already be valid JSON from our GeoJSON processing
        return attribute;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        // Convert from database JSONB to String
        return dbData;
    }
}
