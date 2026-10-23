package com.agricontract.eventing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.*;

class EventSubjectTest {

    @ParameterizedTest
    @EnumSource(SubjectType.class)
    void create_everySubjectType_isValid(SubjectType type) {
        EventSubject subject = new EventSubject(type, "subject-1");

        assertThat(subject.type()).isEqualTo(type);
        assertThat(subject.id()).isEqualTo("subject-1");
    }

    @Test
    void create_nullType_throws() {
        assertThatThrownBy(() -> new EventSubject(null, "subject-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subject.type");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void create_missingId_throws(String id) {
        assertThatThrownBy(() -> new EventSubject(SubjectType.CONTRACT, id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subject.id");
    }
}
