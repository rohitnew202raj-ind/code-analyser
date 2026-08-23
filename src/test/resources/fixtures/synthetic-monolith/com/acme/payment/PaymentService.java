package com.acme.payment;

import com.acme.order.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentRepository paymentRepository;

    public void charge() {
        paymentRepository.save(new Payment());
    }
}
