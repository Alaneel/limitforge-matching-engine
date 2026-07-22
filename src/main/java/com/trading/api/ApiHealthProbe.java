package com.trading.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Lightweight, dependency-free health probe used by the container image. */
public final class ApiHealthProbe {
    private ApiHealthProbe() {
    }

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/health"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Health endpoint returned HTTP " + response.statusCode());
        }
    }
}
