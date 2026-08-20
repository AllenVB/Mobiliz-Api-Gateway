package com.mobiliz.ysbridge.mapping.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * All resource mappings the bridge serves, indexed by name and by the exposed route.
 *
 * <p>Built once at startup. Construction fails on a conflicting definition, so a misconfigured
 * deployment never reaches the point of serving traffic.
 */
public final class MappingCatalog {

    private final Map<String, ResourceMapping> byName;
    private final Map<String, ResourceMapping> byRoute;

    public MappingCatalog(Collection<ResourceMapping> resources) {
        Objects.requireNonNull(resources, "resources");
        Map<String, ResourceMapping> nameIndex = new LinkedHashMap<>();
        Map<String, ResourceMapping> routeIndex = new LinkedHashMap<>();
        for (ResourceMapping resource : resources) {
            ResourceMapping duplicateName = nameIndex.put(resource.name(), resource);
            if (duplicateName != null) {
                throw new IllegalArgumentException(
                        "resource '%s' is defined twice".formatted(resource.name()));
            }
            String route = routeKey(resource.method().name(), resource.exposedPath());
            ResourceMapping duplicateRoute = routeIndex.put(route, resource);
            if (duplicateRoute != null) {
                throw new IllegalArgumentException(
                        "resources '%s' and '%s' both serve %s"
                                .formatted(duplicateRoute.name(), resource.name(), route));
            }
        }
        this.byName = Map.copyOf(nameIndex);
        this.byRoute = Map.copyOf(routeIndex);
    }

    private static String routeKey(String method, String exposedPath) {
        return method + " " + exposedPath;
    }

    public Optional<ResourceMapping> byName(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public List<ResourceMapping> resources() {
        return List.copyOf(byName.values());
    }

    public int size() {
        return byName.size();
    }
}
