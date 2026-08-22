package org.example.analyser.model;

public class MethodCallInfo {

    private String sourceClass;
    private String sourceMethod;

    private String targetClass;
    private String targetMethod;

    public MethodCallInfo() {
    }

    public MethodCallInfo(
            String sourceClass,
            String sourceMethod,
            String targetClass,
            String targetMethod) {

        this.sourceClass = sourceClass;
        this.sourceMethod = sourceMethod;
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
    }

    public String getSourceClass() {
        return sourceClass;
    }

    public void setSourceClass(String sourceClass) {
        this.sourceClass = sourceClass;
    }

    public String getSourceMethod() {
        return sourceMethod;
    }

    public void setSourceMethod(String sourceMethod) {
        this.sourceMethod = sourceMethod;
    }

    public String getTargetClass() {
        return targetClass;
    }

    public void setTargetClass(String targetClass) {
        this.targetClass = targetClass;
    }

    public String getTargetMethod() {
        return targetMethod;
    }

    public void setTargetMethod(String targetMethod) {
        this.targetMethod = targetMethod;
    }
}