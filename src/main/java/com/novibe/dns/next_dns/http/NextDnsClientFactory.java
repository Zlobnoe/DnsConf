package com.novibe.dns.next_dns.http;

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;

/**
 * Factory for creating NextDNS HTTP clients for specific profile IDs
 */
@Component
@RequiredArgsConstructor
public class NextDnsClientFactory {

    private final HttpClient httpClient;
    private final Gson gson;

    /**
     * Creates a NextDnsRewriteClient for the specified profile
     */
    public NextDnsRewriteClient createRewriteClient(String profileId, String authSecret) {
        NextDnsRewriteClient client = new NextDnsRewriteClient(profileId, authSecret);
        injectDependencies(client);
        return client;
    }

    /**
     * Creates a NextDnsDenyClient for the specified profile
     */
    public NextDnsDenyClient createDenyClient(String profileId, String authSecret) {
        NextDnsDenyClient client = new NextDnsDenyClient(profileId, authSecret);
        injectDependencies(client);
        return client;
    }

    /**
     * Injects HttpClient and Gson dependencies into the client
     */
    private void injectDependencies(AbstractNextDnsHttpClient client) {
        client.setHttpClient(httpClient);
        client.setJsonMapper(gson);
    }
}
