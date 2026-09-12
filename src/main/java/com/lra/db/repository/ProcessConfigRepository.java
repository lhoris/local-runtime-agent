package com.lra.db.repository;

import com.lra.db.entity.ProcessConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessConfigRepository extends JpaRepository<ProcessConfig, String> {

    List<ProcessConfig> findByAgentId(String agentId);
}
