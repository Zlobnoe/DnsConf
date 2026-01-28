package com.novibe.dns.next_dns.config;

import org.jspecify.annotations.Nullable;

/**
 * Represents a single NextDNS profile configuration
 */
public record NextDnsProfile(
        String clientId,
        String authSecret,
        @Nullable String externalIp,
        @Nullable String name
) {
    public NextDnsProfile(String clientId, String authSecret) {
        this(clientId, authSecret, null, null);
    }

    public NextDnsProfile(String clientId, String authSecret, @Nullable String externalIp) {
        this(clientId, authSecret, externalIp, null);
    }

    public String getDisplayName() {
        if (name != null && !name.isBlank()) {
            return "%s (%s)".formatted(name, clientId);
        }
        return clientId;
    }
}
