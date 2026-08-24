package org.example.analyser.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A single field declaration, as structured data rather than the
 * raw declaration text this used to be stored as (see
 * {@code ClassAnalyzer}'s javadoc). Deliberately does not capture
 * the field's initializer/value: nothing in this codebase has
 * ever needed it, and dropping it outright - rather than storing
 * it and redacting secret-looking values after the fact - is what
 * makes the whole "field text is stored verbatim, so a hardcoded
 * secret literal would leak into the report unless redacted"
 * problem disappear instead of needing a fix.
 */
public class FieldInfo {

    private String name;
    private String type;
    private List<String> annotations = new ArrayList<>();
    private List<String> annotationSimpleNames = new ArrayList<>();
    private boolean isStatic;
    private boolean isFinal;

    public FieldInfo() {
    }

    public FieldInfo(
            String name,
            String type,
            List<String> annotations,
            List<String> annotationSimpleNames,
            boolean isStatic,
            boolean isFinal) {

        this.name = name;
        this.type = type;
        this.annotations = annotations;
        this.annotationSimpleNames = annotationSimpleNames;
        this.isStatic = isStatic;
        this.isFinal = isFinal;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(List<String> annotations) {
        this.annotations = annotations;
    }

    public List<String> getAnnotationSimpleNames() {
        return annotationSimpleNames;
    }

    public void setAnnotationSimpleNames(List<String> annotationSimpleNames) {
        this.annotationSimpleNames = annotationSimpleNames;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public void setStatic(boolean isStatic) {
        this.isStatic = isStatic;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean isFinal) {
        this.isFinal = isFinal;
    }
}
