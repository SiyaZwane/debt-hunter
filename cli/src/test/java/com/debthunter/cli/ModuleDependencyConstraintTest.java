package com.debthunter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * FR-11: the {@code ai} module must never be a build-time dependency of any module on the scan/gate
 * execution path. Parses each such module's {@code pom.xml} directly, rather than trusting a
 * convention, so a future dependency addition fails this test immediately.
 */
class ModuleDependencyConstraintTest {

  private static final List<String> SCAN_PATH_MODULES =
      List.of("domain", "engine-spi", "repository", "policy", "output", "application");

  @Test
  void noScanPathModuleDependsOnTheAiModule() throws Exception {
    Path repoRoot = Path.of("").toAbsolutePath().getParent();

    for (String module : SCAN_PATH_MODULES) {
      Set<String> dependencyArtifactIds =
          declaredDependencyArtifactIds(repoRoot.resolve(module).resolve("pom.xml"));
      assertThat(dependencyArtifactIds)
          .as("dependencies declared by the %s module", module)
          .doesNotContain("ai");
    }
  }

  private Set<String> declaredDependencyArtifactIds(Path pomFile)
      throws IOException, ParserConfigurationException, SAXException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    Document document = factory.newDocumentBuilder().parse(pomFile.toFile());

    Element dependencies = firstChildElement(document.getDocumentElement(), "dependencies");
    if (dependencies == null) {
      return Set.of();
    }

    Set<String> artifactIds = new HashSet<>();
    NodeList dependencyNodes = dependencies.getElementsByTagName("dependency");
    for (int i = 0; i < dependencyNodes.getLength(); i++) {
      Element dependency = (Element) dependencyNodes.item(i);
      Element artifactId = firstChildElement(dependency, "artifactId");
      if (artifactId != null) {
        artifactIds.add(artifactId.getTextContent().strip());
      }
    }
    return artifactIds;
  }

  private Element firstChildElement(Element parent, String tagName) {
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child instanceof Element element && element.getTagName().equals(tagName)) {
        return element;
      }
    }
    return null;
  }
}
