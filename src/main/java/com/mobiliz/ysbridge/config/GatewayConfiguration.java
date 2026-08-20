package com.mobiliz.ysbridge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobiliz.ysbridge.error.ErrorCatalog;
import com.mobiliz.ysbridge.error.ErrorCatalogLoader;
import com.mobiliz.ysbridge.gateway.CollectionPaginator;
import com.mobiliz.ysbridge.gateway.RouteResolver;
import com.mobiliz.ysbridge.legacy.EnvelopeParser;
import com.mobiliz.ysbridge.legacy.LegacyApiClient;
import com.mobiliz.ysbridge.mapping.model.MappingCatalog;
import com.mobiliz.ysbridge.observability.MappingCoverageRecorder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Wires the gateway: the legacy client, the router, the error catalog, the recorder. */
@Configuration(proxyBeanMethods = false)
public class GatewayConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfiguration.class);

    private static final String ERROR_CATALOG = "error-catalog.yaml";

    @Bean
    RouteResolver routeResolver(MappingCatalog catalog) {
        return new RouteResolver(catalog);
    }

    @Bean
    CollectionPaginator collectionPaginator(BridgeProperties properties) {
        return new CollectionPaginator(properties);
    }

    @Bean
    MappingCoverageRecorder mappingCoverageRecorder() {
        return new MappingCoverageRecorder();
    }

    @Bean
    EnvelopeParser envelopeParser(ObjectMapper objectMapper) {
        return new EnvelopeParser(objectMapper);
    }

    @Bean
    ErrorCatalog errorCatalog() {
        ClassPathResource resource = new ClassPathResource(ERROR_CATALOG);
        try {
            ErrorCatalog catalog =
                    new ErrorCatalogLoader()
                            .parse(
                                    resource.getContentAsString(StandardCharsets.UTF_8),
                                    ERROR_CATALOG);
            log.info("Loaded {} error rule(s) from {}", catalog.size(), ERROR_CATALOG);
            return catalog;
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + ERROR_CATALOG, e);
        }
    }

    /**
     * The client that talks to the legacy API.
     *
     * <p>Redirects are never followed: a gateway that chases a redirect can be walked off its
     * configured host, and the legacy API has no legitimate reason to issue one.
     */
    @Bean
    LegacyApiClient legacyApiClient(BridgeProperties properties) {
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(properties.legacy().connectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.legacy().readTimeout());

        RestClient restClient =
                RestClient.builder()
                        .baseUrl(properties.legacy().baseUrl())
                        .requestFactory(requestFactory)
                        .build();

        log.info(
                "Legacy API at {} (connect {}, read {})",
                properties.legacy().baseUrl(),
                properties.legacy().connectTimeout(),
                properties.legacy().readTimeout());
        return new LegacyApiClient(restClient);
    }
}
