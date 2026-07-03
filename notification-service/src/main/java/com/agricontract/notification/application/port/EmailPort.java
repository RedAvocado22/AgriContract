package com.agricontract.notification.application.port;

public interface EmailPort {
    void sendEmail(String to, String subject, String content);
}
