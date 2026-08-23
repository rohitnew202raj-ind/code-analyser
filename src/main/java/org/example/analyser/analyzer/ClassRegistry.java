package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fast, single-build lookup over the classes discovered in
 * the target project.
 *
 * LIMITATION (documented, deliberately not eliminated):
 * without full compile-time classpath resolution of the
 * target project, we cannot always tell which of two
 * same-named classes in different packages a given usage
 * refers to. Where a fully-qualified name is available
 * (typically via the JavaParser Symbol Solver resolving an
 * expression), use {@link #findByFqn(String)}. Where only a
 * simple name is available and it is ambiguous across the
 * project, {@link #findBySimpleName(String)} now returns
 * null instead of silently guessing the first match - a
 * caller gets "unresolved" rather than "resolved to the
 * wrong class."
 */
public class ClassRegistry {

    private final Map<String, ClassInfo> byFqn =
            new HashMap<>();

    private final Map<String, ClassInfo> unambiguousBySimpleName =
            new HashMap<>();

    public ClassRegistry(List<ClassInfo> classes) {

        Map<String, Integer> nameCounts =
                new HashMap<>();

        for (ClassInfo classInfo : classes) {

            byFqn.put(
                    classInfo.getFullyQualifiedName(),
                    classInfo
            );

            nameCounts.merge(
                    classInfo.getName(),
                    1,
                    Integer::sum
            );
        }

        for (ClassInfo classInfo : classes) {

            if (nameCounts.get(classInfo.getName()) == 1) {

                unambiguousBySimpleName.put(
                        classInfo.getName(),
                        classInfo
                );
            }
        }
    }

    public ClassInfo findByFqn(String fqn) {

        if (fqn == null) {
            return null;
        }

        return byFqn.get(fqn);
    }

    /**
     * Resolves a simple name only when it is unambiguous
     * across the whole scanned project. Returns null for a
     * name that either does not exist, or exists more than
     * once in different packages.
     */
    public ClassInfo findBySimpleName(String simpleName) {

        if (simpleName == null) {
            return null;
        }

        return unambiguousBySimpleName.get(simpleName);
    }

    /**
     * Best-effort resolution: try a fully-qualified name
     * first (if one is available), then fall back to an
     * unambiguous simple-name match.
     */
    public ClassInfo resolve(String fqnOrSimpleName) {

        if (fqnOrSimpleName == null) {
            return null;
        }

        ClassInfo byQualifiedName =
                findByFqn(fqnOrSimpleName);

        if (byQualifiedName != null) {
            return byQualifiedName;
        }

        return findBySimpleName(fqnOrSimpleName);
    }
}
