package org.example.analyser.analyzer;

import com.github.javaparser.ast.expr.AnnotationExpr;

/**
 * Normalizes an annotation's name to its simple (unqualified)
 * form.
 *
 * JavaParser's AnnotationExpr#getNameAsString() returns the
 * name exactly as written, so a fully-qualified usage like
 * {@code @org.springframework.web.bind.annotation.RestController}
 * returns the whole dotted string, not just "RestController".
 * Every place in this codebase that matched annotation names
 * with a literal equals() (e.g. "RestController", "Service")
 * would silently miss any fully-qualified usage - a real
 * pattern in large codebases avoiding import collisions.
 */
public final class AnnotationNames {

    private AnnotationNames() {
    }

    public static String simpleName(AnnotationExpr annotation) {
        return simpleName(annotation.getNameAsString());
    }

    public static String simpleName(String possiblyQualifiedName) {

        if (possiblyQualifiedName == null) {
            return null;
        }

        int lastDot =
                possiblyQualifiedName.lastIndexOf('.');

        if (lastDot < 0) {
            return possiblyQualifiedName;
        }

        return possiblyQualifiedName.substring(lastDot + 1);
    }
}
