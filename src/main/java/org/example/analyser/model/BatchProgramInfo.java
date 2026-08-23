package org.example.analyser.model;

public class BatchProgramInfo {

    private final String className;
    private final String packageName;
    private final String methodName;
    private final String trigger;
    private final String domain;

    public BatchProgramInfo(
            String className,
            String packageName,
            String methodName,
            String trigger,
            String domain) {

        this.className = className;
        this.packageName = packageName;
        this.methodName = methodName;
        this.trigger = trigger;
        this.domain = domain;
    }

    public String getClassName() {
        return className;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getTrigger() {
        return trigger;
    }

    public String getDomain() {
        return domain;
    }
}
