package com.debthunter.policy;

import com.debthunter.domain.ScanResult;
import java.util.Objects;

/**
 * The outcome of {@link BaselineResolver#resolve}: either a usable baseline, an unusable one found
 * but rejected (wrong tool version, bad signature, unreadable), or nothing found at all.
 */
public record BaselineResolution(
    BaselineProvenance provenance, ScanResult baseline, String incompatibilityReason) {

  /**
   * Validates required fields. {@code baseline} and {@code incompatibilityReason} are mutually
   * exclusive.
   */
  public BaselineResolution {
    Objects.requireNonNull(provenance, "provenance");
  }

  /**
   * No baseline was found anywhere in the resolution chain.
   *
   * @return a resolution with {@link BaselineProvenance#NONE} and no baseline
   */
  public static BaselineResolution none() {
    return new BaselineResolution(BaselineProvenance.NONE, null, null);
  }

  /**
   * A baseline was found and is usable.
   *
   * @param provenance where it came from
   * @param baseline the resolved baseline's scan result
   * @return a resolution carrying the usable baseline
   */
  public static BaselineResolution resolved(BaselineProvenance provenance, ScanResult baseline) {
    return new BaselineResolution(provenance, baseline, null);
  }

  /**
   * A baseline was found but cannot be used.
   *
   * @param provenance where it was found
   * @param reason why it was rejected
   * @return a resolution with no usable baseline
   */
  public static BaselineResolution incompatible(BaselineProvenance provenance, String reason) {
    return new BaselineResolution(provenance, null, reason);
  }

  /**
   * Whether a baseline was found but rejected.
   *
   * @return {@code true} if {@link #incompatibilityReason()} is present
   */
  public boolean isIncompatible() {
    return incompatibilityReason != null;
  }
}
