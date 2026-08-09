package com.debthunter.policy;

/** Where a scan's baseline came from, recorded in the run's metadata for traceability. */
public enum BaselineProvenance {
  /** An explicit {@code --baseline} path was supplied. */
  EXPLICIT,
  /** No explicit path was given; a baseline was found at the conventional pipeline-cache path. */
  PIPELINE_CACHE,
  /** No explicit path or pipeline cache; a baseline was fetched from the control plane. */
  CONTROL_PLANE,
  /** No baseline was found anywhere in the resolution chain. */
  NONE
}
