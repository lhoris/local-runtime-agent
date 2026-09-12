package com.lra.db.repository;

import com.lra.db.entity.AgentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentStatusRepository extends JpaRepository<AgentStatus, String> {

    List<AgentStatus> findByAgentId(String agentId);

    List<AgentStatus> findByProcessId(String processId);
}
