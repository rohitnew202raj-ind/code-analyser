package org.example.analyser.scanner;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Resolves the target project's own dependency jars (Spring, JPA,
 * etc.) so they can be added to the Symbol Solver's classpath -
 * closing the gap {@link org.example.analyser.analyzer.TypeResolver}'s
 * own javadoc documents: without this, the solver only ever sees
 * the target project's own source plus the JDK, so every external
 * framework type fails to resolve and every call site falls back
 * to the AST-based heuristics.
 *
 * Mechanism: shells out to {@code mvn dependency:build-classpath},
 * the same standard Maven plugin goal a developer would run by
 * hand, rather than reimplementing Maven's own dependency
 * resolution (transitive versions, exclusions, scopes, dependency
 * management) from scratch. This only ever reads jars already
 * present in the local Maven repository cache (typically
 * {@code ~/.m2/repository}) - it does not fetch anything from the
 * network itself; whether that cache already has everything the
 * target project needs depends on whether that project has been
 * built at least once in this environment already, which is true
 * for the overwhelming majority of real projects someone would
 * point this analyzer at.
 *
 * SCOPE (documented, not a bug):
 *
 * <ul>
 * <li>Maven projects only - a Gradle target project's dependency
 * jars are not resolved here at all (see the Gradle limitations
 * already documented on {@link BuildConfigReader}; doing this
 * properly for Gradle needs its own, larger effort).</li>
 * <li>Only the target project's root {@code pom.xml} is resolved -
 * a multi-module reactor's submodule-specific dependencies are
 * not separately aggregated. For the common case (a single-module
 * project, or a root module that itself has real source and
 * dependencies) this is complete; for an aggregator-only root POM
 * with no source of its own, it may resolve nothing useful.</li>
 * <li>Failure is always non-fatal. If {@code mvn} isn't on
 * {@code PATH}, the project doesn't build, or resolution simply
 * times out, this returns an empty list and the analyzer falls
 * back to exactly the source+JDK-only behavior that existed
 * before this class - never a hard failure of the whole run.</li>
 * </ul>
 */
@Component
public class MavenDependencyResolver {

    private static final long TIMEOUT_SECONDS = 90;

    /**
     * Resolves the target project's dependency jars, or an empty
     * list if it isn't a Maven project, {@code mvn} isn't
     * available, or resolution fails/times out for any reason.
     */
    public List<Path> resolve(Path targetProject) {

        Path pomFile = targetProject.resolve("pom.xml");

        if (!Files.isRegularFile(pomFile)) {
            return List.of();
        }

        Path outputFile;

        try {
            outputFile =
                    Files.createTempFile(
                            "architecture-analyzer-classpath-",
                            ".txt"
                    );
        } catch (IOException cannotCreateTempFile) {
            return List.of();
        }

        try {

            boolean succeeded =
                    runMavenBuildClasspath(targetProject, outputFile);

            if (!succeeded) {
                return List.of();
            }

            return parseClasspathFile(outputFile);

        } finally {

            try {
                Files.deleteIfExists(outputFile);
            } catch (IOException ignoredCleanupFailure) {
                // Best-effort temp file cleanup only.
            }
        }
    }

    private boolean runMavenBuildClasspath(
            Path targetProject,
            Path outputFile) {

        try {

            ProcessBuilder processBuilder =
                    new ProcessBuilder(
                            "mvn",
                            "--batch-mode",
                            "--quiet",
                            "dependency:build-classpath",
                            "-Dmdep.outputFile=" + outputFile
                    );

            processBuilder.directory(targetProject.toFile());
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

            Process process = processBuilder.start();

            boolean finished =
                    process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return false;
            }

            return process.exitValue() == 0;

        } catch (IOException | InterruptedException failure) {

            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            return false;
        }
    }

    private List<Path> parseClasspathFile(Path outputFile) {

        try {

            String classpath =
                    Files.readString(outputFile).trim();

            if (classpath.isEmpty()) {
                return List.of();
            }

            List<Path> jars = new ArrayList<>();

            for (String entry : classpath.split(File.pathSeparator)) {

                Path jarPath = Path.of(entry.trim());

                if (Files.isRegularFile(jarPath)) {
                    jars.add(jarPath);
                }
            }

            return jars;

        } catch (IOException cannotReadOutputFile) {
            return List.of();
        }
    }
}
