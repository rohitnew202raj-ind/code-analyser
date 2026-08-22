package org.example.analyser.model;

public class CrudOperationInfo {

    private String sourceClass;
    private String sourceMethod;

    private String repositoryClass;
    private String repositoryMethod;

    private String operation;
    private String entityClass;
    private String tableName;

    public CrudOperationInfo() {
    }

    public CrudOperationInfo(
            String sourceClass,
            String sourceMethod,
            String repositoryClass,
            String repositoryMethod,
            String operation,
            String entityClass,
            String tableName) {

        this.sourceClass = sourceClass;
        this.sourceMethod = sourceMethod;
        this.repositoryClass = repositoryClass;
        this.repositoryMethod = repositoryMethod;
        this.operation = operation;
        this.entityClass = entityClass;
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

    public String getRepositoryClass() {
        return repositoryClass;
    }

    public void setRepositoryClass(String repositoryClass) {
        this.repositoryClass = repositoryClass;
    }

    public String getRepositoryMethod() {
        return repositoryMethod;
    }

    public void setRepositoryMethod(String repositoryMethod) {
        this.repositoryMethod = repositoryMethod;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getEntityClass() {
        return entityClass;
    }

    public void setEntityClass(String entityClass) {
        this.entityClass = entityClass;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
}