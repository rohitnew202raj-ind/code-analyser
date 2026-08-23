package org.example.analyser.model;

public class ClassCouplingInfo {

    private final String className;
    private final String packageName;
    private final String type;

    private final int outgoingDependencies;
    private final int incomingDependencies;

    /*
     * Structural coupling (above) counts distinct
     * dependency *edges* - a class touched once and a class
     * hammered by every caller look the same. This adds a
     * call-frequency-based weight (from the actual method
     * call graph) as a second, more load-aware signal.
     */
    private final int callWeight;

    public ClassCouplingInfo(
            String className,
            String packageName,
            String type,
            int outgoingDependencies,
            int incomingDependencies,
            int callWeight) {

        this.className = className;
        this.packageName = packageName;
        this.type = type;
        this.outgoingDependencies = outgoingDependencies;
        this.incomingDependencies = incomingDependencies;
        this.callWeight = callWeight;
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

    public int getCallWeight() {
        return callWeight;
    }
}