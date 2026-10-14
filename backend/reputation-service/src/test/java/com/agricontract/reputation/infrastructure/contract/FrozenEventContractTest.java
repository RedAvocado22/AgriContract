package com.agricontract.reputation.infrastructure.contract;
import com.agricontract.reputation.infrastructure.messaging.ReputationFactConsumerTest;
import org.junit.jupiter.api.Test;
class FrozenEventContractTest {
    @Test void completionFixtureMatchesFrozenSchema() throws Exception {
        FrozenSchemas.assertEvent("contractSettled", ReputationFactConsumerTest.event("contract.settled"));
    }
    @Test void disputeFixtureMatchesFrozenSchema() throws Exception {
        FrozenSchemas.assertEvent("milestoneDisputeResolved", ReputationFactConsumerTest.event("milestone.dispute_resolved"));
    }
}
