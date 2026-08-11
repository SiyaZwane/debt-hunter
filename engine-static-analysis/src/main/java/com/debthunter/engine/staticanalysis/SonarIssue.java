package com.debthunter.engine.staticanalysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One issue from a SonarQube issues-search export: {@code {"issues":[...]}}. {@code component} is
 * SonarQube's {@code "<projectKey>:<relativePath>"} form; {@code line} is {@code null} for a
 * file-scoped issue with no specific line.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SonarIssue(
    String key,
    String rule,
    String severity,
    String component,
    Integer line,
    String message,
    String type) {}
