package com.debthunter.integration;

import com.debthunter.domain.ScanResult;

/** Publishes a completed scan result somewhere external, independent of how that's done. */
public interface ResultUploader {

  /**
   * Publishes {@code result} to {@code config}'s endpoint.
   *
   * @param result the scan result to publish
   * @param config where and how to publish it
   * @return the outcome of this single attempt
   */
  PublishResult publish(ScanResult result, PublishConfig config);
}
