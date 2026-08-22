package org.example.analyser.parser;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

@Component
public class JavaSourceParser {

    public CompilationUnit parse(Path javaFile, int javaVersion)
            throws IOException {

        ParserConfiguration.LanguageLevel languageLevel =
                switch (javaVersion) {
                    case 17 -> ParserConfiguration.LanguageLevel.JAVA_17;
                    case 21 -> ParserConfiguration.LanguageLevel.JAVA_21;
                    default -> throw new IllegalArgumentException(
                            "Unsupported Java version: " + javaVersion
                    );
                };

        ParserConfiguration configuration =
                new ParserConfiguration()
                        .setLanguageLevel(languageLevel);

        return new com.github.javaparser.JavaParser(configuration)
                .parse(javaFile)
                .getResult()
                .orElseThrow();
    }
}