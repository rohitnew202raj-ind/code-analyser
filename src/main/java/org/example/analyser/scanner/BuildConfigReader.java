package org.example.analyser.scanner;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects a target project's module source roots and Java
 * language level from its own build file, instead of the
 * previous hardcoded "always Java 21, single flat source
 * tree" assumption.
 *
 * Supports multi-module Maven (recursively following
 * &lt;modules&gt;) with a best-effort Gradle fallback.
 *
 * LIMITATION (documented): the Gradle path is regex-based,
 * not a real Groovy/Kotlin-DSL parser - it handles the common
 * "sourceCompatibility = '17'" / "sourceCompatibility =
 * JavaVersion.VERSION_17" forms but will miss anything
 * computed dynamically (a shared version catalog, a variable,
 * a convention plugin). When nothing can be determined at
 * all, this falls back to treating the whole target path as
 * one module on Java 21, matching the previous behavior, so a
 * project this can't parse still gets analyzed rather than
 * failing outright.
 */
@Component
public class BuildConfigReader {

    private static final int DEFAULT_JAVA_VERSION = 21;

    private static final Pattern GRADLE_SOURCE_COMPATIBILITY =
            Pattern.compile(
                    "sourceCompatibility\\s*=\\s*['\"]?"
                            + "(?:JavaVersion\\.VERSION_)?"
                            + "(\\d+)(?:\\.(\\d+))?['\"]?"
            );

    public record ModuleConfig(
            Path sourceRoot,
            int javaVersion) {
    }

    public List<ModuleConfig> detect(Path targetProject) {

        List<ModuleConfig> mavenModules =
                detectMavenModules(targetProject);

        if (!mavenModules.isEmpty()) {
            return mavenModules;
        }

        List<ModuleConfig> gradleModules =
                detectGradleModules(targetProject);

        if (!gradleModules.isEmpty()) {
            return gradleModules;
        }

        return List.of(
                new ModuleConfig(
                        targetProject,
                        DEFAULT_JAVA_VERSION
                )
        );
    }

    // ==========================================
    // MAVEN
    // ==========================================

    private List<ModuleConfig> detectMavenModules(
            Path projectRoot) {

        List<ModuleConfig> modules =
                new ArrayList<>();

        Path rootPom =
                projectRoot.resolve("pom.xml");

        if (!Files.isRegularFile(rootPom)) {
            return modules;
        }

        collectMavenModule(
                projectRoot,
                rootPom,
                DEFAULT_JAVA_VERSION,
                modules
        );

        return modules;
    }

    private void collectMavenModule(
            Path moduleDir,
            Path pomFile,
            int inheritedJavaVersion,
            List<ModuleConfig> collected) {

        Document document =
                parseXml(pomFile);

        if (document == null) {
            return;
        }

        int javaVersion =
                readJavaVersion(document)
                        .orElse(inheritedJavaVersion);

        List<String> childModules =
                readTextValues(document, "//modules/module");

        if (childModules.isEmpty()) {

            Path sourceRoot =
                    moduleDir.resolve("src/main/java");

            if (Files.isDirectory(sourceRoot)) {

                collected.add(
                        new ModuleConfig(
                                sourceRoot,
                                javaVersion
                        )
                );
            }

            return;
        }

        for (String child : childModules) {

            Path childDir =
                    moduleDir.resolve(child);

            Path childPom =
                    childDir.resolve("pom.xml");

            if (Files.isRegularFile(childPom)) {

                collectMavenModule(
                        childDir,
                        childPom,
                        javaVersion,
                        collected
                );
            }
        }
    }

    private java.util.Optional<Integer> readJavaVersion(
            Document document) {

        List<String> candidates = new ArrayList<>();

        candidates.addAll(
                readTextValues(
                        document,
                        "//properties/java.version"
                )
        );

        candidates.addAll(
                readTextValues(
                        document,
                        "//properties/maven.compiler.release"
                )
        );

        candidates.addAll(
                readTextValues(
                        document,
                        "//properties/maven.compiler.source"
                )
        );

        for (String candidate : candidates) {

            Integer parsed =
                    parseJavaVersion(candidate);

            if (parsed != null) {
                return java.util.Optional.of(parsed);
            }
        }

        return java.util.Optional.empty();
    }

    private Integer parseJavaVersion(String raw) {

        if (raw == null || raw.isBlank()) {
            return null;
        }

        String trimmed = raw.trim();

        // "1.8" -> 8
        if (trimmed.startsWith("1.")) {
            trimmed = trimmed.substring(2);
        }

        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException notNumeric) {
            return null;
        }
    }

    private Document parseXml(Path file) {

        try {

            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();

            factory.setFeature(
                    "http://apache.org/xml/features/"
                            + "disallow-doctype-decl",
                    true
            );

            DocumentBuilder builder =
                    factory.newDocumentBuilder();

            return builder.parse(file.toFile());

        } catch (Exception unparsable) {

            return null;
        }
    }

    private List<String> readTextValues(
            Document document,
            String xpathExpression) {

        List<String> values =
                new ArrayList<>();

        try {

            XPath xpath =
                    XPathFactory.newInstance().newXPath();

            NodeList nodes =
                    (NodeList) xpath.evaluate(
                            xpathExpression,
                            document,
                            XPathConstants.NODESET
                    );

            for (int i = 0; i < nodes.getLength(); i++) {

                Node node = nodes.item(i);

                if (node.getTextContent() != null) {
                    values.add(node.getTextContent().trim());
                }
            }

        } catch (Exception invalidExpression) {
            // fall through with whatever was collected
        }

        return values;
    }

    // ==========================================
    // GRADLE (best-effort, regex-based)
    // ==========================================

    private List<ModuleConfig> detectGradleModules(
            Path projectRoot) {

        List<ModuleConfig> modules =
                new ArrayList<>();

        Path buildFile =
                projectRoot.resolve("build.gradle");

        if (!Files.isRegularFile(buildFile)) {

            buildFile =
                    projectRoot.resolve("build.gradle.kts");
        }

        if (!Files.isRegularFile(buildFile)) {
            return modules;
        }

        int javaVersion =
                readGradleJavaVersion(buildFile)
                        .orElse(DEFAULT_JAVA_VERSION);

        Path sourceRoot =
                projectRoot.resolve("src/main/java");

        if (Files.isDirectory(sourceRoot)) {

            modules.add(
                    new ModuleConfig(sourceRoot, javaVersion)
            );
        }

        return modules;
    }

    private java.util.Optional<Integer> readGradleJavaVersion(
            Path buildFile) {

        try {

            String content =
                    Files.readString(buildFile);

            Matcher matcher =
                    GRADLE_SOURCE_COMPATIBILITY.matcher(content);

            if (matcher.find()) {
                return java.util.Optional.of(
                        Integer.parseInt(matcher.group(1))
                );
            }

        } catch (IOException unreadable) {
            // fall through to empty
        }

        return java.util.Optional.empty();
    }
}
