package com.debthunter.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.debthunter.domain.SuppressionEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SuppressionRegistryTest {

  private final SuppressionRegistry registry = new SuppressionRegistry();
  private static final LocalDate COMMIT_DATE = LocalDate.parse("2026-01-01");

  @Test
  void withNoFileTheResultIsEmpty(@TempDir Path repoRoot) {
    List<SuppressionEntry> entries = registry.load(repoRoot, 0, COMMIT_DATE);

    assertThat(entries).isEmpty();
  }

  @Test
  void parsesEveryFieldOfAValidEntry(@TempDir Path repoRoot) throws IOException {
    write(
        repoRoot,
        """
        suppressions:
          - fingerprint: fp-1
            owner: alice
            reason: "Tracked in JIRA-123"
            expires: "2026-06-01"
        """);

    List<SuppressionEntry> entries = registry.load(repoRoot, 0, COMMIT_DATE);

    assertThat(entries)
        .containsExactly(
            new SuppressionEntry(
                "fp-1", "alice", "Tracked in JIRA-123", LocalDate.parse("2026-06-01")));
  }

  @Test
  void anUnquotedExpiryDateIsParsedTheSameWayAsAQuotedOne(@TempDir Path repoRoot)
      throws IOException {
    write(
        repoRoot,
        """
        suppressions:
          - fingerprint: fp-1
            owner: alice
            reason: unquoted date test
            expires: 2026-06-01
        """);

    List<SuppressionEntry> entries = registry.load(repoRoot, 0, COMMIT_DATE);

    assertThat(entries.get(0).expiresOn()).isEqualTo(LocalDate.parse("2026-06-01"));
  }

  @Test
  void multipleEntriesAreReturnedInFileOrder(@TempDir Path repoRoot) throws IOException {
    write(
        repoRoot,
        """
        suppressions:
          - fingerprint: fp-1
            owner: alice
            reason: first
            expires: "2026-06-01"
          - fingerprint: fp-2
            owner: bob
            reason: second
            expires: "2026-07-01"
        """);

    List<SuppressionEntry> entries = registry.load(repoRoot, 0, COMMIT_DATE);

    assertThat(entries).extracting(SuppressionEntry::fingerprint).containsExactly("fp-1", "fp-2");
  }

  @Test
  void aMissingFingerprintIsRejected(@TempDir Path repoRoot) throws IOException {
    write(
        repoRoot,
        """
        suppressions:
          - owner: alice
            reason: no fingerprint
            expires: "2026-06-01"
        """);

    assertThatThrownBy(() -> registry.load(repoRoot, 0, COMMIT_DATE))
        .isInstanceOf(SuppressionParseException.class)
        .hasMessageContaining("fingerprint");
  }

  @Test
  void aMissingOwnerIsRejected(@TempDir Path repoRoot) throws IOException {
    write(
        repoRoot,
        """
        suppressions:
          - fingerprint: fp-1
            reason: no owner
            expires: "2026-06-01"
        """);

    assertThatThrownBy(() -> registry.load(repoRoot, 0, COMMIT_DATE))
        .isInstanceOf(SuppressionParseException.class)
        .hasMessageContaining("owner");
  }

  @Test
  void aMissingReasonIsRejected(@TempDir Path repoRoot) throws IOException {
    write(
        repoRoot,
        """
        suppressions:
          - fingerprint: fp-1
            owner: alice
            expires: "2026-06-01"
        """);

    assertThatThrownBy(() -> registry.load(repoRoot, 0, COMMIT_DATE))
        .isInstanceOf(SuppressionParseException.class)
        .hasMessageContaining("reason");
  }

  @Test
  void aMissingExpiryIsRejected(@TempDir Path repoRoot) throws IOException {
    write(
        repoRoot,
        """
        suppressions:
          - fingerprint: fp-1
            owner: alice
            reason: no expiry
        """);

    assertThatThrownBy(() -> registry.load(repoRoot, 0, COMMIT_DATE))
        .isInstanceOf(SuppressionParseException.class)
        .hasMessageContaining("expires");
  }

  @Test
  void anInvalidExpiryDateFormatIsRejected(@TempDir Path repoRoot) throws IOException {
    write(
        repoRoot,
        """
        suppressions:
          - fingerprint: fp-1
            owner: alice
            reason: bad date
            expires: "not-a-date"
        """);

    assertThatThrownBy(() -> registry.load(repoRoot, 0, COMMIT_DATE))
        .isInstanceOf(SuppressionParseException.class)
        .hasMessageContaining("expires");
  }

  @Test
  void malformedYamlIsRejected(@TempDir Path repoRoot) throws IOException {
    write(repoRoot, "suppressions: [unterminated");

    assertThatThrownBy(() -> registry.load(repoRoot, 0, COMMIT_DATE))
        .isInstanceOf(SuppressionParseException.class);
  }

  @Test
  void aNonListSuppressionsSectionIsRejected(@TempDir Path repoRoot) throws IOException {
    write(repoRoot, "suppressions: not-a-list");

    assertThatThrownBy(() -> registry.load(repoRoot, 0, COMMIT_DATE))
        .isInstanceOf(SuppressionParseException.class)
        .hasMessageContaining("list");
  }

  @Test
  void withNoMaxExpiryDaysConfiguredAnyExpiryIsAccepted(@TempDir Path repoRoot) throws IOException {
    write(
        repoRoot,
        """
        suppressions:
          - fingerprint: fp-1
            owner: alice
            reason: far future
            expires: "2099-01-01"
        """);

    List<SuppressionEntry> entries = registry.load(repoRoot, 0, COMMIT_DATE);

    assertThat(entries).hasSize(1);
  }

  @Test
  void anExpiryWithinTheMaxExpiryDaysCeilingIsAccepted(@TempDir Path repoRoot) throws IOException {
    write(
        repoRoot,
        """
        suppressions:
          - fingerprint: fp-1
            owner: alice
            reason: within ceiling
            expires: "2026-01-30"
        """);

    List<SuppressionEntry> entries = registry.load(repoRoot, 30, COMMIT_DATE);

    assertThat(entries).hasSize(1);
  }

  @Test
  void anExpiryBeyondTheMaxExpiryDaysCeilingIsRejected(@TempDir Path repoRoot) throws IOException {
    write(
        repoRoot,
        """
        suppressions:
          - fingerprint: fp-1
            owner: alice
            reason: beyond ceiling
            expires: "2026-06-01"
        """);

    assertThatThrownBy(() -> registry.load(repoRoot, 30, COMMIT_DATE))
        .isInstanceOf(SuppressionRejectedException.class)
        .hasMessageContaining("maxExpiryDays");
  }

  private void write(Path repoRoot, String yaml) throws IOException {
    Files.writeString(repoRoot.resolve(SuppressionRegistry.SUPPRESSIONS_FILE_NAME), yaml);
  }
}
