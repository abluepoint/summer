package com.abluepoint.summer.sample.realtime;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface RealtimeMessageRepository extends JpaRepository<RealtimeMessageEntity, String> {
    Optional<RealtimeMessageEntity> findByEnterpriseIdAndSenderAndClientMessageId(String enterpriseId, String sender, String clientMessageId);
    Optional<RealtimeMessageEntity> findFirstByEnterpriseIdOrderBySequenceDesc(String enterpriseId);
    List<RealtimeMessageEntity> findTop500ByEnterpriseIdAndSequenceGreaterThanOrderBySequenceAsc(String enterpriseId, long sequence);
    List<RealtimeMessageEntity> findTop500ByEnterpriseIdOrderBySequenceAsc(String enterpriseId);
}
