package org.example.analyser.model;

public class ClassCouplingInfo {

    private final String className;
    private final String packageName;
    private final String type;

    private final int outgoingDependencies;
    private final int incomingDependencies;

    public ClassCouplingInfo(
            String className,
            String packageName,
            String type,
            int outgoingDependencies,
            int incomingDependencies) {

        this.className = className;
        this.packageName = packageName;
        this.type = type;
        this.outgoingDependencies = outgoingDependencies;
        this.incomingDependencies = incomingDependencies;
    }

    public String getClassName() {
        return className;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getType() {
        return type;
    }

    public int getOutgoingDependencies() {
        return outgoingDependencies;
    }

    public int getIncomingDependencies() {
        return incomingDependencies;
    }

    public int getTotalCoupling() {
        return outgoingDependencies + incomingDependencies;
    }
}