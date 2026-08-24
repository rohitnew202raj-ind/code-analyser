package com.acme.order;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderReportingService {

    @Autowired
    private OrderRepository orderRepository;

    public void reportOnOrder(Long id) {
        orderRepository.findById(id);
    }
}
