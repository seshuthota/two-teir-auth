package com.company.l1api.service;

import com.company.l1api.dto.ApiErrorResponse;
import com.company.l1api.dto.TokenResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class L2ClientService {

    private final List<WebClient> l2WebClients;
    private final AtomicInteger counter = new AtomicInteger(0);

    public L2ClientService(List<WebClient> l2WebClients) {
        this.l2WebClients = l2WebClients;
    }

    private WebClient pickClient() {
        var idx = Math.abs(counter.getAndIncrement() % l2WebClients.size());
        return l2WebClients.get(idx);
    }

    public Mono<TokenResponse> requestToken(Map<String, Object> body, String correlationId) {
        return pickClient().post()
                .uri("/internal/auth/token")
                .header("X-Correlation-Id", correlationId)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(ApiErrorResponse.class)
                                .defaultIfEmpty(new ApiErrorResponse(
                                        response.statusCode().value(), "Error",
                                        "UPSTREAM_ERROR", "Upstream service error"))
                                .flatMap(err -> Mono.error(new L2ClientException(err))))
                .bodyToMono(TokenResponse.class);
    }

    public Mono<TokenResponse> refreshToken(Map<String, Object> body, String correlationId) {
        return pickClient().post()
                .uri("/internal/auth/refresh")
                .header("X-Correlation-Id", correlationId)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(ApiErrorResponse.class)
                                .defaultIfEmpty(new ApiErrorResponse(
                                        response.statusCode().value(), "Error",
                                        "UPSTREAM_ERROR", "Upstream service error"))
                                .flatMap(err -> Mono.error(new L2ClientException(err))))
                .bodyToMono(TokenResponse.class);
    }

    public Mono<Void> logout(String token, String correlationId) {
        return pickClient().post()
                .uri("/internal/auth/logout")
                .header("Authorization", "Bearer " + token)
                .header("X-Correlation-Id", correlationId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(ApiErrorResponse.class)
                                .defaultIfEmpty(new ApiErrorResponse(
                                        response.statusCode().value(), "Error",
                                        "UPSTREAM_ERROR", "Upstream service error"))
                                .flatMap(err -> Mono.error(new L2ClientException(err))))
                .bodyToMono(Void.class);
    }

    public <T> Mono<ResponseEntity<T>> forwardWithStatus(String path, String method, String token,
                                                             Object body, String correlationId,
                                                             Class<T> responseType) {
        var client = pickClient();
        var httpMethod = HttpMethod.valueOf(method);
        var uriSpec = client.method(httpMethod).uri(path);

        if (token != null) {
            uriSpec = uriSpec.header("Authorization", "Bearer " + token);
        }
        uriSpec = uriSpec.header("X-Correlation-Id", correlationId);

        if (body != null) {
            uriSpec = uriSpec.bodyValue(body);
        }

        return uriSpec.retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(ApiErrorResponse.class)
                                .defaultIfEmpty(new ApiErrorResponse(
                                        response.statusCode().value(), "Error",
                                        "UPSTREAM_ERROR", "Upstream service error"))
                                .flatMap(err -> Mono.error(new L2ClientException(err))))
                .toEntity(responseType);
    }

    public static class L2ClientException extends RuntimeException {
        private final ApiErrorResponse errorResponse;

        public L2ClientException(ApiErrorResponse errorResponse) {
            super(errorResponse.message());
            this.errorResponse = errorResponse;
        }

        public ApiErrorResponse getErrorResponse() {
            return errorResponse;
        }
    }
}
