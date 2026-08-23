package org.example.analyser.model;

public enum DomainBoundaryVerdict {

    /**
     * Structurally isolated: connected to few or no other
     * domains, and not part of a domain-level cycle. A
     * plausible microservice-extraction boundary.
     */
    EXTRACTION_CANDIDATE,

    /**
     * Connected to too many other domains to cleanly pull out
     * as-is.
     */
    TANGLED,

    /**
     * Part of a circular domain dependency - extraction
     * requires breaking the cycle first, regardless of raw
     * coupling counts.
     */
    BLOCKED_BY_CYCLE
}
