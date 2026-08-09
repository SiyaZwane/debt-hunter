package com.debthunter.engine.spi;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Lets an {@link AnalysisEngine} provide the enclosing type or method for a finding's location, so
 * that its fingerprint can be anchored to a symbol rather than just a line number.
 */
public interface SymbolAnchorExtractor {

  /**
   * An extractor that never derives a symbol; the default for engines with no symbol-level
   * granularity.
   */
  SymbolAnchorExtractor NONE = (filePath, line) -> Optional.empty();

  /**
   * Resolves the enclosing symbol for a location, if one can be derived.
   *
   * @param filePath the file containing the location, relative to the repository root
   * @param line the 1-based source line, or 0 if the finding is file-scoped
   * @return the enclosing symbol's identity (e.g. {@code "Foo#bar"}), or empty if none is derivable
   */
  Optional<String> anchorFor(Path filePath, int line);
}
