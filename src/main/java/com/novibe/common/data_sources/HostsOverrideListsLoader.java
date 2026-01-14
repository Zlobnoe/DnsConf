package com.novibe.common.data_sources;

import com.novibe.common.config.EnvironmentVariables;
import com.novibe.common.util.Log;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class HostsOverrideListsLoader extends ListLoader<HostsOverrideListsLoader.BypassRoute> {

    public record BypassRoute(String ip, String website) {
    }

    @Override
    protected Stream<BypassRoute> lineParser(String urlList) {
        return Pattern.compile("\\r?\\n").splitAsStream(urlList)
                .parallel()
                .map(String::strip)
                .filter(str -> !str.isBlank())
                .filter(line -> !line.startsWith("#"))
                .filter(line -> !HostsBlockListsLoader.isBlock(line))
                .map(this::mapLine);
    }

    @Override
    protected String listType() {
        return "Override";
    }

    private BypassRoute mapLine(String line) {
        int delimiter = line.indexOf(" ");
        String originalIp = line.substring(0, delimiter++);
        String website = line.substring(delimiter);
        String ip = originalIp;
        
        // Use EXTERNAL_IP if environment variable is set, otherwise use IP from file
        if (EnvironmentVariables.EXTERNAL_IP != null && !EnvironmentVariables.EXTERNAL_IP.isBlank()) {
            ip = EnvironmentVariables.EXTERNAL_IP;
            Log.io("Processing: %s -> %s (IP changed from %s to %s)".formatted(website, ip, originalIp, ip));
        } else {
            Log.io("Processing: %s -> %s".formatted(website, ip));
        }
        
        return new BypassRoute(ip, website);
    }

}
