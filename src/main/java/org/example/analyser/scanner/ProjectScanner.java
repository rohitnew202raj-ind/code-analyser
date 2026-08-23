package org.example.analyser.scanner;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class ProjectScanner {

    /*
     * Build output, VCS metadata, and generated sources were
     * previously scanned right along with real source code -
     * on a real project that means build artifacts and
     * generated-sources directories under target/ get counted
     * as production architecture. IDE folders are excluded
     * for the same reason.
     */
    private static final Set<String> EXCLUDED_DIRECTORY_NAMES =
            Set.of(
                    "target",
                    "build",
                    ".git",
                    ".idea",
                    ".gradle",
                    "node_modules",
                    "out"
            );

    public List<Path> scanJavaFiles(Path projectPath)
            throws IOException {

        try (Stream<Path> paths = Files.walk(projectPath)) {

            return paths
                    .filter(path -> !isExcluded(
                            projectPath,
                            path
                    ))
                    .filter(Files::isRegularFile)
                    .filter(path ->
                            path.toString().endsWith(".java")
                    )
                    .toList();
        }
    }

    private boolean isExcluded(
            Path projectPath,
            Path candidate) {

        Path relative;

        try {
            relative = projectPath.relativize(candidate);
        } catch (IllegalArgumentException notRelativizable) {
            return false;
        }

        for (Path segment : relative) {

            if (EXCLUDED_DIRECTORY_NAMES.contains(
                    segment.toString()
            )) {

                return true;
            }
        }

        return false;
    }
}
