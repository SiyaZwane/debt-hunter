package com.debthunter.engine.codemaat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Builds fake Code Maat "executables" (plain shell scripts) for tests, standing in for the real
 * subprocess without requiring Code Maat itself to be installed. Each script accepts the same
 * {@code -l <log> -c git2 -a <analysis>} arguments the real tool does and emits a fixed CSV body
 * per analysis type, so {@link CodeMaatEngine}'s subprocess-handling logic can be exercised
 * end-to-end and deterministically.
 */
final class CodeMaatTestSupport {

  private CodeMaatTestSupport() {}

  /**
   * Writes a script that emits {@code csvByAnalysisType.get(analysisType)} on stdout and exits 0
   * for any analysis type present in the map, or exits 1 with a diagnostic on stderr otherwise.
   *
   * @param directory where to create the script
   * @param csvByAnalysisType CSV body (including header) keyed by {@code -a} value
   * @return the script's path, already marked executable
   */
  static Path fakeCodeMaat(Path directory, Map<String, String> csvByAnalysisType) {
    StringBuilder script = new StringBuilder("#!/bin/sh\n").append("analysis=\"\"\n");
    script
        .append("while [ \"$#\" -gt 0 ]; do\n")
        .append("  case \"$1\" in\n")
        .append("    -a) analysis=\"$2\"; shift 2 ;;\n")
        .append("    *) shift ;;\n")
        .append("  esac\n")
        .append("done\n")
        .append("case \"$analysis\" in\n");
    csvByAnalysisType.forEach(
        (analysisType, csv) ->
            script
                .append("  ")
                .append(analysisType)
                .append(")\n    cat <<'DEBTHUNTEREOF'\n")
                .append(csv)
                .append(csv.endsWith("\n") ? "" : "\n")
                .append("DEBTHUNTEREOF\n    exit 0 ;;\n"));
    script
        .append("  *) echo \"fake-code-maat: unsupported analysis '$analysis'\" >&2; exit 1 ;;\n")
        .append("esac\n");
    return writeExecutable(directory, "fake-code-maat.sh", script.toString());
  }

  /**
   * Writes a script that sleeps well past any reasonable test timeout, to exercise timeout
   * handling. It ignores SIGTERM so the engine's forceful kill is what actually stops it.
   *
   * @param directory where to create the script
   * @return the script's path, already marked executable
   */
  static Path hangingCodeMaat(Path directory) {
    String script = "#!/bin/sh\ntrap '' TERM\nsleep 300\n";
    return writeExecutable(directory, "hanging-code-maat.sh", script);
  }

  /**
   * Writes a script that computes real revision counts from the log file it's given — counting how
   * many {@code 0\t0\t<path>} lines {@link CodeMaatLogWriter} wrote per file — instead of replaying
   * canned output. {@code coupling}, {@code age}, and {@code authors} emit empty results, since
   * this stands in for a test that only cares about revisions/hotspots.
   *
   * @param directory where to create the script
   * @return the script's path, already marked executable
   */
  static Path revisionCountingFakeCodeMaat(Path directory) {
    String script =
        "#!/bin/sh\n"
            + "analysis=\"\"\n"
            + "logfile=\"\"\n"
            + "while [ \"$#\" -gt 0 ]; do\n"
            + "  case \"$1\" in\n"
            + "    -l) logfile=\"$2\"; shift 2 ;;\n"
            + "    -a) analysis=\"$2\"; shift 2 ;;\n"
            + "    *) shift ;;\n"
            + "  esac\n"
            + "done\n"
            + "case \"$analysis\" in\n"
            + "  revisions)\n"
            + "    echo \"entity,n-revs\"\n"
            + "    awk -F'\\t' '$1==\"0\" && $2==\"0\" {print $3}' \"$logfile\" | sort | uniq -c |"
            + " awk '{print $2\",\"$1}'\n"
            + "    exit 0 ;;\n"
            + "  coupling) echo \"entity,coupled,degree,average-revs\"; exit 0 ;;\n"
            + "  age) echo \"entity,age-months\"; exit 0 ;;\n"
            + "  authors) echo \"entity,n-authors,n-revs\"; exit 0 ;;\n"
            + "  *) exit 1 ;;\n"
            + "esac\n";
    return writeExecutable(directory, "revision-counting-fake-code-maat.sh", script);
  }

  /**
   * Writes a script that always exits non-zero, to exercise failure handling.
   *
   * @param directory where to create the script
   * @param exitCode the exit code to return
   * @return the script's path, already marked executable
   */
  static Path failingCodeMaat(Path directory, int exitCode) {
    String script =
        "#!/bin/sh\necho 'fake-code-maat: simulated failure' >&2\nexit " + exitCode + "\n";
    return writeExecutable(directory, "failing-code-maat.sh", script);
  }

  private static Path writeExecutable(Path directory, String fileName, String content) {
    try {
      Files.createDirectories(directory);
      Path script = directory.resolve(fileName);
      Files.writeString(script, content);
      if (!script.toFile().setExecutable(true)) {
        throw new IllegalStateException("Could not mark " + script + " executable");
      }
      return script;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
