package com.acme.common;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("sms-enabled")
@Service
public class SmsNotificationSender implements NotificationSender {

    @Override
    public void send() {
    }
}
