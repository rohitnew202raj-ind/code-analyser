package com.acme.order;

import com.acme.payment.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentService paymentService;

    public void getOrder(Long id) {
        orderRepository.findById(id);
    }

    public void placeOrder() {
        orderRepository.save(new Order());
        paymentService.charge();
    }
}
