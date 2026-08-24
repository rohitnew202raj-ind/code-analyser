package org.example.analyser.analyzer;

import org.example.analyser.model.BeanResolution;
import org.example.analyser.model.BeanResolutionVerdict;
import org.example.analyser.model.ClassInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BeanResolutionAnalyzerTest {

    private final BeanResolutionAnalyzer analyzer =
            new BeanResolutionAnalyzer();

    @Test
    void ignoresAnInterfaceWithOnlyOneImplementation() {

        ClassInfo impl = service("OrderServiceImpl", "OrderService");

        List<BeanResolution> resolutions =
                analyzer.analyze(List.of(impl));

        assertThat(resolutions).isEmpty();
    }

    @Test
    void resolvesByPrimaryWhenExactlyOneImplementationIsPrimary() {

        ClassInfo stripe = service("StripePaymentGateway", "PaymentGateway");
        stripe.getAnnotationSimpleNames().add("Primary");

        ClassInfo paypal = service("PaypalPaymentGateway", "PaymentGateway");

        List<BeanResolution> resolutions =
                analyzer.analyze(List.of(stripe, paypal));

        assertThat(resolutions).hasSize(1);

        BeanResolution resolution = resolutions.get(0);

        assertThat(resolution.getInterfaceName()).isEqualTo("PaymentGateway");
        assertThat(resolution.getVerdict())
                .isEqualTo(BeanResolutionVerdict.RESOLVED_BY_PRIMARY);
        assertThat(resolution.getResolvedImplementation())
                .isEqualTo("StripePaymentGateway");
        assertThat(resolution.getCandidateImplementations())
                .containsExactlyInAnyOrder(
                        "StripePaymentGateway", "PaypalPaymentGateway"
                );
    }

    @Test
    void reportsAmbiguousWhenNoImplementationIsPrimary() {

        ClassInfo email = service("EmailNotificationSender", "NotificationSender");
        ClassInfo sms = service("SmsNotificationSender", "NotificationSender");

        List<BeanResolution> resolutions =
                analyzer.analyze(List.of(email, sms));

        assertThat(resolutions).hasSize(1);

        BeanResolution resolution = resolutions.get(0);

        assertThat(resolution.getVerdict())
                .isEqualTo(BeanResolutionVerdict.AMBIGUOUS);
        assertThat(resolution.getResolvedImplementation()).isNull();
        assertThat(resolution.getDescription())
                .contains("EmailNotificationSender")
                .contains("SmsNotificationSender");
    }

    @Test
    void reportsAmbiguousWhenTwoImplementationsAreBothPrimary() {

        // Would not actually compile/start under real Spring, but
        // this tool doesn't guess a winner just because a real
        // config wouldn't reach this state - it reports what it
        // sees.
        ClassInfo first = service("FirstImpl", "Contract");
        first.getAnnotationSimpleNames().add("Primary");

        ClassInfo second = service("SecondImpl", "Contract");
        second.getAnnotationSimpleNames().add("Primary");

        List<BeanResolution> resolutions =
                analyzer.analyze(List.of(first, second));

        assertThat(resolutions.get(0).getVerdict())
                .isEqualTo(BeanResolutionVerdict.AMBIGUOUS);
    }

    @Test
    void includesProfileValueInAmbiguousDescription() {

        ClassInfo email = service("EmailNotificationSender", "NotificationSender");

        ClassInfo sms = service("SmsNotificationSender", "NotificationSender");
        sms.getAnnotations().add("@Profile(\"sms-enabled\")");

        List<BeanResolution> resolutions =
                analyzer.analyze(List.of(email, sms));

        assertThat(resolutions.get(0).getDescription())
                .contains("SmsNotificationSender [@Profile(sms-enabled)]");
    }

    @Test
    void excludesImplementorsThatAreNotSpringManagedBeans() {

        // A plain class implementing the interface (a test
        // double, a manual instantiation) isn't a real candidate
        // Spring would ever choose between.
        ClassInfo realImpl = service("OrderServiceImpl", "OrderService");

        ClassInfo notABean = new ClassInfo();
        notABean.setName("FakeOrderService");
        notABean.setType("POJO");
        notABean.getImplementedTypes().add("OrderService");

        List<BeanResolution> resolutions =
                analyzer.analyze(List.of(realImpl, notABean));

        assertThat(resolutions).isEmpty();
    }

    private ClassInfo service(String name, String implementedInterface) {

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName(name);
        classInfo.setType("SERVICE");
        classInfo.getImplementedTypes().add(implementedInterface);
        return classInfo;
    }
}
