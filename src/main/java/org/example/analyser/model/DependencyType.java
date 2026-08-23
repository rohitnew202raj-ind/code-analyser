package org.example.analyser.model;

public enum DependencyType {

    SERVICE_DEPENDENCY,

    REPOSITORY_DEPENDENCY,

    /**
     * Dependency on a plain {@code @Component} - a mapper,
     * validator, converter, interceptor, etc. that isn't a
     * service or repository.
     */
    COMPONENT_DEPENDENCY,

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

    /**
     * Resolved target that isn't a service, repository, or
     * component - e.g. a field of a DTO, event, or exception
     * type.
     */
    OTHER_DEPENDENCY
}