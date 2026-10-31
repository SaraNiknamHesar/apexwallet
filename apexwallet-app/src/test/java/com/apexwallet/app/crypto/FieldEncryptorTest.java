package com.apexwallet.app.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FieldEncryptorTest {

    @Test
    void ciphertextHidesTheNumberAndSurvivesKeyRotation() throws Exception {
        SecretKey first = aes();
        SecretKey hmac = aes();
        FieldEncryptor encryptor = new FieldEncryptor(Map.of("v1", first), "v1", hmac);
        EncryptedValue encrypted = encryptor.encrypt("GB82WEST12345698765432");
        assertNotEquals("GB82WEST12345698765432", encrypted.ciphertext());
        assertEquals("v1", encrypted.keyId());
        assertEquals("GB82WEST12345698765432", encryptor.decrypt(encrypted));
        assertNotEquals(encrypted.nonce(), encryptor.encrypt("GB82WEST12345698765432").nonce());
        assertEquals(encryptor.hash("GB82WEST12345698765432"), encryptor.hash("GB82WEST12345698765432"));

        FieldEncryptor rotated = encryptor.withAdditionalKey("v2", aes(), true);
        EncryptedValue fresh = rotated.encrypt("DE89370400440532013000");
        assertEquals("v2", fresh.keyId());
        assertEquals("GB82WEST12345698765432", rotated.decrypt(encrypted));
        assertEquals("DE89370400440532013000", rotated.decrypt(fresh));
    }

    private static SecretKey aes() throws NoSuchAlgorithmException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }
}
