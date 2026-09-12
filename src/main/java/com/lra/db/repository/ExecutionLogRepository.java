package com.lra.db.repository;

import com.lra.db.entity.ExecutionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, String> {

    List<ExecutionLog> findByAgentId(String agentId);

    List<ExecutionLog> findByProcessId(String processId);

    Page<ExecutionLog> findByProcessId(String processId, Pageable pageable);
}
