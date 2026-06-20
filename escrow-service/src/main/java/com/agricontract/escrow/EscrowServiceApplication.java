package com.agricontract.escrow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class EscrowServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EscrowServiceApplication.class, args);
    }
}
