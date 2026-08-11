package com.debthunter.application.crossrepo;

import java.util.List;

/** The full set of team-level couplings produced by a {@link CrossRepositoryAnalyser} run. */
public record CouplingMap(List<TeamCoupling> couplings) {

  public CouplingMap {
    couplings = List.copyOf(couplings);
  }
}
