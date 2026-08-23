package org.example.analyser.model;

public enum DependencyType {

    SERVICE_DEPENDENCY,

    REPOSITORY_DEPENDENCY,

    ENTITY_RELATIONSHIP,

    /**
     * Dependency obtained via ApplicationContext.getBean(...)
     * instead of field injection.
     */
    RUNTIME_LOOKUP_DEPENDENCY,

    /**
     * Dependency obtained via direct instantiation
     * (new SomeApplicationClass()) instead of DI.
     */
    DIRECT_INSTANTIATION_DEPENDENCY,

    UNKNOWN
}