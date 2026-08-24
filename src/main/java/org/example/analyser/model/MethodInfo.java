package org.example.analyser.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A single method declaration, as structured data rather than
 * just its name (all that was ever kept before this). Carries
 * method-level annotations - previously not captured anywhere in
 * this codebase at all, only class-level annotations were - which
 * is what makes a check like "flag a method with multiple writes
 * and no {@code @Transactional}" possible for a future analyzer;
 * none is added by this change, only the data it would need.
 */
public class MethodInfo {

    private String name;
    private String returnType;
    private List<ParameterInfo> parameters = new ArrayList<>();
    private List<String> annotations = new ArrayList<>();
    private List<String> annotationSimpleNames = new ArrayList<>();

    public MethodInfo() {
    }

    public MethodInfo(
            String name,
            String returnType,
            List<ParameterInfo> parameters,
            List<String> annotations,
            List<String> annotationSimpleNames) {

        this.name = name;
        this.returnType = returnType;
        this.parameters = parameters;
        this.annotations = annotations;
        this.annotationSimpleNames = annotationSimpleNames;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public List<ParameterInfo> getParameters() {
        return parameters;
    }

    public void setParameters(List<ParameterInfo> parameters) {
        this.parameters = parameters;
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
}
