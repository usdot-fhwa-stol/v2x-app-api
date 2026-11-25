package usdot.v2x.app.api.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import usdot.v2x.app.api.config.crypto.CryptoProperties;
import usdot.v2x.app.api.crypto.AesGcmEncryptor;

class EncryptedStringConverterTest {

    private final EncryptedStringConverter converter = new EncryptedStringConverter(encryptor());

    private static AesGcmEncryptor encryptor() {
        CryptoProperties properties = new CryptoProperties();
        properties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        return new AesGcmEncryptor(properties);
    }

    @Test
    void storesCiphertextAndReadsPlaintext() {
        String column = converter.convertToDatabaseColumn("mqtt-password");

        assertNotEquals("mqtt-password", column);
        assertEquals("mqtt-password", converter.convertToEntityAttribute(column));
    }

    @Test
    void passesNullThrough() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }
}
