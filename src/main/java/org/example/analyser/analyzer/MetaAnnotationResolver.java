package org.example.analyser.analyzer;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves composed ("meta") annotations declared inside the
 * scanned project.
 *
 * Spring lets a project define its own annotation that is
 * itself annotated with a stereotype, e.g.:
 *
 * @RestController
 * @RequestMapping
 * public @interface ApiController { }
 *
 * A class annotated only with @ApiController is still a REST
 * controller, but a plain name-equality check against
 * "RestController" would miss it entirely. This walks the
 * annotation-declares-annotation graph (built from
 * AnnotationDeclaration nodes found while scanning) to answer
 * "does this annotation carry that stereotype, directly or
 * transitively".
 *
 * Not a Spring bean: the registry depends on the scan result,
 * so it is built once by AnalyzerRunner after parsing and
 * passed explicitly to whatever needs it.
 */
public class MetaAnnotationResolver {

    public static final MetaAnnotationResolver EMPTY =
            new MetaAnnotationResolver(Map.of());

    private final Map<String, List<String>> annotationsByDeclaredAnnotation;

    public MetaAnnotationResolver(
            Map<String, List<String>> annotationsByDeclaredAnnotation) {

        this.annotationsByDeclaredAnnotation =
                annotationsByDeclaredAnnotation;
    }

    /**
     * True if {@code annotationSimpleName} is (or transitively
     * carries) {@code target}.
     */
    public boolean carries(
            String annotationSimpleName,
            String target) {

        return carries(
                annotationSimpleName,
                target,
                new HashSet<>()
        );
    }

    private boolean carries(
            String annotationSimpleName,
            String target,
            Set<String> visited) {

        if (annotationSimpleName == null) {
            return false;
        }

        if (annotationSimpleName.equals(target)) {
            return true;
        }

        if (!visited.add(annotationSimpleName)) {
            // Cycle guard - already visited this annotation
            // on this path.
            return false;
        }

        List<String> parents =
                annotationsByDeclaredAnnotation
                        .getOrDefault(
                                annotationSimpleName,
                                List.of()
                        );

        for (String parent : parents) {

            if (carries(parent, target, visited)) {
                return true;
            }
        }

        return false;
    }
}
