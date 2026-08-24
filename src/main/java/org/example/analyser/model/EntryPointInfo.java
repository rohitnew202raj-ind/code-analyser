package org.example.analyser.model;

/**
 * A single point where execution enters the application from
 * outside normal method-call flow - a REST/GraphQL endpoint, a
 * scheduled/event-driven trigger, a Spring Batch step, a bare
 * {@code main()}, or a startup runner.
 *
 * Replaces the previously separate {@code ApiInfo} and
 * {@code BatchProgramInfo} models. Both were "where does
 * execution start" facts with the same shape (a class, a
 * method, a domain, and a label for what triggers it) - keeping
 * them as two parallel lists meant every consumer that cares
 * about "all entry points" (starting with the flow engine) had
 * to know about both and merge them itself.
 */
public class EntryPointInfo {

    private String className;
    private String packageName;
    private String methodName;

    private TriggerType triggerType;

    /*
     * The REST path or GraphQL field name this entry point is
     * reachable at. Null for batch/scheduled/startup entry
     * points, which aren't reachable by a path at all.
     */
    private String path;

    private String domain;

    public EntryPointInfo() {
    }

    public EntryPointInfo(
            String className,
            String packageName,
            String methodName,
            TriggerType triggerType,
            String path,
            String domain) {

        this.className = className;
        this.packageName = packageName;
        this.methodName = methodName;
        this.triggerType = triggerType;
        this.path = path;
        this.domain = domain;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(TriggerType triggerType) {
        this.triggerType = triggerType;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }
}
