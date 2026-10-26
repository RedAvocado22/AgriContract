package com.agricontract.inspection.domain.model;

import java.lang.reflect.Modifier;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReportIdentityTest {
    @Test void allocationHasNoCallerSuppliedReportIdAndIdentityHasNoMutableFields() {
        var assignment=new InspectionAssignment("commission","contract","milestone","inspector",Instant.EPOCH,null,"contract-service",null);
        var first=ReportIdentity.allocate(assignment);
        assertThat(first.reportId()).isNotEqualTo(ReportIdentity.allocate(assignment).reportId());
        assertThat(first.assignment()).isEqualTo(assignment);
        assertThat(ReportIdentity.class.getConstructors()).isEmpty();
        for (var field:ReportIdentity.class.getDeclaredFields()) {
            assertThat(Modifier.isPrivate(field.getModifiers())).isTrue();
            assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
        }
    }

    @Test void absentCommitmentsCannotBecomeAConfirmedSnapshot() {
        assertThatThrownBy(() -> new ReportEvidence("{}","{}",null,"b".repeat(64),"c".repeat(64),Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportEvidence("{}","{}","a".repeat(64),"b".repeat(64),"c".repeat(64),null))
                .isInstanceOf(NullPointerException.class);
    }
}
