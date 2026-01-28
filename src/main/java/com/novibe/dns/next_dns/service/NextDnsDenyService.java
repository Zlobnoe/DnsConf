package com.novibe.dns.next_dns.service;

import com.novibe.common.config.EnvironmentVariables;
import com.novibe.common.util.Log;
import com.novibe.dns.next_dns.http.NextDnsDenyClient;
import com.novibe.dns.next_dns.http.NextDnsRateLimitedApiProcessor;
import com.novibe.dns.next_dns.http.dto.request.CreateDenyDto;
import com.novibe.dns.next_dns.http.dto.response.deny.DenyDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NextDnsDenyService {

    public List<String> dropExistingDenys(NextDnsDenyClient client, List<String> newDenyList) {
        Log.io("Fetching existing denylist from NextDNS");
        List<DenyDto> existingDenyList = client.fetchDenylist();
        
        // Check if FORCE_REWRITE is enabled
        boolean forceRewrite = "true".equalsIgnoreCase(EnvironmentVariables.FORCE_REWRITE);
        
        if (forceRewrite) {
            // Remove all existing denys
            List<String> allIds = existingDenyList.stream().map(DenyDto::getId).toList();
            if (!allIds.isEmpty()) {
                Log.io("FORCE_REWRITE enabled: Removing ALL %s existing denys from NextDNS".formatted(allIds.size()));
                NextDnsRateLimitedApiProcessor.callApi(allIds, client::deleteDenyById);
            }
            return newDenyList;
        }
        
        // Normal mode: only add new domains
        Set<String> existingDomainsSet = existingDenyList.stream()
                .filter(DenyDto::isActive)
                .map(DenyDto::getId)
                .collect(Collectors.toSet());
        newDenyList.removeIf(existingDomainsSet::contains);
        return newDenyList;
    }

    public void saveDenyList(NextDnsDenyClient client, List<String> newDenylist) {
        List<CreateDenyDto> createRequests = newDenylist.stream().map(CreateDenyDto::new).toList();
        Log.io("Saving new denylist to NextDNS...");
        NextDnsRateLimitedApiProcessor.callApi(createRequests, client::saveDeny);
    }

    public void removeAll(NextDnsDenyClient client) {
        Log.io("Fetching existing denylist from NextDNS");
        List<DenyDto> existing = client.fetchDenylist();
        List<String> ids = existing.stream().map(DenyDto::getId).toList();
        Log.io("Removing denylist from NextDNS");
        NextDnsRateLimitedApiProcessor.callApi(ids, client::deleteDenyById);
    }

}
