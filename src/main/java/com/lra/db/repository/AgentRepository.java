package com.lra.db.repository;

import com.lra.db.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentRepository extends JpaRepository<Agent, String> {

    List<Agent> findByHostname(String hostname);

    List<Agent> findByIpAddress(String ipAddress);
}
