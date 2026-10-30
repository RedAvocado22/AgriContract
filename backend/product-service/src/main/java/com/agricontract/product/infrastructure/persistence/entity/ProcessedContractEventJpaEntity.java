package com.agricontract.product.infrastructure.persistence.entity;

import com.agricontract.product.domain.model.vo.ContractLifecycleEventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_contract_events")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedContractEventJpaEntity {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private ContractLifecycleEventType eventType;

    @Column(name = "contract_id", nullable = false, length = 255)
    private String contractId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
