package com.debthunter.testkit;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;

/**
 * Runs a scan against a repository and returns its report as JSON, however that scan is actually
 * performed — in-process, via the built jar, or via a container image. {@link ConformanceRunner}
 * depends only on this abstraction, not on any scanning implementation, so this module stays free
 * of a dependency on the modules that would otherwise create a cycle back to it.
 */
public interface ScanInvoker {

  /**
   * Scans {@code repoPath}, writing output under {@code outputDir}.
   *
   * @param repoPath the repository to scan
   * @param outputDir where to write report files
   * @return the resulting report as JSON
   * @throws Exception if the scan cannot be run or its output cannot be read
   */
  JsonNode scan(Path repoPath, Path outputDir) throws Exception;
}
