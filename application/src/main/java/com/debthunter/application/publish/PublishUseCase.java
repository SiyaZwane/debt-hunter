package com.debthunter.application.publish;

import com.debthunter.domain.ScanResult;
import com.debthunter.integration.PublishConfig;
import com.debthunter.integration.PublishResult;
import com.debthunter.integration.ResultUploader;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optionally publishes a completed scan result. Publication never affects a scan's verdict: a
 * failure here is logged as a warning and reported back in the summary, nothing more — it cannot
 * change the exit code the scan already decided on.
 */
public final class PublishUseCase {

  private static final Logger log = LoggerFactory.getLogger(PublishUseCase.class);

  private final ResultUploader uploader;

  /**
   * Creates the use case.
   *
   * @param uploader publishes the result when configured and not offline
   */
  public PublishUseCase(ResultUploader uploader) {
    this.uploader = Objects.requireNonNull(uploader, "uploader");
  }

  /**
   * Publishes {@code result} per {@code config}, unless {@code offline} is set or {@code config} is
   * {@code null}.
   *
   * @param result the completed scan result to publish
   * @param config where and how to publish it, or {@code null} if publication isn't configured
   * @param offline whether the scan was run with {@code --offline}
   * @return what happened
   */
  public PublishSummary publish(ScanResult result, PublishConfig config, boolean offline) {
    if (offline) {
      return PublishSummary.offlineSkipped();
    }
    if (config == null) {
      return PublishSummary.notConfigured();
    }

    PublishResult publishResult = uploader.publish(result, config);
    if (publishResult.success()) {
      return PublishSummary.published();
    }
    log.warn("Failed to publish scan result: {}", publishResult.reason());
    return PublishSummary.failed(publishResult.reason());
  }
}
