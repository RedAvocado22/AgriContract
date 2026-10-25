package com.agricontract.inspection.infrastructure.config;

import com.agricontract.inspection.application.service.VietnamBusinessDays;
import java.time.LocalDate;
import java.util.*;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AssignmentConfiguration {
    @Bean
    VietnamBusinessDays vietnamBusinessDays(@Value("${inspection.assignment.holidays}") String holidays) {
        if (holidays.isBlank()) throw new IllegalArgumentException("Configure Vietnam public holidays for the operating years");
        var dates = Arrays.stream(holidays.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).map(LocalDate::parse).collect(Collectors.toSet());
        if (dates.isEmpty()) throw new IllegalArgumentException("Configure at least one Vietnam public holiday");
        return new VietnamBusinessDays(dates);
    }

    @Bean
    RandomGenerator inspectorRandom() { return new java.security.SecureRandom(); }
}
