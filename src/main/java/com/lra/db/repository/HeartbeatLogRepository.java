package com.lra.db.repository;

import com.lra.db.entity.HeartbeatLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HeartbeatLogRepository extends JpaRepository<HeartbeatLog, String> {

    List<HeartbeatLog> findByAgentId(String agentId);

    Optional<HeartbeatLog> findFirstByAgentIdOrderByHeartbeatTimeDesc(String agentId);
}
