package com.lra.db.entity.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

import java.sql.Timestamp;

/** Common audit columns shared by Agent-managed entities. */
@MappedSuperclass
@EntityListeners(AuditEntityListener.class)
public abstract class BaseAudit {

    @Column(name = "CREATED_OBJECT_TYPE", length = 1)
    private String createdObjectType;
    @Column(name = "CREATED_OBJECT_ID", length = 22)
    private String createdObjectId;
    @Column(name = "CREATED_PROGRAM_ID", length = 22)
    private String createdProgramId;
    @Column(name = "CREATED_TIMESTAMP")
    private Timestamp createdTimestamp;
    @Column(name = "LAST_UPDATED_OBJECT_TYPE", length = 1)
    private String lastUpdatedObjectType;
    @Column(name = "LAST_UPDATED_OBJECT_ID", length = 22)
    private String lastUpdatedObjectId;
    @Column(name = "LAST_UPDATED_PROGRAM_ID", length = 22)
    private String lastUpdatedProgramId;
    @Column(name = "LAST_UPDATED_TIMESTAMP")
    private Timestamp lastUpdatedTimestamp;
    @Column(name = "DATA_END_STATUS", length = 1)
    private String dataEndStatus = "N";
    @Column(name = "DATA_END_OBJECT_TYPE", length = 1)
    private String dataEndObjectType;
    @Column(name = "DATA_END_OBJECT_ID", length = 22)
    private String dataEndObjectId;
    @Column(name = "DATA_END_PROGRAM_ID", length = 22)
    private String dataEndProgramId;
    @Column(name = "DATA_END_TIMESTAMP")
    private Timestamp dataEndTimestamp;

    public String getCreatedObjectType() { return createdObjectType; }
    public void setCreatedObjectType(String value) { createdObjectType = value; }
    public String getCreatedObjectId() { return createdObjectId; }
    public void setCreatedObjectId(String value) { createdObjectId = value; }
    public String getCreatedProgramId() { return createdProgramId; }
    public void setCreatedProgramId(String value) { createdProgramId = value; }
    public Timestamp getCreatedTimestamp() { return createdTimestamp; }
    public void setCreatedTimestamp(Timestamp value) { createdTimestamp = value; }
    public String getLastUpdatedObjectType() { return lastUpdatedObjectType; }
    public void setLastUpdatedObjectType(String value) { lastUpdatedObjectType = value; }
    public String getLastUpdatedObjectId() { return lastUpdatedObjectId; }
    public void setLastUpdatedObjectId(String value) { lastUpdatedObjectId = value; }
    public String getLastUpdatedProgramId() { return lastUpdatedProgramId; }
    public void setLastUpdatedProgramId(String value) { lastUpdatedProgramId = value; }
    public Timestamp getLastUpdatedTimestamp() { return lastUpdatedTimestamp; }
    public void setLastUpdatedTimestamp(Timestamp value) { lastUpdatedTimestamp = value; }
    public String getDataEndStatus() { return dataEndStatus; }
    public void setDataEndStatus(String value) { dataEndStatus = value; }
    public String getDataEndObjectType() { return dataEndObjectType; }
    public void setDataEndObjectType(String value) { dataEndObjectType = value; }
    public String getDataEndObjectId() { return dataEndObjectId; }
    public void setDataEndObjectId(String value) { dataEndObjectId = value; }
    public String getDataEndProgramId() { return dataEndProgramId; }
    public void setDataEndProgramId(String value) { dataEndProgramId = value; }
    public Timestamp getDataEndTimestamp() { return dataEndTimestamp; }
    public void setDataEndTimestamp(Timestamp value) { dataEndTimestamp = value; }
}
