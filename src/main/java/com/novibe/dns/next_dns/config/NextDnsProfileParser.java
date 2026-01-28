package com.novibe.dns.next_dns.config;

import com.novibe.common.util.Log;

import java.util.ArrayList;
import java.util.List;

import static com.novibe.common.config.EnvironmentVariables.*;

/**
 * Parser for NextDNS profile configurations from environment variables
 */
public class NextDnsProfileParser {

    /**
     * Parses CLIENT_ID, AUTH_SECRET and EXTERNAL_IP environment variables into a list of NextDnsProfile objects
     * 
     * Format examples:
     * - Single profile: CLIENT_ID=abc123 AUTH_SECRET=key1
     * - Multiple profiles: CLIENT_ID=abc123,def456,ghi789 AUTH_SECRET=key1,key2,key3
     * - Same API key for all: CLIENT_ID=abc123,def456 AUTH_SECRET=key1
     * - With EXTERNAL_IP for all: CLIENT_ID=abc123,def456 AUTH_SECRET=key1,key2 EXTERNAL_IP=10.20.30.40
     * - With different EXTERNAL_IP: CLIENT_ID=abc123,def456 AUTH_SECRET=key1,key2 EXTERNAL_IP=10.20.30.40,10.20.30.41
     * - Mixed EXTERNAL_IP: CLIENT_ID=abc123,def456,ghi789 AUTH_SECRET=key1,key2,key3 EXTERNAL_IP=10.20.30.40,,10.20.30.42
     * 
     * @return List of NextDnsProfile configurations
     * @throws IllegalArgumentException if configuration is invalid
     */
    public static List<NextDnsProfile> parse() {
        String clientIdsRaw = CLIENT_ID;
        String authSecretsRaw = AUTH_SECRET;
        String externalIpsRaw = EXTERNAL_IP;

        if (clientIdsRaw == null || clientIdsRaw.isBlank()) {
            throw new IllegalArgumentException("CLIENT_ID environment variable is required for NextDNS");
        }

        if (authSecretsRaw == null || authSecretsRaw.isBlank()) {
            throw new IllegalArgumentException("AUTH_SECRET environment variable is required for NextDNS");
        }

        // Split CLIENT_IDs by comma or semicolon
        String[] clientIds = clientIdsRaw.split("[,;]");
        List<String> trimmedClientIds = new ArrayList<>();
        
        for (String clientId : clientIds) {
            String trimmed = clientId.strip();
            if (!trimmed.isEmpty()) {
                trimmedClientIds.add(trimmed);
            }
        }

        if (trimmedClientIds.isEmpty()) {
            throw new IllegalArgumentException("At least one CLIENT_ID must be provided");
        }

        // Parse AUTH_SECRETs
        List<String> authSecrets = parseAuthSecrets(authSecretsRaw, trimmedClientIds.size());

        // Parse EXTERNAL_IPs if provided
        List<String> externalIps = parseExternalIps(externalIpsRaw, trimmedClientIds.size());

        // Build profile list
        List<NextDnsProfile> profiles = new ArrayList<>();
        for (int i = 0; i < trimmedClientIds.size(); i++) {
            String clientId = trimmedClientIds.get(i);
            String authSecret = authSecrets.get(i);
            String externalIp = externalIps.get(i);
            profiles.add(new NextDnsProfile(clientId, authSecret, externalIp));
        }

        return profiles;
    }

    /**
     * Parses AUTH_SECRET string into a list matching the number of profiles
     * 
     * @param authSecretsRaw Raw AUTH_SECRET string from environment
     * @param profileCount Number of profiles (CLIENT_IDs)
     * @return List of auth secrets
     * @throws IllegalArgumentException if auth secrets don't match profile count
     */
    private static List<String> parseAuthSecrets(String authSecretsRaw, int profileCount) {
        List<String> result = new ArrayList<>();

        // Check if it's a single key (no commas/semicolons) - apply to all profiles
        if (!authSecretsRaw.contains(",") && !authSecretsRaw.contains(";")) {
            String singleKey = authSecretsRaw.strip();
            Log.io("Single AUTH_SECRET will be applied to all %d profiles".formatted(profileCount));
            for (int i = 0; i < profileCount; i++) {
                result.add(singleKey);
            }
            return result;
        }

        // Multiple keys specified
        String[] keys = authSecretsRaw.split("[,;]");
        
        if (keys.length != profileCount) {
            throw new IllegalArgumentException(
                "AUTH_SECRET count (%d) must match CLIENT_ID count (%d) or be a single value for all profiles"
                    .formatted(keys.length, profileCount)
            );
        }

        for (String key : keys) {
            String trimmed = key.strip();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("AUTH_SECRET cannot be empty for any profile");
            }
            result.add(trimmed);
        }

        return result;
    }

    /**
     * Parses EXTERNAL_IP string into a list matching the number of profiles
     * 
     * @param externalIpsRaw Raw EXTERNAL_IP string from environment
     * @param profileCount Number of profiles (CLIENT_IDs)
     * @return List of external IPs (null for profiles without specific IP)
     */
    private static List<String> parseExternalIps(String externalIpsRaw, int profileCount) {
        List<String> result = new ArrayList<>();

        if (externalIpsRaw == null || externalIpsRaw.isBlank()) {
            // No EXTERNAL_IP specified - return list of nulls
            for (int i = 0; i < profileCount; i++) {
                result.add(null);
            }
            return result;
        }

        // Check if it's a single IP (no commas/semicolons) - apply to all profiles
        if (!externalIpsRaw.contains(",") && !externalIpsRaw.contains(";")) {
            String singleIp = externalIpsRaw.strip();
            Log.io("Single EXTERNAL_IP '%s' will be applied to all %d profiles".formatted(singleIp, profileCount));
            for (int i = 0; i < profileCount; i++) {
                result.add(singleIp);
            }
            return result;
        }

        // Multiple IPs specified
        String[] ips = externalIpsRaw.split("[,;]");
        
        for (int i = 0; i < profileCount; i++) {
            if (i < ips.length) {
                String ip = ips[i].strip();
                result.add(ip.isEmpty() ? null : ip);
            } else {
                // More profiles than IPs - use null for remaining
                result.add(null);
            }
        }

        // Validate: warn if more IPs than profiles
        if (ips.length > profileCount) {
            Log.fail("Warning: %d EXTERNAL_IPs provided but only %d profiles. Extra IPs will be ignored."
                    .formatted(ips.length, profileCount));
        }

        return result;
    }

    /**
     * Logs the parsed profile configuration
     */
    public static void logProfiles(List<NextDnsProfile> profiles) {
        Log.step("NextDNS Profile Configuration");
        Log.common("Total profiles: %d".formatted(profiles.size()));
        
        for (int i = 0; i < profiles.size(); i++) {
            NextDnsProfile profile = profiles.get(i);
            String ipInfo = profile.externalIp() != null 
                ? "EXTERNAL_IP: " + profile.externalIp() 
                : "Using IPs from hosts files";
            Log.common("  Profile %d/%d: %s (%s)".formatted(
                i + 1, 
                profiles.size(), 
                profile.clientId(), 
                ipInfo
            ));
        }
    }
}
