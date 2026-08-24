package org.example.analyser.model;

/**
 * How {@link ClassInfo#getType()} was determined - so a
 * consumer of {@code report.json} can tell a confirmed fact
 * from an educated guess instead of treating every classified
 * class the same way.
 */
public enum ClassificationSource {

    /**
     * Directly (or transitively, through a composed/meta
     * annotation) carries the Spring stereotype annotation that
     * determines this type - e.g. {@code @Service}. High
     * confidence: the framework itself will treat this class
     * this way at runtime.
     */
    ANNOTATION,

    /**
     * Determined from an AST-confirmed structural fact that
     * isn't an annotation - extending a known repository/
     * exception supertype, or literally being an
     * {@code interface} declaration. High confidence, but
     * listed separately from ANNOTATION since it's a different
     * kind of evidence (inheritance/declaration shape, not a
     * stereotype the framework itself reads).
     */
    STRUCTURAL,

    /**
     * Guessed from the class's name (e.g. a {@code *Dto} or
     * {@code *Event} suffix) because no stereotype annotation or
     * structural signal was found. Lower confidence - a class
     * named {@code OrderDtoValidator} would match none of these
     * suffixes and a class that happens to end in {@code Dto}
     * without being one would be misclassified. Prefer
     * ANNOTATION/STRUCTURAL evidence over this whenever it's
     * available.
     */
    NAMING_HEURISTIC,

    /**
     * No annotation, structural signal, or naming pattern
     * matched anything - the type is the final catch-all
     * default ({@code POJO}), not a guess that happened to be
     * wrong. Distinct from NAMING_HEURISTIC because no heuristic
     * actually fired here.
     */
    NONE
}
