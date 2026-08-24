package org.example.analyser.model;

import java.util.List;

/**
 * The resolution outcome for one interface with two or more
 * candidate Spring-managed implementations - "when code
 * @Autowires this interface, which concrete class actually gets
 * wired?" See {@link BeanResolutionVerdict} for what each
 * verdict means.
 */
public class BeanResolution {

    private String interfaceName;
    private List<String> candidateImplementations;
    private BeanResolutionVerdict verdict;

    /*
     * The implementation class name Spring resolves to, only
     * set when verdict is RESOLVED_BY_PRIMARY. Null for
     * AMBIGUOUS - deliberately not guessed.
     */
    private String resolvedImplementation;

    private String description;

    public BeanResolution() {
    }

    public BeanResolution(
            String interfaceName,
            List<String> candidateImplementations,
            BeanResolutionVerdict verdict,
            String resolvedImplementation,
            String description) {

        this.interfaceName = interfaceName;
        this.candidateImplementations = candidateImplementations;
        this.verdict = verdict;
        this.resolvedImplementation = resolvedImplementation;
        this.description = description;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public List<String> getCandidateImplementations() {
        return candidateImplementations;
    }

    public void setCandidateImplementations(
            List<String> candidateImplementations) {

        this.candidateImplementations = candidateImplementations;
    }

    public BeanResolutionVerdict getVerdict() {
        return verdict;
    }

    public void setVerdict(BeanResolutionVerdict verdict) {
        this.verdict = verdict;
    }

    public String getResolvedImplementation() {
        return resolvedImplementation;
    }

    public void setResolvedImplementation(String resolvedImplementation) {
        this.resolvedImplementation = resolvedImplementation;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
