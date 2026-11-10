package usdot.v2x.app.api.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

import usdot.v2x.app.api.crypto.AesGcmEncryptor;

/**
 * JPA converter that stores a String attribute encrypted with AES-256-GCM.
 * Apply it to secret columns with
 * {@code @Convert(converter = EncryptedStringConverter.class)}. Spring Boot's
 * Hibernate bean container supplies the {@link AesGcmEncryptor} dependency.
 */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final AesGcmEncryptor encryptor;

    public EncryptedStringConverter(AesGcmEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryptor.decrypt(dbData);
    }
}
