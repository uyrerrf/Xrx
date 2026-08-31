package com.xrc.system.core;

import android.util.Base64;
import android.util.Log;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoManager {
    private static final String TAG = Constants.TAG + ":Crypto";
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String RSA = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    private final SecureRandom random;
    private KeyPair rsaKeyPair;

    static {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    public CryptoManager() {
        this.random = new SecureRandom();
        generateRSAKeys();
    }

    private void generateRSAKeys() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048, random);
            rsaKeyPair = kpg.generateKeyPair();
        } catch (Exception e) {
            Log.e(TAG, "RSA gen failed", e);
        }
    }

    public String getRSAPublicKey() {
        if (rsaKeyPair == null) return "";
        return Base64.encodeToString(rsaKeyPair.getPublic().getEncoded(), Base64.NO_WRAP);
    }

    public byte[] encryptRSA(byte[] data, String pubKeyB64) {
        try {
            byte[] keyBytes = Base64.decode(pubKeyB64, Base64.NO_WRAP);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey pubKey = kf.generatePublic(spec);
            Cipher cipher = Cipher.getInstance(RSA);
            cipher.init(Cipher.ENCRYPT_MODE, pubKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            Log.e(TAG, "RSA encrypt failed", e);
            return null;
        }
    }

    public byte[] decryptRSA(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(RSA);
            cipher.init(Cipher.DECRYPT_MODE, rsaKeyPair.getPrivate());
            return cipher.doFinal(data);
        } catch (Exception e) {
            Log.e(TAG, "RSA decrypt failed", e);
            return null;
        }
    }

    public String encryptAES(String plaintext, String keyB64) {
        try {
            byte[] keyBytes = Base64.decode(keyB64, Base64.NO_WRAP);
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "AES encrypt failed", e);
            return null;
        }
    }

    public String decryptAES(String cipherB64, String keyB64) {
        try {
            byte[] combined = Base64.decode(cipherB64, Base64.NO_WRAP);
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);
            byte[] keyBytes = Base64.decode(keyB64, Base64.NO_WRAP);
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance(AES_GCM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "AES decrypt failed", e);
            return null;
        }
    }

    public String generateAESKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(AES_KEY_SIZE, random);
            SecretKey key = kg.generateKey();
            return Base64.encodeToString(key.getEncoded(), Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "AES key gen failed", e);
            return null;
        }
    }

    public String hashSHA256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA256 failed", e);
            return "";
        }
    }

    public String hmacSHA256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec spec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(spec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            Log.e(TAG, "HMAC failed", e);
            return "";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }
}
