package com.novibe.common;

import com.google.gson.Gson;
import com.novibe.common.exception.NextDnsHttpError;
import com.novibe.common.util.Log;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Semaphore;

import static java.util.Objects.isNull;

public abstract class HttpRequestSender {

    private final Semaphore semaphore = new Semaphore(100);

    protected static final String GET = "GET";
    protected static final String POST = "POST";
    protected static final String DELETE = "DELETE";

    protected abstract String apiUrl();

    protected abstract String authHeaderName();

    protected abstract String authHeaderValue();

    protected abstract void react401();

    protected abstract void react403();

    @Setter(onMethod_ = @Autowired, value = AccessLevel.PACKAGE)
    private HttpClient httpClient;

    @Setter(onMethod_ = @Autowired, value = AccessLevel.PACKAGE)
    private Gson jsonMapper;

    /**
     * Protected method to allow subclasses to set HttpClient
     */
    protected void setHttpClientInternal(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Protected method to allow subclasses to set Gson
     */
    protected void setJsonMapperInternal(Gson jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public <T> T get(String path, Class<T> responseType) {
        return sendRequest(GET, path, null, responseType);
    }

    public <T, R> T post(String path, R requestBody, Class<T> responseType) {
        return sendRequest(POST, path, requestBody, responseType);
    }

    public <T> T delete(String path, Class<T> responseType) {
        return sendRequest(DELETE, path, null, responseType);

    }

    @SneakyThrows
    protected <T, R> T sendRequest(String method, String path, R body, Class<T> responseBody) {
        URI uri = URI.create(apiUrl() + (isNull(path) ? "" : path));
        HttpRequest.BodyPublisher requestBody;
        if (isNull(body)) {
            requestBody = HttpRequest.BodyPublishers.noBody();
        } else {
            requestBody = HttpRequest.BodyPublishers.ofString(jsonMapper.toJson(body));
        }
        semaphore.acquire();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header(authHeaderName(), authHeaderValue())
                .header("Content-Type", "application/json")
                .method(method, requestBody)
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        semaphore.release();
        if (response.statusCode() > 299) {
            Log.fail("Request failed! Status: %s for %s %s\nReason: %s ".formatted(
                    response.statusCode(),
                    response.request().method(),
                    response.request().uri(),
                    response.body()));
            if (response.statusCode() == 401) {
                react401();
                System.exit(1);
            }
            if (response.statusCode() == 403) {
                react403();
                System.exit(1);
            } else {
                throw new NextDnsHttpError(response.statusCode(), response.body());
            }
        }
        if (response.body().isEmpty()) {
            return null;
        }
        return jsonMapper.fromJson(response.body(), responseBody);
    }
}
