package com.debthunter.integration;

/**
 * How far work-item publication has been rolled out for a repository: {@code OBSERVE} watches
 * without ever creating, closing, or reopening a tracker item; {@code ENFORCE} performs those
 * actions for real.
 */
public enum RolloutStage {
  OBSERVE,
  ENFORCE
}
