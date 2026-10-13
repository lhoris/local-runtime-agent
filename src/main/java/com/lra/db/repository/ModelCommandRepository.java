package com.lra.db.repository;

import com.lra.db.entity.ModelCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelCommandRepository extends JpaRepository<ModelCommand, String> {

    List<ModelCommand> findByAgentIdAndCommandStatus(String agentId, String commandStatus);
}
