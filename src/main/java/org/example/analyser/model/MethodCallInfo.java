package org.example.analyser.model;

public class MethodCallInfo {

    private String sourceClass;
    private String sourceMethod;

    private String targetClass;
    private String targetMethod;

    /*
     * Whether this call site is textually nested inside a
     * Java loop construct (for/while/do-while/for-each) within
     * its enclosing method. Defaults to false and is set
     * explicitly by MethodCallAnalyzer - see its javadoc for
     * what this does and doesn't detect (e.g. stream().forEach
     * lambdas are out of scope for v1).
     */
    private boolean insideLoop;

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

    public boolean isInsideLoop() {
        return insideLoop;
    }

    public void setInsideLoop(boolean insideLoop) {
        this.insideLoop = insideLoop;
    }
}