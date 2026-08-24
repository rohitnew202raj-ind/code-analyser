package org.example.analyser.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassInfo {

    private String name;
    private String packageName;
    private String type;
    private boolean interfaceDeclaration;

    /*
     * How `type` was determined - see ClassificationSource. Set
     * by SpringComponentAnalyzer alongside `type` itself; null
     * only before classification has run.
     */
    private ClassificationSource typeSource;

    /*
     * Classes can legitimately carry more than one Spring
     * "role" (e.g. a @Component that is also @Aspect).
     * `type` stays the single primary classification used
     * by existing dependency/coupling logic; `roles` carries
     * every applicable role so that information isn't lost.
     */
    private List<String> roles =
            new ArrayList<>();

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

    /*
     * Same annotations, but normalized to their simple
     * (unqualified) name - e.g. both "@RestController" and
     * "@org.springframework...RestController" become
     * "RestController". Used for classification, where
     * matching on the raw text would miss fully-qualified
     * annotation usage.
     */
    private List<String> annotationSimpleNames =
            new ArrayList<>();

    private List<String> fields =
            new ArrayList<>();

    private List<String> methods =
            new ArrayList<>();

    /*
     * Combined extends + implements, kept for
     * backward compatibility with existing callers.
     * Prefer getExtendedTypes()/getImplementedTypes()
     * for anything that cares about the distinction.
     */
    private List<String> interfaces =
            new ArrayList<>();

    private List<String> extendedTypes =
            new ArrayList<>();

    private List<String> implementedTypes =
            new ArrayList<>();

    private List<DependencyInfo> dependencies =
            new ArrayList<>();

    /*
     * extends-clause type name -> its first generic type
     * argument (as written), when present. Used to resolve
     * repository entity types through a custom base
     * repository interface, e.g.:
     *
     * interface OrderRepository
     *         extends BaseRepository<OrderEntity, Long>
     *
     * -> {"BaseRepository": "OrderEntity"}
     */
    private Map<String, String> extendsTypeArguments =
            new HashMap<>();

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

    public String getFullyQualifiedName() {

        if (packageName == null
                || packageName.isBlank()) {

            return name;
        }

        return packageName + "." + name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ClassificationSource getTypeSource() {
        return typeSource;
    }

    public void setTypeSource(ClassificationSource typeSource) {
        this.typeSource = typeSource;
    }

    public boolean isInterfaceDeclaration() {
        return interfaceDeclaration;
    }

    public void setInterfaceDeclaration(boolean interfaceDeclaration) {
        this.interfaceDeclaration = interfaceDeclaration;
    }

    // =========================================================
    // ROLES
    // =========================================================

    public List<String> getRoles() {
        return roles;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
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

    public List<String> getAnnotationSimpleNames() {
        return annotationSimpleNames;
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

    /**
     * @deprecated combined extends+implements list, kept only
     * for backward compatibility. Use
     * {@link #getExtendedTypes()} / {@link #getImplementedTypes()}.
     */
    @Deprecated
    public List<String> getInterfaces() {
        return interfaces;
    }

    public List<String> getExtendedTypes() {
        return extendedTypes;
    }

    public List<String> getImplementedTypes() {
        return implementedTypes;
    }

    // =========================================================
    // DEPENDENCIES
    // =========================================================

    public List<DependencyInfo> getDependencies() {
        return dependencies;
    }

    public Map<String, String> getExtendsTypeArguments() {
        return extendsTypeArguments;
    }
}
