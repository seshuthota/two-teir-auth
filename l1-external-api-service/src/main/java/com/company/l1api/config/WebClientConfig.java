package com.company.l1api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Configuration
public class WebClientConfig {

    @Bean
    public List<WebClient> l2WebClients(
            @Value("${l2.service.base-urls}") List<String> baseUrls,
            WebClient.Builder builder) {
        return baseUrls.stream()
                .map(url -> builder.baseUrl(url).build())
                .toList();
    }
}
