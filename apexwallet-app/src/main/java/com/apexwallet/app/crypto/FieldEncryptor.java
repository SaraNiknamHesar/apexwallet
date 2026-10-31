package com.apexwallet.app.crypto;

import com.apexwallet.app.config.ApexProperties;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AES-GCM with a random nonce per value, plus an HMAC so an exact account number can be looked up
 * without storing the plaintext. The lookup hash is not reversible, but equal numbers share a hash.
 */
public final class FieldEncryptor {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final Map<String, SecretKey> keys;
    private final String activeKeyId;
    private final SecretKey hmacKey;
    private final SecureRandom random = new SecureRandom();

    public FieldEncryptor(Map<String, SecretKey> keys, String activeKeyId, SecretKey hmacKey) {
        if (!keys.containsKey(activeKeyId)) {
            throw new IllegalArgumentException("active key id is missing");
        }
        this.keys = Map.copyOf(keys);
        this.activeKeyId = activeKeyId;
        this.hmacKey = hmacKey;
    }

    public static FieldEncryptor fromProperties(ApexProperties properties) {
        Map<String, SecretKey> keys = new LinkedHashMap<>();
        properties.getDataKeys().forEach((id, base64) -> keys.put(id, aesKey(Base64.getDecoder().decode(base64))));
        byte[] hmac = properties.getHmacKey() == null || properties.getHmacKey().isBlank()
                ? randomBytes(32)
                : Base64.getDecoder().decode(properties.getHmacKey());
        if (keys.isEmpty()) {
            keys.put(properties.getActiveKeyId(), aesKey(randomBytes(32)));
        }
        return new FieldEncryptor(keys, properties.getActiveKeyId(), new SecretKeySpec(hmac, "HmacSHA256"));
    }

    public EncryptedValue encrypt(String plaintext) {
        try {
            byte[] nonce = randomBytes(NONCE_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeKeyId), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] body = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new EncryptedValue(b64(body), b64(nonce), activeKeyId);
        } catch (Exception ex) {
            throw new IllegalStateException("encryption failed", ex);
        }
    }

    public String decrypt(EncryptedValue value) {
        SecretKey key = keys.get(value.keyId());
        if (key == null) {
            throw new IllegalArgumentException("unknown key id " + value.keyId());
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, Base64.getDecoder().decode(value.nonce())));
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(value.ciphertext()));
            return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("decryption failed", ex);
        }
    }

    public String hash(String plaintext) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return java.util.HexFormat.of().formatHex(mac.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("hmac failed", ex);
        }
    }

    public FieldEncryptor withAdditionalKey(String keyId, SecretKey key, boolean activate) {
        Map<String, SecretKey> copy = new LinkedHashMap<>(keys);
        copy.put(keyId, key);
        return new FieldEncryptor(copy, activate ? keyId : activeKeyId, hmacKey);
    }

    private static SecretKey aesKey(byte[] bytes) {
        return new SecretKeySpec(bytes, "AES");
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
