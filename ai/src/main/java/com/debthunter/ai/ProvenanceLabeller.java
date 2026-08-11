package com.debthunter.ai;

import java.util.Objects;

/** Marks generated text as model-authored, so it is never mistaken for human-written content. */
public final class ProvenanceLabeller {

  /** The marker prefixed onto every piece of generated text. */
  public static final String LABEL = "[AI-generated]";

  private ProvenanceLabeller() {}

  /**
   * Prefixes {@code text} with the provenance marker.
   *
   * @param text the generated text to label
   * @return {@code text}, prefixed with {@value #LABEL}
   */
  public static String label(String text) {
    Objects.requireNonNull(text, "text");
    return LABEL + " " + text;
  }
}
