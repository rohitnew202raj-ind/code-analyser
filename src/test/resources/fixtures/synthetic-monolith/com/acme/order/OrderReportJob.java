package com.acme.order;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderReportJob {

    @Autowired
    private OrderRepository orderRepository;

    @Scheduled(cron = "0 0 1 * * *")
    public void runDailyReport() {

        List<Long> ids = List.of(1L, 2L, 3L);

        for (Long id : ids) {
            orderRepository.findById(id);
        }
    }
}
