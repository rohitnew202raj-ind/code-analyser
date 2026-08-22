package org.example.analyser.model;

public class EntityMutationInfo {

    private String sourceClass;
    private String sourceMethod;

    private String entityClass;
    private String fieldName;

    private String operation;
    private String tableName;

    public EntityMutationInfo() {
    }

    public EntityMutationInfo(
            String sourceClass,
            String sourceMethod,
            String entityClass,
            String fieldName,
            String operation,
            String tableName) {

        this.sourceClass = sourceClass;
        this.sourceMethod = sourceMethod;
        this.entityClass = entityClass;
        this.fieldName = fieldName;
        this.operation = operation;
        this.tableName = tableName;
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

    public String getEntityClass() {
        return entityClass;
    }

    public void setEntityClass(String entityClass) {
        this.entityClass = entityClass;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
}