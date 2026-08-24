package org.example.analyser.scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MavenDependencyResolverTest {

    private final MavenDependencyResolver resolver =
            new MavenDependencyResolver();

    @Test
    void returnsEmptyListWhenNoPomXmlPresent(@TempDir Path targetProject) {

        List<Path> jars = resolver.resolve(targetProject);

        assertThat(jars).isEmpty();
    }

    @Test
    void resolvesADependencyJarAlreadyPresentInTheLocalRepository(
            @TempDir Path targetProject) throws IOException {

        // javaparser-core is guaranteed to already be present in
        // the local repository cache - it's this very project's
        // own dependency (see pom.xml) - so this test needs no
        // network access to pass.
        Files.writeString(
                targetProject.resolve("pom.xml"),
                """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.acme</groupId>
                    <artifactId>dependency-resolution-test</artifactId>
                    <version>1.0</version>
                    <packaging>jar</packaging>
                    <dependencies>
                        <dependency>
                            <groupId>com.github.javaparser</groupId>
                            <artifactId>javaparser-core</artifactId>
                            <version>3.27.1</version>
                        </dependency>
                    </dependencies>
                </project>
                """
        );

        List<Path> jars = resolver.resolve(targetProject);

        assertThat(jars)
                .anyMatch(jar ->
                        jar.getFileName().toString()
                                .startsWith("javaparser-core")
                );
    }
}
