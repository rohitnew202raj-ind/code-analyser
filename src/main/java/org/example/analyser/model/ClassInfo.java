package org.example.analyser.model;

import java.util.ArrayList;
import java.util.List;

public class ClassInfo {

    private String name;
    private String packageName;
    private String type;

    /*
     * For Spring Data repositories.
     *
     * Example:
     *
     * OrderRepository
     *     -> OrderEntity
     *
     * CustomerRepository
     *     -> Customer
     */
    private String repositoryEntityType;

    private List<String> annotations =
            new ArrayList<>();

    private List<String> fields =
            new ArrayList<>();

    private List<String> methods =
            new ArrayList<>();

    private List<String> interfaces =
            new ArrayList<>();

    private List<DependencyInfo> dependencies =
            new ArrayList<>();

    // =========================================================
    // BASIC CLASS INFORMATION
    // =========================================================

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(
            String packageName) {

        this.packageName = packageName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    // =========================================================
    // REPOSITORY ENTITY TYPE
    // =========================================================

    public String getRepositoryEntityType() {
        return repositoryEntityType;
    }

    public void setRepositoryEntityType(
            String repositoryEntityType) {

        this.repositoryEntityType =
                repositoryEntityType;
    }

    // =========================================================
    // ANNOTATIONS
    // =========================================================

    public List<String> getAnnotations() {
        return annotations;
    }

    // =========================================================
    // FIELDS
    // =========================================================

    public List<String> getFields() {
        return fields;
    }

    // =========================================================
    // METHODS
    // =========================================================

    public List<String> getMethods() {
        return methods;
    }

    // =========================================================
    // INTERFACES / EXTENDS
    // =========================================================

    public List<String> getInterfaces() {
        return interfaces;
    }

    // =========================================================
    // DEPENDENCIES
    // =========================================================

    public List<DependencyInfo> getDependencies() {
        return dependencies;
    }
}