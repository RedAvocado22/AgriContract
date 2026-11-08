package com.agricontract.contract.infrastructure.config;

import com.agricontract.calendar.VietnamBusinessDays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The shared Vietnam business-day calendar (#295), from VIETNAM_HOLIDAYS. Missing or invalid: startup fails. */
@Configuration
public class BusinessCalendarConfiguration {

    @Bean
    public VietnamBusinessDays vietnamBusinessDays(@Value("${contract.calendar.vietnam-holidays}") String holidays) {
        return VietnamBusinessDays.fromConfiguration(holidays);
    }
}
