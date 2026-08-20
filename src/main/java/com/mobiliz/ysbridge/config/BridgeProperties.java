package com.mobiliz.ysbridge.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration of the bridge.
 *
 * <p>{@code baseUrl} has no default on purpose. A bridge with no upstream is useless, so failing to
 * start is more helpful than starting and failing every request.
 *
 * @param legacy how to reach the legacy API
 * @param mapping where the mapping documents live
 * @param pagination how collection responses are sliced
 * @param limits bounds the gateway enforces on inbound requests
 */
@Validated
@ConfigurationProperties(prefix = "ys-bridge")
public record BridgeProperties(
        @Valid @DefaultValue Legacy legacy,
        @Valid @DefaultValue Mapping mapping,
        @Valid @DefaultValue Pagination pagination,
        @Valid @DefaultValue Limits limits) {
    // @Valid is load-bearing: without it Bean Validation does not descend into the nested
    // records and the constraints below are never evaluated.

    /**
     * @param baseUrl origin of the legacy API, without a trailing slash
     * @param connectTimeout how long to wait for the connection to be established
     * @param readTimeout how long to wait for the response
     */
    public record Legacy(
            @NotBlank(message = "ys-bridge.legacy.base-url must be set") String baseUrl,
            @DefaultValue("5s") Duration connectTimeout,
            @DefaultValue("15s") Duration readTimeout) {}

    /**
     * @param locations Spring resource patterns holding the mapping documents
     */
    public record Mapping(
            @NotEmpty @DefaultValue("classpath*:mapping/*.yaml") List<String> locations) {}

    /**
     * Collection responses are always paged.
     *
     * <p>The legacy API does not paginate — it answers with whole collections, one of which is
     * roughly a megabyte. The gateway therefore pages on its own side: it bounds what the caller
     * has to receive and parse, though not what the legacy API has to produce. When the legacy API
     * gains real pagination the exposed contract does not have to change.
     *
     * @param defaultSize page size when the caller does not ask for one
     * @param maxSize largest page a caller may request
     */
    public record Pagination(
            @Min(1) @Max(1000) @DefaultValue("100") int defaultSize,
            @Min(1) @Max(5000) @DefaultValue("500") int maxSize) {

        public Pagination {
            if (defaultSize > maxSize) {
                throw new IllegalArgumentException(
                        "ys-bridge.pagination.default-size (%d) must not exceed max-size (%d)"
                                .formatted(defaultSize, maxSize));
            }
        }
    }

    /**
     * @param maxRequestBody largest request body the gateway accepts; file uploads travel as base64
     *     inside JSON, which inflates them by roughly a third, so this needs headroom
     */
    public record Limits(@DefaultValue("10MB") DataSize maxRequestBody) {}
}
