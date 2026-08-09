package com.debthunter.output;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Computes HMAC-SHA256 signatures over a baseline artefact's canonical bytes. Shared by {@link
 * BaselineWriter} (signing) and the policy module's baseline resolver (verification), so both sides
 * always compute the signature over identical input.
 */
public final class BaselineSigner {

  private static final String ALGORITHM = "HmacSHA256";

  /**
   * Signs {@code payload} with {@code key}.
   *
   * @param payload the canonical bytes to sign (typically the artefact's unsigned JSON form)
   * @param key the signing key
   * @return a lowercase hex-encoded HMAC-SHA256 digest
   */
  public String sign(String payload, String key) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM));
      byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(ALGORITHM + " not available", e);
    }
  }
}
