package com.mobiliz.ysbridge.config;

import com.mobiliz.ysbridge.mapping.engine.JsonTranslator;
import com.mobiliz.ysbridge.mapping.loader.MappingConfigurationException;
import com.mobiliz.ysbridge.mapping.loader.MappingYamlLoader;
import com.mobiliz.ysbridge.mapping.model.MappingCatalog;
import com.mobiliz.ysbridge.mapping.model.ResourceMapping;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * Loads the mapping documents once, at startup.
 *
 * <p>Any problem here aborts startup. A mapping file with a typo would otherwise surface as a field
 * quietly missing from a production response, which is far harder to notice than a service that
 * refuses to start with the offending line named.
 */
@Configuration(proxyBeanMethods = false)
public class MappingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MappingConfiguration.class);

    @Bean
    MappingCatalog mappingCatalog(BridgeProperties properties, ResourcePatternResolver resolver) {
        MappingYamlLoader loader = new MappingYamlLoader();
        List<ResourceMapping> mappings = new ArrayList<>();

        for (String location : properties.mapping().locations()) {
            for (Resource resource : findResources(resolver, location)) {
                mappings.addAll(loader.parse(read(resource), describe(resource)));
            }
        }
        if (mappings.isEmpty()) {
            throw new MappingConfigurationException(
                    String.join(", ", properties.mapping().locations()),
                    "matched no mapping documents; the bridge would have nothing to serve");
        }

        MappingCatalog catalog = new MappingCatalog(mappings);
        log.info(
                "Loaded {} resource mapping(s): {}",
                catalog.size(),
                catalog.resources().stream().map(ResourceMapping::name).sorted().toList());
        return catalog;
    }

    @Bean
    JsonTranslator jsonTranslator() {
        return new JsonTranslator();
    }

    /** Returns matching resources in a stable order, so startup logs are reproducible. */
    private static List<Resource> findResources(ResourcePatternResolver resolver, String location) {
        Resource[] found;
        try {
            found = resolver.getResources(location);
        } catch (IOException e) {
            throw new MappingConfigurationException(location, "could not be resolved", e);
        }
        return Arrays.stream(found)
                .sorted(Comparator.comparing(MappingConfiguration::describe))
                .toList();
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MappingConfigurationException(describe(resource), "could not be read", e);
        }
    }

    private static String describe(Resource resource) {
        String filename = resource.getFilename();
        return filename != null ? filename : resource.getDescription();
    }
}
