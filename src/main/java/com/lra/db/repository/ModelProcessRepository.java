package com.lra.db.repository;

import com.lra.db.entity.ModelProcess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelProcessRepository extends JpaRepository<ModelProcess, String> {

    List<ModelProcess> findByAgentId(String agentId);

    List<ModelProcess> findByProcessId(String processId);
}
