package com.debthunter.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Computes a stable, content-anchored fingerprint for a finding from its rule and location identity
 * alone, deliberately excluding volatile evidence (revision counts, scores, messages) so that
 * reformatting a file, or renaming it, does not change a finding's identity across scans.
 */
public final class Fingerprinter {

  /**
   * Separates components before hashing. A NUL byte cannot appear in a rule id, file path, or
   * symbol name, so no combination of component values can collide across a boundary.
   */
  private static final String SEPARATOR = "\u0000";

  /**
   * Computes a SHA-256 fingerprint from a finding's rule and location identity.
   *
   * @param ruleId the rule that produced the finding
   * @param normalisedPath the finding's canonical path identity, stable across renames
   * @param symbolAnchor the enclosing symbol's identity, or {@code null} if none is derivable
   * @param normalisedEvidenceKey additional identity-bearing evidence (e.g. a coupled path); must
   *     exclude volatile magnitudes such as counts or scores
   * @return a 64-character lowercase hex SHA-256 digest
   */
  public String fingerprint(
      String ruleId, String normalisedPath, String symbolAnchor, String normalisedEvidenceKey) {
    Objects.requireNonNull(ruleId, "ruleId");
    Objects.requireNonNull(normalisedPath, "normalisedPath");
    Objects.requireNonNull(normalisedEvidenceKey, "normalisedEvidenceKey");
    String anchor = symbolAnchor == null ? "" : symbolAnchor;
    String input = String.join(SEPARATOR, ruleId, normalisedPath, anchor, normalisedEvidenceKey);
    return sha256Hex(input);
  }

  private String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
