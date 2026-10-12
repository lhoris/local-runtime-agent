package com.lra.agent.identity;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Finds the local IPv4 address used to identify this agent in TB_M26_AGENT.
 */
@Component
public class LocalIpResolver {

    private static final Logger log = LoggerFactory.getLogger(LocalIpResolver.class);

    @Value("${agent.ip-address:}")
    private String configuredIpAddress;

    public Optional<String> resolve() {
        return resolveAll().stream().findFirst();
    }

    public List<String> resolveAll() {
        List<String> addresses = new ArrayList<>();
        addConfiguredAddress(addresses);
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> networkAddresses = networkInterface.getInetAddresses();
                while (networkAddresses.hasMoreElements()) {
                    InetAddress address = networkAddresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        addresses.add(address.getHostAddress());
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to inspect network interfaces for local IP", ex);
        }

        if (addresses.isEmpty()) {
            try {
                InetAddress fallback = InetAddress.getLocalHost();
                if (fallback instanceof Inet4Address && !fallback.isLoopbackAddress()) {
                    addresses.add(fallback.getHostAddress());
                }
            } catch (Exception ex) {
                log.warn("Failed to resolve fallback local IP", ex);
            }
        }
        return addresses.stream().distinct().toList();
    }

    private void addConfiguredAddress(List<String> addresses) {
        if (configuredIpAddress == null || configuredIpAddress.isBlank()) {
            return;
        }
        String value = configuredIpAddress.trim();
        try {
            InetAddress address = InetAddress.getByName(value);
            if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                addresses.add(address.getHostAddress());
            } else {
                log.warn("Configured Agent IP is not a usable IPv4 address: {}", value);
            }
        } catch (Exception ex) {
            log.warn("Configured Agent IP could not be resolved: {}", value, ex);
        }
    }

    public Optional<String> resolveHostname() {
        try {
            return Optional.ofNullable(InetAddress.getLocalHost().getHostName())
                .filter(hostname -> !hostname.isBlank());
        } catch (Exception ex) {
            log.warn("Failed to resolve local hostname", ex);
            return Optional.empty();
        }
    }
}
