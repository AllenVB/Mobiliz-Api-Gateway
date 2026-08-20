package com.mobiliz.ysbridge.gateway;

import com.mobiliz.ysbridge.mapping.model.MappingCatalog;
import com.mobiliz.ysbridge.mapping.model.ResourceMapping;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Matches an incoming request against the exposed routes of the mapping catalog.
 *
 * <p>Patterns are compiled once at startup and ordered most-specific-first, so a literal segment
 * always wins over a variable one. Without that ordering, {@code /api/v1/work-orders/{listFilter}}
 * could swallow a request meant for a more specific sibling.
 */
public class RouteResolver {

    private final List<CompiledRoute> routes;

    public RouteResolver(MappingCatalog catalog) {
        PathPatternParser parser = new PathPatternParser();
        List<CompiledRoute> compiled = new ArrayList<>();
        for (ResourceMapping resource : catalog.resources()) {
            compiled.add(new CompiledRoute(parser.parse(resource.exposedPath()), resource));
        }
        compiled.sort(
                Comparator.comparing(CompiledRoute::pattern, PathPattern.SPECIFICITY_COMPARATOR));
        this.routes = List.copyOf(compiled);
    }

    /**
     * @param method method of the incoming request
     * @param path path of the incoming request, e.g. {@code /api/v1/auth/login}
     * @return the matching resource and the path variables extracted from it
     */
    public Optional<RouteMatch> resolve(HttpMethod method, String path) {
        PathContainer container = PathContainer.parsePath(path);
        for (CompiledRoute route : routes) {
            if (!route.resource().method().equals(method)) {
                continue;
            }
            PathPattern.PathMatchInfo match = route.pattern().matchAndExtract(container);
            if (match != null) {
                return Optional.of(new RouteMatch(route.resource(), match.getUriVariables()));
            }
        }
        return Optional.empty();
    }

    /**
     * Whether any route serves {@code path} under some other method. Lets the caller answer 405
     * Method Not Allowed rather than a misleading 404.
     */
    public boolean pathExistsUnderAnotherMethod(String path) {
        PathContainer container = PathContainer.parsePath(path);
        for (CompiledRoute route : routes) {
            if (route.pattern().matches(container)) {
                return true;
            }
        }
        return false;
    }

    /** Methods that {@code path} does accept, for the Allow header of a 405. */
    public List<HttpMethod> methodsFor(String path) {
        PathContainer container = PathContainer.parsePath(path);
        List<HttpMethod> methods = new ArrayList<>();
        for (CompiledRoute route : routes) {
            if (route.pattern().matches(container)
                    && !methods.contains(route.resource().method())) {
                methods.add(route.resource().method());
            }
        }
        return List.copyOf(methods);
    }

    private record CompiledRoute(PathPattern pattern, ResourceMapping resource) {}

    /**
     * @param resource the mapping that serves the request
     * @param pathVariables variables extracted from the exposed path
     */
    public record RouteMatch(ResourceMapping resource, Map<String, String> pathVariables) {}
}
