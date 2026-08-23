package org.example.analyser.analyzer;

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
    }

    @Test
    void classifiesUnannotatedClassEndingInDtoAsDto() {

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("CreateOrderRequestDto");

        analyzer.classify(classInfo, MetaAnnotationResolver.EMPTY);

        assertThat(classInfo.getType()).isEqualTo("DTO");
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
    }

    @Test
    void classifiesExceptionByNameSuffixEvenWithoutResolvedSupertype() {

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("OrderValidationException");

        analyzer.classify(classInfo, MetaAnnotationResolver.EMPTY);

        assertThat(classInfo.getType()).isEqualTo("EXCEPTION");
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
    }

    @Test
    void classifiesUnannotatedPlainClassAsPojoNotUnknown() {

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("ApiResponse");

        analyzer.classify(classInfo, MetaAnnotationResolver.EMPTY);

        assertThat(classInfo.getType())
                .isEqualTo("POJO")
                .isNotEqualTo("UNKNOWN");
    }
}
