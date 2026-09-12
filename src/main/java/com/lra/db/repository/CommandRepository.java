package com.lra.db.repository;

import com.lra.db.entity.Command;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommandRepository extends JpaRepository<Command, String> {

    List<Command> findByAgentIdAndCommandStatus(String agentId, String commandStatus);
}
