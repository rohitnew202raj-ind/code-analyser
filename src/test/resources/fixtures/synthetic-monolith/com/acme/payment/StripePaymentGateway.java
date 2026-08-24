package com.acme.payment;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
public class StripePaymentGateway implements PaymentGateway {

    @Override
    public void charge() {
    }
}
