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
 * A PC-unit agent registered with the central server (ARCHITECTURE.md §5.1 agent_info).
 */
@Entity
@Table(name = "TB_M26_AGENT_INFO")
public class AgentInfo {

    @Id
    @NotBlank
    @Column(name = "AGENT_ID", length = 22)
    private String agentId;

    @Column(name = "HOSTNAME", length = 256)
    private String hostname;

    @Column(name = "OS_TYPE", length = 32)
    private String osType;

    @Column(name = "IP_ADDRESS", length = 45)
    private String ipAddress;

    @Column(name = "SPRING_BOOT_VERSION", length = 32)
    private String springBootVersion;

    @Column(name = "INSTALLED_AT")
    private Instant installedAt;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getOsType() {
        return osType;
    }

    public void setOsType(String osType) {
        this.osType = osType;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getSpringBootVersion() {
        return springBootVersion;
    }

    public void setSpringBootVersion(String springBootVersion) {
        this.springBootVersion = springBootVersion;
    }

    public Instant getInstalledAt() {
        return installedAt;
    }

    public void setInstalledAt(Instant installedAt) {
        this.installedAt = installedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AgentInfo other)) {
            return false;
        }
        return agentId != null && agentId.equals(other.agentId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(agentId);
    }
}
