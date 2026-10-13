package com.lra.db.entity.audit;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.sql.Timestamp;

/** Populates audit columns during Agent JPA persistence lifecycle events. */
public class AuditEntityListener {

    @PrePersist
    public void beforePersist(BaseAudit entity) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        entity.setCreatedObjectType(defaultValue(entity.getCreatedObjectType(), "S"));
        entity.setCreatedObjectId(defaultValue(entity.getCreatedObjectId(), "AGENT"));
        entity.setCreatedProgramId(defaultValue(entity.getCreatedProgramId(), programId(entity)));
        if (entity.getCreatedTimestamp() == null) entity.setCreatedTimestamp(now);
        entity.setLastUpdatedObjectType("S");
        entity.setLastUpdatedObjectId("AGENT");
        entity.setLastUpdatedProgramId(programId(entity));
        entity.setLastUpdatedTimestamp(now);
        entity.setDataEndStatus(defaultValue(entity.getDataEndStatus(), "N"));
    }

    @PreUpdate
    public void beforeUpdate(BaseAudit entity) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        entity.setLastUpdatedObjectType("S");
        entity.setLastUpdatedObjectId("AGENT");
        entity.setLastUpdatedProgramId(programId(entity));
        entity.setLastUpdatedTimestamp(now);
        if ("Y".equalsIgnoreCase(entity.getDataEndStatus()) && entity.getDataEndTimestamp() == null) {
            entity.setDataEndObjectType("S");
            entity.setDataEndObjectId("AGENT");
            entity.setDataEndProgramId(programId(entity));
            entity.setDataEndTimestamp(now);
        }
    }

    private String programId(BaseAudit entity) {
        return entity.getClass().getSimpleName().toUpperCase();
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
