package org.example.analyser.model;

public enum ArchitectureFindingType {

    /**
     * A strongly connected component (size &gt; 1) in the
     * dependency graph - a group of classes that transitively
     * depend on each other.
     */
    CIRCULAR_DEPENDENCY,

    /**
     * A class with unusually high outgoing (efferent) coupling -
     * depends on too many other classes to plausibly have one
     * job.
     */
    GOD_CLASS,

    /**
     * A controller depending directly on a repository, skipping
     * the service layer.
     */
    REPOSITORY_BYPASS,

    /**
     * A service/repository/component class with no incoming
     * dependency edges that isn't itself an entry point -
     * likely nothing in the codebase actually uses it.
     */
    DEAD_COMPONENT
}
