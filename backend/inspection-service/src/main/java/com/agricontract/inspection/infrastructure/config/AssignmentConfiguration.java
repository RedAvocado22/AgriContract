package com.agricontract.inspection.infrastructure.config;

import com.agricontract.calendar.VietnamBusinessDays;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AssignmentConfiguration {
    @Bean
    VietnamBusinessDays vietnamBusinessDays(@Value("${VIETNAM_HOLIDAYS}") String holidays) {
        return VietnamBusinessDays.fromConfiguration(holidays);
    }

    @Bean
    RandomGenerator inspectorRandom() { return new java.security.SecureRandom(); }
}
