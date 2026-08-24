package org.example.analyser.model;

public enum BeanResolutionVerdict {

    /**
     * Exactly one implementation is {@code @Primary} among two
     * or more candidates - Spring resolves to it unconditionally,
     * regardless of any {@code @Qualifier}/profile configuration
     * at individual injection sites.
     */
    RESOLVED_BY_PRIMARY,

    /**
     * Two or more real candidate implementations exist with no
     * single {@code @Primary} to disambiguate. Which one is
     * actually wired depends on {@code @Qualifier} at each
     * injection site and/or which Spring profile is active at
     * runtime - neither of which this tool resolves. Reported as
     * a list of candidates rather than guessed.
     */
    AMBIGUOUS
}
