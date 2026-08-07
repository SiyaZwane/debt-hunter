package com.debthunter.domain;

/** The kind of technical debt a {@link Finding} represents. */
public enum Category {
  HOTSPOT,
  TEMPORAL_COUPLING,
  CHURN,
  KNOWLEDGE_CONCENTRATION,
  ARCHITECTURE,
  STATIC_ANALYSIS,
  DEPENDENCY,
  TEST_HEALTH,
  CUSTOM
}
