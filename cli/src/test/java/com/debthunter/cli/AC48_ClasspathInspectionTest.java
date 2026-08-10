package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.debthunter.application.scan.ScanUseCase;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AC-48: reflective classpath inspection of the scan execution path's own type graph — every field
 * and constructor parameter type declared on {@link ScanCommand} and {@link ScanUseCase} belongs to
 * some package other than {@code com.debthunter.ai}.
 */
class AC48_ClasspathInspectionTest {

  @Test
  void ac48_noFieldOrConstructorParameterOnTheScanPathIsAnAiType() {
    List<Class<?>> types = new ArrayList<>();
    collectTypes(ScanCommand.class, types);
    collectTypes(ScanUseCase.class, types);

    List<String> aiTypeNames =
        types.stream()
            .filter(type -> type.getPackageName().startsWith("com.debthunter.ai"))
            .map(Class::getName)
            .toList();

    assertThat(aiTypeNames).isEmpty();
  }

  private void collectTypes(Class<?> owner, List<Class<?>> types) {
    for (Field field : owner.getDeclaredFields()) {
      types.add(field.getType());
    }
    for (Constructor<?> constructor : owner.getDeclaredConstructors()) {
      types.addAll(List.of(constructor.getParameterTypes()));
    }
  }
}
