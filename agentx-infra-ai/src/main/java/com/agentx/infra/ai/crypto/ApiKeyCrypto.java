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
import java.nio.file.Files;
import java.nio.file.Path;
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
            // 开发环境兜底：首启生成密钥并持久化到 ~/.agentx/master.key，
            // 此后每次启动复用——避免"每次重启随机密钥→已存模型 API Key 全部解不开"。
            // 生产环境仍应显式配置 AGENTX_MASTER_KEY。
            this.key = loadOrCreateLocalKey();
            log.warn("AGENTX_MASTER_KEY 未设置，使用本地持久化开发密钥 ~/.agentx/master.key（生产环境请显式配置）");
        } else {
            this.key = new SecretKeySpec(Base64.getDecoder().decode(masterKeyB64), "AES");
        }
    }

    private static SecretKey loadOrCreateLocalKey() {
        Path keyFile = Path.of(System.getProperty("user.home"), ".agentx", "master.key");
        try {
            if (Files.exists(keyFile)) {
                byte[] raw = Base64.getDecoder().decode(Files.readString(keyFile).strip());
                return new SecretKeySpec(raw, "AES");
            }
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(256);
            SecretKey generated = kg.generateKey();
            Files.createDirectories(keyFile.getParent());
            Files.writeString(keyFile, Base64.getEncoder().encodeToString(generated.getEncoded()));
            return generated;
        } catch (Exception e) {
            throw new IllegalStateException("本地开发密钥初始化失败: " + keyFile, e);
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
