package org.example.analyser;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class ArchitectureAnalyzerApplication {

    /*
     * This is a CLI analysis tool, not a web service - despite
     * spring-boot-starter-web being on the classpath (for
     * Jackson, used by ReportExporter). Without this, Spring
     * Boot starts a full embedded Tomcat server on every run,
     * which does nothing useful here and keeps the JVM alive
     * after the analysis finishes instead of exiting cleanly.
     */
    public static void main(String[] args) {
        new SpringApplicationBuilder(ArchitectureAnalyzerApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
