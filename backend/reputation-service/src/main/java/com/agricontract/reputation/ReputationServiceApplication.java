package com.agricontract.reputation;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ReputationServiceApplication {
    public static void main(String[] args) { SpringApplication.run(ReputationServiceApplication.class, args); }
}
