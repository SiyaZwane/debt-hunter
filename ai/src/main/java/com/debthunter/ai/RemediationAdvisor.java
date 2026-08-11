package com.debthunter.ai;

import com.debthunter.domain.Finding;

/** Proposes a remediation action for a finding, independent of how the proposal is derived. */
public interface RemediationAdvisor {

  /**
   * Proposes a remediation action for {@code finding}.
   *
   * @param finding the finding to propose a remediation for
   * @return a human-readable remediation suggestion
   */
  String proposeRemediation(Finding finding);
}
