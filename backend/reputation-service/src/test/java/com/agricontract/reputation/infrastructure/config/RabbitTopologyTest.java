package com.agricontract.reputation.infrastructure.config;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import static org.assertj.core.api.Assertions.*;
class RabbitTopologyTest {
    @Test void ownsDlxAndPreservesCanonicalDeadLetterKeys() {
        var topology = new RabbitConfiguration().reputationBindings();
        assertThat(topology.getDeclarablesByType(DirectExchange.class)).singleElement()
                .satisfies(exchange -> {
                    assertThat(exchange.getName()).isEqualTo("agricontract.reputation.dlx");
                    assertThat(exchange.isDurable()).isTrue();
                });
        for (String key : RabbitConfiguration.EVENT_KEYS) {
            String queueName = "reputation-svc." + key;
            assertThat(topology.getDeclarablesByType(Queue.class)).filteredOn(q -> q.getName().equals(queueName))
                    .singleElement().satisfies(queue -> {
                        assertThat(queue.getArguments()).containsEntry("x-dead-letter-exchange", "agricontract.reputation.dlx")
                                .doesNotContainKey("x-dead-letter-routing-key");
                    });
            assertThat(topology.getDeclarablesByType(Binding.class))
                    .filteredOn(binding -> binding.getDestination().equals(queueName + ".dlq")).singleElement()
                    .satisfies(binding -> {
                        assertThat(binding.getExchange()).isEqualTo("agricontract.reputation.dlx");
                        assertThat(binding.getRoutingKey()).isEqualTo(key);
                    });
        }
    }
}
