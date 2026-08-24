package com.acme.order;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderInvoiceService {

    @Autowired
    private OrderRepository orderRepository;

    public void generateInvoice(Long id) {
        orderRepository.findById(id);
    }
}
