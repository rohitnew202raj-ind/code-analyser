package org.example.analyser.analyzer;

import org.example.analyser.model.ClassificationSource;
import org.example.analyser.model.ClassInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringComponentAnalyzerTest {

    private final SpringComponentAnalyzer analyzer =
            new SpringComponentAnalyzer();

    @Test
    void classifiesAnnotatedClassByStereotype() {

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("OrderService");
        classInfo.getAnnotationSimpleNames().add("Service");

        analyzer.classify(classInfo, MetaAnnotationResolver.EMPTY);

        assertThat(classInfo.getType()).isEqualTo("SERVICE");
        assertThat(classInfo.getTypeSource())
                .isEqualTo(ClassificationSource.ANNOTATION);
    }

    @Test
    void classifiesUnannotatedClassEndingInDtoAsDto() {

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("CreateOrderRequestDto");

        analyzer.classify(classInfo, MetaAnnotationResolver.EMPTY);

        assertThat(classInfo.getType()).isEqualTo("DTO");
        assertThat(classInfo.getTypeSource())
                .isEqualTo(ClassificationSource.NAMING_HEURISTIC);
    }

    @Test
    void classifiesUnannotatedClassEndingInEventAsEvent() {

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("OrderCreatedEvent");

        analyzer.classify(classInfo, MetaAnnotationResolver.EMPTY);

        assertThat(classInfo.getType()).isEqualTo("EVENT");
    }

    @Test
    void classifiesClassExtendingRuntimeExceptionAsException() {

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("OrderNotFoundException");
        classInfo.getExtendedTypes().add("RuntimeException");

        analyzer.classify(classInfo, MetaAnnotationResolver.EMPTY);

        assertThat(classInfo.getType()).isEqualTo("EXCEPTION");
        assertThat(classInfo.getTypeSource())
                .isEqualTo(ClassificationSource.STRUCTURAL);
    }

    @Test
    void classifiesExceptionByNameSuffixEvenWithoutResolvedSupertype() {

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("OrderValidationException");

        analyzer.classify(classInfo, MetaAnnotationResolver.EMPTY);

        assertThat(classInfo.getType()).isEqualTo("EXCEPTION");

        // Name suffix only, no resolved supertype - a guess,
        // not a confirmed structural fact.
        assertThat(classInfo.getTypeSource())
                .isEqualTo(ClassificationSource.NAMING_HEURISTIC);
    }

    @Test
    void classifiesUnannotatedClassEndingInConstantsAsConstants() {

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("OrderConstants");

        analyzer.classify(classInfo, MetaAnnotationResolver.EMPTY);

        assertThat(classInfo.getType()).isEqualTo("CONSTANTS");
    }

    @Test
    void classifiesUnannotatedClassEndingInSpecificationAsSpecification() {

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("OrderSpecification");

        analyzer.classify(classInfo, MetaAnnotationResolver.EMPTY);

        assertThat(classInfo.getType()).isEqualTo("SPECIFICATION");
    }

    @Test
    void classifiesUnannotatedClassEndingInHelperOrUtilsAsUtility() {

        ClassInfo helper = new ClassInfo();
        helper.setName("OrderHelper");
        analyzer.classify(helper, MetaAnnotationResolver.EMPTY);
        assertThat(helper.getType()).isEqualTo("UTILITY");

        ClassInfo utils = new ClassInfo();
        utils.setName("StringUtils");
        analyzer.classify(utils, MetaAnnotationResolver.EMPTY);
        assertThat(utils.getType()).isEqualTo("UTILITY");
    }

    @Test
    void classifiesUnannotatedPlainInterfaceAsInterface() {

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("SomeCustomContract");
        classInfo.setInterfaceDeclaration(true);

        analyzer.classify(classInfo, MetaAnnotationResolver.EMPTY);

        assertThat(classInfo.getType()).isEqualTo("INTERFACE");

        // Being an `interface` at all is a confirmed AST fact,
        // not a name-based guess.
        assertThat(classInfo.getTypeSource())
                .isEqualTo(ClassificationSource.STRUCTURAL);
    }

    @Test
    void classifiesUnannotatedPlainClassAsPojoNotUnknown() {

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("ApiResponse");

        analyzer.classify(classInfo, MetaAnnotationResolver.EMPTY);

        assertThat(classInfo.getType())
                .isEqualTo("POJO")
                .isNotEqualTo("UNKNOWN");

        // No heuristic actually fired to produce POJO - it's
        // the last-resort default, not a guess that missed.
        assertThat(classInfo.getTypeSource())
                .isEqualTo(ClassificationSource.NONE);
    }

    @Test
    void classifiesRepositoryAnnotatedInterfaceAsAnnotationSourced() {

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("OrderRepository");
        classInfo.getAnnotationSimpleNames().add("Repository");

        analyzer.classify(classInfo, MetaAnnotationResolver.EMPTY);

        assertThat(classInfo.getType()).isEqualTo("REPOSITORY");
        assertThat(classInfo.getTypeSource())
                .isEqualTo(ClassificationSource.ANNOTATION);
    }

    @Test
    void classifiesRepositoryExtendingJpaRepositoryAsStructurallySourced() {

        // No @Repository annotation at all - Spring Data derives
        // the bean purely from extending the marker interface,
        // so this is a structural fact, not an annotation match.
        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("OrderRepository");
        classInfo.getExtendedTypes().add("JpaRepository");

        analyzer.classify(classInfo, MetaAnnotationResolver.EMPTY);

        assertThat(classInfo.getType()).isEqualTo("REPOSITORY");
        assertThat(classInfo.getTypeSource())
                .isEqualTo(ClassificationSource.STRUCTURAL);
    }
}
