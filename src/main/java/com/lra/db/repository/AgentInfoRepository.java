package com.lra.db.repository;

import com.lra.db.entity.AgentInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentInfoRepository extends JpaRepository<AgentInfo, String> {

    List<AgentInfo> findByHostname(String hostname);
}
