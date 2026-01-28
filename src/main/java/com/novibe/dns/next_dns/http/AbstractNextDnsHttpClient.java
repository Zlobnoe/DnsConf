package com.novibe.dns.next_dns.http;

import com.google.gson.Gson;
import com.novibe.common.HttpRequestSender;
import com.novibe.common.util.Log;

import java.net.http.HttpClient;

public abstract class AbstractNextDnsHttpClient extends HttpRequestSender {

    private final String profileId;
    private final String authSecret;

    protected AbstractNextDnsHttpClient(String profileId, String authSecret) {
        this.profileId = profileId;
        this.authSecret = authSecret;
    }

    protected abstract String path();

    @Override
    protected String apiUrl() {
        return "https://api.nextdns.io/profiles/%s".formatted(profileId);
    }

    @Override
    protected String authHeaderName() {
        return "X-Api-Key";
    }

    @Override
    protected String authHeaderValue() {
        return authSecret;
    }

    @Override
    protected final void react401() {
        Log.fail("Invalid api key!");
    }

    @Override
    protected void react403() {
        Log.fail("Invalid api key!");
    }

    /**
     * Public method to inject HttpClient dependency
     */
    public void injectHttpClient(HttpClient httpClient) {
        setHttpClient(httpClient);
    }

    /**
     * Public method to inject Gson dependency
     */
    public void injectGson(Gson gson) {
        setJsonMapper(gson);
    }
}
