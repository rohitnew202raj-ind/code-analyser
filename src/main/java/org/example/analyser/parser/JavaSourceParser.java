package org.example.analyser.parser;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class JavaSourceParser {

    private JavaSymbolSolver symbolSolver;

    /**
     * Wires up the Symbol Solver against the target project's
     * own detected source roots, its resolved dependency jars
     * (see {@link org.example.analyser.scanner.MavenDependencyResolver}),
     * and the JDK. Must be called once, before any parse() calls,
     * for calculateResolvedType() / resolve() to work anywhere
     * downstream.
     *
     * LIMITATION (documented): {@code dependencyJars} is only
     * ever populated for Maven target projects whose dependencies
     * are already present in the local repository cache - for a
     * Gradle project, or a Maven project that's never been built
     * in this environment, this list is empty and resolution
     * falls back to source+JDK only, exactly as before. A jar
     * that fails to load (corrupt, unreadable) is skipped rather
     * than aborting solver setup entirely.
     */
    public void configureSymbolSolver(
            List<Path> sourceRoots,
            List<Path> dependencyJars) {

        CombinedTypeSolver combinedTypeSolver =
                new CombinedTypeSolver();

        combinedTypeSolver.add(new ReflectionTypeSolver());

        for (Path sourceRoot : sourceRoots) {

            if (Files.isDirectory(sourceRoot)) {

                combinedTypeSolver.add(
                        new JavaParserTypeSolver(sourceRoot)
                );
            }
        }

        for (Path dependencyJar : dependencyJars) {

            try {

                combinedTypeSolver.add(
                        new JarTypeSolver(dependencyJar)
                );

            } catch (IOException unreadableJar) {
                // Skip this one jar rather than aborting solver
                // setup for the whole run.
            }
        }

        this.symbolSolver =
                new JavaSymbolSolver(combinedTypeSolver);
    }

    public CompilationUnit parse(Path javaFile, int javaVersion)
            throws IOException {

        ParserConfiguration.LanguageLevel languageLevel =
                switch (javaVersion) {
                    case 8 -> ParserConfiguration.LanguageLevel.JAVA_8;
                    case 11 -> ParserConfiguration.LanguageLevel.JAVA_11;
                    case 17 -> ParserConfiguration.LanguageLevel.JAVA_17;
                    case 21 -> ParserConfiguration.LanguageLevel.JAVA_21;
                    /*
                     * Graceful fallback instead of throwing:
                     * an unrecognized version (older, or a
                     * newer release than this analyzer knows
                     * about yet) shouldn't abort the whole
                     * run. Parsing with a newer language level
                     * than the source actually uses is safe
                     * for the vast majority of real code.
                     */
                    default -> ParserConfiguration.LanguageLevel.JAVA_21;
                };

        ParserConfiguration configuration =
                new ParserConfiguration()
                        .setLanguageLevel(languageLevel);

        if (symbolSolver != null) {
            configuration.setSymbolResolver(symbolSolver);
        }

        ParseResult<CompilationUnit> result =
                new com.github.javaparser.JavaParser(configuration)
                        .parse(javaFile);

        if (!result.isSuccessful()
                || result.getResult().isEmpty()) {

            throw new IOException(
                    "Failed to parse "
                            + javaFile
                            + ": "
                            + result.getProblems()
            );
        }

        return result.getResult().get();
    }
}
