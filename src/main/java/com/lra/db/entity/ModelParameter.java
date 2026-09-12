package com.lra.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

/**
 * A versioned key/value tuning parameter for a model process
 * (ARCHITECTURE.md §5.1 model_parameters).
 */
@Entity
@Table(name = "TB_M26_MODEL_PARAMETER")
public class ModelParameter {

    /** Application-assigned identifier (not DB-generated). */
    @Id
    @NotBlank
    @Column(name = "PARAMETER_ID", length = 64)
    private String paramId;

    @NotBlank
    @Column(name = "PROCESS_ID", length = 64, nullable = false)
    private String processId;

    @Column(name = "PARAM_KEY", length = 256)
    private String paramKey;

    @Column(name = "PARAM_VALUE", columnDefinition = "text")
    private String paramValue;

    @Column(name = "PARAM_TYPE", length = 32)
    private String paramType;

    @Column(name = "PARAM_VERSION")
    private Integer version;

    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    

    

    public String getParamId() {
        return paramId;
    }

    public void setParamId(String paramId) {
        this.paramId = paramId;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getParamKey() {
        return paramKey;
    }

    public void setParamKey(String paramKey) {
        this.paramKey = paramKey;
    }

    public String getParamValue() {
        return paramValue;
    }

    public void setParamValue(String paramValue) {
        this.paramValue = paramValue;
    }

    public String getParamType() {
        return paramType;
    }

    public void setParamType(String paramType) {
        this.paramType = paramType;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ModelParameter other)) {
            return false;
        }
        return paramId != null && paramId.equals(other.paramId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(paramId);
    }
}

