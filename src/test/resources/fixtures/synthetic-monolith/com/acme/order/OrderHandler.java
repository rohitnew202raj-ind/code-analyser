package com.acme.order;

import org.springframework.stereotype.Component;

@Component
public class OrderHandler {

    public String getOrder() {
        return "order";
    }

    public String createOrder() {
        return "created";
    }
}
