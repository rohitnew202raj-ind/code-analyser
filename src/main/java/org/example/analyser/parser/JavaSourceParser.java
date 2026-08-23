package org.example.analyser.parser;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
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
     * own detected source roots plus the JDK. Must be called
     * once, before any parse() calls, for calculateResolvedType()
     * / resolve() to work anywhere downstream.
     *
     * LIMITATION (documented): only the target project's own
     * source and the JDK (via reflection) are resolvable.
     * External framework/library types (Spring, JPA, etc.)
     * are not on this classpath, so resolution naturally fails
     * for those - callers are expected to fall back to
     * AST-based heuristics when resolution fails, not to
     * assume it always succeeds.
     */
    public void configureSymbolSolver(List<Path> sourceRoots) {

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
