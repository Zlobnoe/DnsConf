package com.novibe.dns.next_dns;

import com.novibe.common.DnsTaskRunner;
import com.novibe.common.data_sources.HostsBlockListsLoader;
import com.novibe.common.data_sources.HostsOverrideListsLoader;
import com.novibe.common.util.EnvParser;
import com.novibe.common.util.Log;
import com.novibe.dns.next_dns.config.NextDnsProfile;
import com.novibe.dns.next_dns.config.NextDnsProfileParser;
import com.novibe.dns.next_dns.http.NextDnsClientFactory;
import com.novibe.dns.next_dns.http.NextDnsDenyClient;
import com.novibe.dns.next_dns.http.NextDnsRewriteClient;
import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import com.novibe.dns.next_dns.service.NextDnsDenyService;
import com.novibe.dns.next_dns.service.NextDnsRewriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.novibe.common.config.EnvironmentVariables.BLOCK;
import static com.novibe.common.config.EnvironmentVariables.REDIRECT;

@Service
@RequiredArgsConstructor
public class NextDnsTaskRunner implements DnsTaskRunner {

    private final HostsBlockListsLoader blockListsLoader;
    private final HostsOverrideListsLoader overrideListsLoader;
    private final NextDnsRewriteService nextDnsRewriteService;
    private final NextDnsDenyService nextDnsDenyService;
    private final NextDnsClientFactory clientFactory;

    @Override
    public void run() {

        Log.global("NextDNS - Multiple Profiles Support");
        Log.common("""
        Script behaviour: old block/redirect settings are about to be updated via provided block/redirect sources.
        If no sources provided, then all NextDNS settings will be removed.
        If provided only one type of sources, related settings will be updated; another type remain untouched.
        NextDNS API rate limiter reset config: 60 seconds after the last request""");

        // Parse profiles configuration
        List<NextDnsProfile> profiles;
        try {
            profiles = NextDnsProfileParser.parse();
            NextDnsProfileParser.logProfiles(profiles);
        } catch (IllegalArgumentException e) {
            Log.fail("Failed to parse NextDNS profiles: " + e.getMessage());
            System.exit(1);
            return;
        }

        // Parse sources
        List<String> blockSources = EnvParser.parse(BLOCK);
        List<String> rewriteSources = EnvParser.parse(REDIRECT);

        // Load data from sources once (not per profile!)
        List<String> blocks = new ArrayList<>();
        List<HostsOverrideListsLoader.BypassRoute> overridesBase = new ArrayList<>();

        if (!blockSources.isEmpty()) {
            Log.step("Obtain block lists from %s sources".formatted(blockSources.size()));
            blocks = blockListsLoader.fetchWebsites(blockSources);
            Log.common("Loaded %s domains to block".formatted(blocks.size()));
        } else {
            Log.fail("No block sources provided");
        }

        if (!rewriteSources.isEmpty()) {
            Log.step("Obtain rewrite lists from %s sources".formatted(rewriteSources.size()));
            overridesBase = overrideListsLoader.fetchWebsites(rewriteSources);
            Log.common("Loaded %s domains to redirect".formatted(overridesBase.size()));
        } else {
            Log.fail("No rewrite sources provided");
        }

        // Process each profile
        int successCount = 0;
        int errorCount = 0;
        
        for (int i = 0; i < profiles.size(); i++) {
            NextDnsProfile profile = profiles.get(i);
            
            try {
                Log.global("Processing Profile %d/%d: %s".formatted(i + 1, profiles.size(), profile.getDisplayName()));
                
                // Create HTTP clients for this profile
                NextDnsDenyClient denyClient = clientFactory.createDenyClient(profile.clientId(), profile.authSecret());
                NextDnsRewriteClient rewriteClient = clientFactory.createRewriteClient(profile.clientId(), profile.authSecret());

                // Process blocks
                if (!blockSources.isEmpty()) {
                    Log.step("Processing denylist for profile %s".formatted(profile.clientId()));
                    List<String> filteredBlocklist = nextDnsDenyService.dropExistingDenys(denyClient, new ArrayList<>(blocks));
                    Log.common("Prepared %s domains to block".formatted(filteredBlocklist.size()));
                    nextDnsDenyService.saveDenyList(denyClient, filteredBlocklist);
                }

                // Process rewrites with profile-specific EXTERNAL_IP
                if (!rewriteSources.isEmpty()) {
                    Log.step("Processing rewrites for profile %s".formatted(profile.clientId()));
                    
                    // Apply profile-specific EXTERNAL_IP if set
                    List<HostsOverrideListsLoader.BypassRoute> overrides = 
                            overrideListsLoader.applyExternalIp(new ArrayList<>(overridesBase), profile.externalIp());
                    
                    Map<String, CreateRewriteDto> requests = nextDnsRewriteService.buildNewRewrites(overrides);
                    List<CreateRewriteDto> createRewriteDtos = nextDnsRewriteService.cleanupOutdated(rewriteClient, requests);
                    Log.common("Prepared %s domains to rewrite".formatted(createRewriteDtos.size()));
                    
                    nextDnsRewriteService.saveRewrites(rewriteClient, createRewriteDtos);
                }

                // Remove all settings if no sources provided
                if (blockSources.isEmpty() && rewriteSources.isEmpty()) {
                    Log.step("Remove settings for profile %s".formatted(profile.clientId()));
                    nextDnsDenyService.removeAll(denyClient);
                    nextDnsRewriteService.removeAll(rewriteClient);
                }

                Log.common("✓ Profile %s processed successfully".formatted(profile.clientId()));
                successCount++;
                
            } catch (Exception e) {
                Log.fail("✗ Error processing profile %s: %s".formatted(profile.clientId(), e.getMessage()));
                e.printStackTrace();
                errorCount++;
                // Continue with next profile
            }
        }

        // Summary
        Log.global("FINISHED");
        Log.common("Summary: %d profiles processed successfully, %d with errors".formatted(successCount, errorCount));
        
        if (errorCount > 0) {
            System.exit(1);
        }
    }

}
