package com.acme.payment;

import org.springframework.stereotype.Service;

@Service
public class PaypalPaymentGateway implements PaymentGateway {

    @Override
    public void charge() {
    }
}
