package com.agricontract.inspection.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class AssignmentConfigurationTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", ",,,", " , , ", "not-a-date", "2026-02-30"})
    void invalidCalendarCannotSilentlyBecomeWeekendOnly(String value) {
        new ApplicationContextRunner().withUserConfiguration(AssignmentConfiguration.class)
                .withBean(PropertySourcesPlaceholderConfigurer.class, PropertySourcesPlaceholderConfigurer::new)
                .withPropertyValues("VIETNAM_HOLIDAYS=" + value)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void missingConfigurationFailsStartup() {
        new ApplicationContextRunner().withUserConfiguration(AssignmentConfiguration.class)
                .withBean(PropertySourcesPlaceholderConfigurer.class, PropertySourcesPlaceholderConfigurer::new)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void validExternalConfigurationIsUsed() {
        new ApplicationContextRunner().withUserConfiguration(AssignmentConfiguration.class)
                .withBean(PropertySourcesPlaceholderConfigurer.class, PropertySourcesPlaceholderConfigurer::new)
                .withPropertyValues("VIETNAM_HOLIDAYS=2026-10-26, 2027-01-01")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var calendar = context.getBean(com.agricontract.calendar.VietnamBusinessDays.class);
                    assertThat(calendar.deadline(Instant.parse("2026-10-23T03:00:00Z"), 1))
                            .isEqualTo(Instant.parse("2026-10-27T16:59:59.999Z"));
                });
    }
}
