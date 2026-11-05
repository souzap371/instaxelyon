package br.com.elyon.audit;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
class MetaTokenCipher {
    private static final SecureRandom RANDOM = new SecureRandom();

    boolean configured() { return key() != null; }

    String encrypt(String value) {
        try {
            byte[] key = key();
            if (key == null) throw new IllegalStateException("Chave de criptografia dos tokens Meta não configurada.");
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (IllegalStateException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalStateException("Não foi possível proteger o token Meta."); }
    }

    String decrypt(String value) {
        try {
            byte[] key = key();
            if (key == null) throw new IllegalStateException("Chave de criptografia dos tokens Meta não configurada.");
            byte[] input = Base64.getDecoder().decode(value);
            byte[] iv = java.util.Arrays.copyOfRange(input, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(input, 12, input.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalStateException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalStateException("Não foi possível abrir a conexão Meta armazenada."); }
    }

    private byte[] key() {
        String encoded = System.getenv("META_TOKEN_ENCRYPTION_KEY");
        if (encoded == null || encoded.isBlank()) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            return decoded.length == 32 ? decoded : null;
        } catch (IllegalArgumentException ex) { return null; }
    }
}
