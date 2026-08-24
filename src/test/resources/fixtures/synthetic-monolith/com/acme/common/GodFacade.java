package com.acme.common;

import com.acme.inventory.InventoryRepository;
import com.acme.inventory.InventoryService;
import com.acme.order.OrderRepository;
import com.acme.order.OrderService;
import com.acme.payment.PaymentRepository;
import com.acme.payment.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GodFacade {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentService paymentService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private InventoryService inventoryService;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private DepA depA;
    @Autowired private DepB depB;
    @Autowired private DepC depC;
    @Autowired private DepD depD;
}
