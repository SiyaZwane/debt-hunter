package com.debthunter.engine.codemaat;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses Code Maat's CSV output into typed rows. Validates the header of every analysis type
 * against the exact column set Code Maat is documented to emit, so a format change in a future Code
 * Maat version is reported as a parse failure rather than silently misread.
 */
public final class CodeMaatOutputParser {

  private static final String REVISIONS_HEADER = "entity,n-revs";
  private static final String COUPLING_HEADER = "entity,coupled,degree,average-revs";
  private static final String AGE_HEADER = "entity,age-months";
  private static final String AUTHORS_HEADER = "entity,n-authors,n-revs";

  /**
   * Parses the output of {@code code-maat -a revisions}.
   *
   * @param csv the raw CSV text, including its header line
   * @return one row per file
   */
  public List<RevisionsRow> parseRevisions(String csv) {
    List<String> lines = validateHeaderAndSplit(csv, REVISIONS_HEADER);
    List<RevisionsRow> rows = new ArrayList<>();
    for (String line : lines) {
      String[] columns = splitColumns(line, 2, REVISIONS_HEADER);
      rows.add(new RevisionsRow(columns[0], parseInt(columns[1], line)));
    }
    return rows;
  }

  /**
   * Parses the output of {@code code-maat -a coupling}.
   *
   * @param csv the raw CSV text, including its header line
   * @return one row per coupled file pair
   */
  public List<CouplingRow> parseCoupling(String csv) {
    List<String> lines = validateHeaderAndSplit(csv, COUPLING_HEADER);
    List<CouplingRow> rows = new ArrayList<>();
    for (String line : lines) {
      String[] columns = splitColumns(line, 4, COUPLING_HEADER);
      rows.add(
          new CouplingRow(
              columns[0], columns[1], parseInt(columns[2], line), parseInt(columns[3], line)));
    }
    return rows;
  }

  /**
   * Parses the output of {@code code-maat -a age}.
   *
   * @param csv the raw CSV text, including its header line
   * @return one row per file
   */
  public List<AgeRow> parseAge(String csv) {
    List<String> lines = validateHeaderAndSplit(csv, AGE_HEADER);
    List<AgeRow> rows = new ArrayList<>();
    for (String line : lines) {
      String[] columns = splitColumns(line, 2, AGE_HEADER);
      rows.add(new AgeRow(columns[0], parseInt(columns[1], line)));
    }
    return rows;
  }

  /**
   * Parses the output of {@code code-maat -a authors}.
   *
   * @param csv the raw CSV text, including its header line
   * @return one row per file
   */
  public List<AuthorsRow> parseAuthors(String csv) {
    List<String> lines = validateHeaderAndSplit(csv, AUTHORS_HEADER);
    List<AuthorsRow> rows = new ArrayList<>();
    for (String line : lines) {
      String[] columns = splitColumns(line, 3, AUTHORS_HEADER);
      rows.add(new AuthorsRow(columns[0], parseInt(columns[1], line), parseInt(columns[2], line)));
    }
    return rows;
  }

  private List<String> validateHeaderAndSplit(String csv, String expectedHeader) {
    List<String> lines = new ArrayList<>();
    for (String line : csv.split("\\R")) {
      if (!line.isBlank()) {
        lines.add(line);
      }
    }
    if (lines.isEmpty()) {
      throw new CodeMaatParseException(
          "Expected a header line '" + expectedHeader + "' but output was empty");
    }
    String actualHeader = lines.get(0).strip();
    if (!actualHeader.equals(expectedHeader)) {
      throw new CodeMaatParseException(
          "Expected header '" + expectedHeader + "' but got '" + actualHeader + "'");
    }
    return lines.subList(1, lines.size());
  }

  private String[] splitColumns(String line, int expectedCount, String expectedHeader) {
    String[] columns = line.split(",", -1);
    if (columns.length != expectedCount) {
      throw new CodeMaatParseException(
          "Expected "
              + expectedCount
              + " columns matching header '"
              + expectedHeader
              + "' but row '"
              + line
              + "' has "
              + columns.length);
    }
    return columns;
  }

  private int parseInt(String value, String line) {
    try {
      return Integer.parseInt(value.strip());
    } catch (NumberFormatException e) {
      throw new CodeMaatParseException(
          "Expected a number but got '" + value + "' in row '" + line + "'");
    }
  }
}
