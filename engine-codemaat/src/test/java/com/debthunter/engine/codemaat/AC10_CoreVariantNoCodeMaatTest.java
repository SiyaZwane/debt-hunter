package com.debthunter.engine.codemaat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * AC-10: Code Maat is invoked as an isolated subprocess, never linked as a library. This module's
 * own classpath must not contain the Clojure runtime Code Maat is built on — if it ever does, some
 * dependency silently started pulling it in as a real compile-time dependency, defeating the whole
 * point of the subprocess adapter.
 */
class AC10_CoreVariantNoCodeMaatTest {

  @Test
  void ac10_clojureRuntimeIsNotOnTheClasspath() {
    assertThatThrownBy(() -> Class.forName("clojure.lang.RT"))
        .isInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void ac10_noClassInThisModuleBelongsToACodeMaatOrClojurePackage() throws Exception {
    for (Class<?> engineClass :
        new Class<?>[] {
          CodeMaatEngine.class,
          CodeMaatLogWriter.class,
          CodeMaatOutputParser.class,
          CodeMaatFindingMapper.class
        }) {
      for (var field : engineClass.getDeclaredFields()) {
        String typeName = field.getType().getName();
        if (typeName.startsWith("clojure.") || typeName.contains("codemaat.core")) {
          throw new AssertionError(
              engineClass.getSimpleName() + "." + field.getName() + " references " + typeName);
        }
      }
    }
  }
}
