package com.lra.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Command queue shared with vision-monitor. */
@Entity
@Table(name = "TB_M26_MODEL_COMMAND")
public class ModelCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MODEL_COMMAND_ID")
    private Long modelCommandId;

    @Column(name = "MODEL_PROCESS_ID", nullable = false)
    private Long modelProcessId;

    @Column(name = "TARGET_SERVER_IP", length = 45, nullable = false)
    private String targetServerIp;

    @Column(name = "COMMAND_TYPE", length = 20, nullable = false)
    private String commandType;

    @Column(name = "COMMAND_STATUS", length = 20, nullable = false)
    private String commandStatus;

    @Column(name = "REQUESTED_BY", length = 100, nullable = false)
    private String requestedBy;

    @Column(name = "REQUESTED_TIMESTAMP")
    private Instant requestedTimestamp;

    @Column(name = "CLAIMED_TIMESTAMP")
    private Instant claimedTimestamp;

    @Column(name = "PROCESSED_TIMESTAMP")
    private Instant processedTimestamp;

    @Column(name = "AGENT_ID", length = 100)
    private String agentId;

    @Column(name = "RESULT_CODE", length = 50)
    private String resultCode;

    @Column(name = "RESULT_MESSAGE", length = 2000)
    private String resultMessage;

    @Column(name = "RETRY_COUNT", nullable = false)
    private Integer retryCount;

    public Long getModelCommandId() { return modelCommandId; }
    public String getCommandId() { return modelCommandId == null ? null : String.valueOf(modelCommandId); }
    public Long getModelProcessId() { return modelProcessId; }
    public String getProcessId() { return modelProcessId == null ? null : String.valueOf(modelProcessId); }
    public void setProcessId(String processId) { this.modelProcessId = Long.valueOf(processId); }
    public String getTargetServerIp() { return targetServerIp; }
    public void setTargetServerIp(String targetServerIp) { this.targetServerIp = targetServerIp; }
    public String getCommandType() { return commandType; }
    public void setCommandType(String commandType) { this.commandType = commandType; }
    public String getCommandStatus() { return commandStatus; }
    public void setCommandStatus(String commandStatus) { this.commandStatus = commandStatus; }
    public void setClaimedTimestamp(Instant claimedTimestamp) { this.claimedTimestamp = claimedTimestamp; }
    public void setProcessedTimestamp(Instant processedTimestamp) { this.processedTimestamp = processedTimestamp; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public void setResultMessage(String resultMessage) { this.resultMessage = resultMessage; }
}
