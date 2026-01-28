package com.novibe.dns.next_dns.service;

import com.novibe.common.config.EnvironmentVariables;
import com.novibe.common.data_sources.HostsOverrideListsLoader;
import com.novibe.common.util.Log;
import com.novibe.dns.next_dns.http.NextDnsRateLimitedApiProcessor;
import com.novibe.dns.next_dns.http.NextDnsRewriteClient;
import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import com.novibe.dns.next_dns.http.dto.response.rewrite.RewriteDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.nonNull;

@Service
public class NextDnsRewriteService {

    public Map<String, CreateRewriteDto> buildNewRewrites(List<HostsOverrideListsLoader.BypassRoute> overrides) {
        Map<String, CreateRewriteDto> rewriteDtos = new HashMap<>();
        overrides.forEach(route -> rewriteDtos.putIfAbsent(route.website(), new CreateRewriteDto(route.website(), route.ip())));
        return rewriteDtos;
    }

    public List<CreateRewriteDto> cleanupOutdated(NextDnsRewriteClient client, Map<String, CreateRewriteDto> newRewriteRequests) {
        List<RewriteDto> existingRewrites = getExistingRewrites(client);
        
        // Check if FORCE_REWRITE is enabled
        boolean forceRewrite = "true".equalsIgnoreCase(EnvironmentVariables.FORCE_REWRITE);
        
        if (forceRewrite) {
            // Remove all existing rewrites
            List<String> allIds = existingRewrites.stream().map(RewriteDto::id).toList();
            if (!allIds.isEmpty()) {
                Log.io("FORCE_REWRITE enabled: Removing ALL %s existing rewrites from NextDNS".formatted(allIds.size()));
                NextDnsRateLimitedApiProcessor.callApi(allIds, client::deleteRewriteById);
            }
            return List.copyOf(newRewriteRequests.values());
        }

        // Normal mode: only remove outdated rewrites
        List<String> outdatedIds = new ArrayList<>();

        for (RewriteDto existingRewrite : existingRewrites) {
            String domain = existingRewrite.name();
            String oldIp = existingRewrite.content();
            CreateRewriteDto request = newRewriteRequests.get(domain);
            if (nonNull(request) && !request.getContent().equals(oldIp)) {
                outdatedIds.add(existingRewrite.id());
            } else {
                newRewriteRequests.remove(domain);
            }
        }
        if (!outdatedIds.isEmpty()) {
            Log.io("Removing %s outdated rewrites from NextDNS".formatted(outdatedIds.size()));
            NextDnsRateLimitedApiProcessor.callApi(outdatedIds, client::deleteRewriteById);
        }
        return List.copyOf(newRewriteRequests.values());
    }

    public List<RewriteDto> getExistingRewrites(NextDnsRewriteClient client) {
        Log.io("Fetching existing rewrites from NextDNS");
        return client.fetchRewrites();
    }

    public void saveRewrites(NextDnsRewriteClient client, List<CreateRewriteDto> createRewriteDtos) {
        Log.io("Saving %s new rewrites to NextDNS...".formatted(createRewriteDtos.size()));
        NextDnsRateLimitedApiProcessor.callApi(createRewriteDtos, client::saveRewrite);
    }

    public void removeAll(NextDnsRewriteClient client) {
        Log.io("Fetching existing rewrites from NextDNS");
        List<RewriteDto> list = client.fetchRewrites();
        List<String> ids = list.stream().map(RewriteDto::id).toList();
        Log.io("Removing rewrites from NextDNS");
        NextDnsRateLimitedApiProcessor.callApi(ids, client::deleteRewriteById);
    }

}
