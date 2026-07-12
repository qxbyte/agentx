package com.agentx.infra.ai.crypto;

import com.agentx.common.api.ErrorCode;
import com.agentx.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Component
public class ApiKeyCrypto {
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyCrypto(@Value("${agentx.crypto.master-key:}") String masterKeyB64) {
        if (masterKeyB64 == null || masterKeyB64.isBlank()) {
            log.warn("AGENTX_MASTER_KEY 未设置，使用随机密钥（重启后已存密文不可解，仅限开发环境）");
            try {
                KeyGenerator kg = KeyGenerator.getInstance("AES");
                kg.init(256);
                this.key = kg.generateKey();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        } else {
            this.key = new SecretKeySpec(Base64.getDecoder().decode(masterKeyB64), "AES");
        }
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "密钥加密失败");
        }
    }

    public String decrypt(String encB64) {
        try {
            byte[] all = Base64.getDecoder().decode(encB64);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
            byte[] plain = cipher.doFinal(all, IV_LEN, all.length - IV_LEN);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "密钥解密失败");
        }
    }
}
