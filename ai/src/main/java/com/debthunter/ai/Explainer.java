package com.debthunter.ai;

import com.debthunter.domain.Finding;

/**
 * Explains a single finding in natural language, independent of how that's done. This is
 * exploratory, developer-facing tooling: nothing on the scan/gate path calls it, and nothing on the
 * scan/gate path depends on this module.
 */
public interface Explainer {

  /**
   * Explains {@code finding} by sending {@code prompt} to the model endpoint.
   *
   * @param finding the finding to explain
   * @param prompt the exact prompt text to send
   * @param config where and how to request the explanation
   * @return an available explanation, or an unavailable one describing why not
   */
  Explanation explain(Finding finding, String prompt, ExplainConfig config);
}
