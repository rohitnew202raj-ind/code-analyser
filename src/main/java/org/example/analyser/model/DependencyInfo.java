package org.example.analyser.model;

public class DependencyInfo {

    private String sourceClass;
    private String targetClass;
    private String fieldName;
    private DependencyType type;

    public DependencyInfo() {
    }

    public DependencyInfo(
            String sourceClass,
            String targetClass,
            String fieldName,
            DependencyType type) {

        this.sourceClass = sourceClass;
        this.targetClass = targetClass;
        this.fieldName = fieldName;
        this.type = type;
    }

    public String getSourceClass() {
        return sourceClass;
    }

    public void setSourceClass(String sourceClass) {
        this.sourceClass = sourceClass;
    }

    public String getTargetClass() {
        return targetClass;
    }

    public void setTargetClass(String targetClass) {
        this.targetClass = targetClass;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public DependencyType getType() {
        return type;
    }

    public void setType(DependencyType type) {
        this.type = type;
    }
}