package com.mobiliz.ysbridge.observability;

import com.mobiliz.ysbridge.mapping.Direction;
import com.mobiliz.ysbridge.mapping.engine.TranslationObserver;
import com.mobiliz.ysbridge.mapping.model.FieldType;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remembers every gap the translator reported, per resource.
 *
 * <p>This is what turns "we think the mapping is complete" into evidence. A field the legacy API
 * starts sending, a value outside its table, a shape that changed — each is recorded here and
 * exposed at {@code /internal/mapping/coverage}, so the mapping files can be completed from what
 * production actually sees.
 *
 * <p>Entries are capped per resource. Without a cap, a caller sending junk fields could grow this
 * map without bound.
 */
public class MappingCoverageRecorder {

    private static final Logger log = LoggerFactory.getLogger("mapping-coverage");

    /** Beyond this many distinct findings for one resource, further ones are only logged. */
    static final int MAX_ENTRIES_PER_RESOURCE = 200;

    private final Map<String, Set<String>> findingsByResource = new ConcurrentHashMap<>();

    /** Returns an observer that attributes everything it sees to {@code resourceName}. */
    public TranslationObserver observerFor(String resourceName) {
        return new TranslationObserver() {

            @Override
            public void unmappedField(Direction direction, String path) {
                record(resourceName, "unmapped-field %s %s".formatted(direction, path));
            }

            @Override
            public void unmappedValue(Direction direction, String path, String value) {
                // The value itself is deliberately not recorded: it is payload data and
                // could be personal or a credential. The path is enough to find the gap.
                record(resourceName, "unmapped-value %s %s".formatted(direction, path));
            }

            @Override
            public void typeMismatch(
                    Direction direction, String path, String value, FieldType expected) {
                record(
                        resourceName,
                        "type-mismatch %s %s expected %s".formatted(direction, path, expected));
            }

            @Override
            public void unexpectedShape(Direction direction, String path, String actualType) {
                record(
                        resourceName,
                        "unexpected-shape %s %s is %s".formatted(direction, path, actualType));
            }
        };
    }

    /**
     * Records a payload that arrived for a resource with no response mapping at all.
     *
     * <p>Without this the report is misleading in exactly the case that matters most. An endpoint
     * with no {@code response} block relays its payload untranslated and never reaches the
     * translator, so nothing was observed and the report said {@code clean} — while a whole
     * endpoint was passing Turkish field names through. The gaps the report is meant to reveal were
     * the ones it could not see.
     *
     * <p>The top-level names are recorded because they are what someone completing the mapping
     * needs. Values are not: they are payload data, and could be personal or a credential.
     */
    public void unmappedResponse(String resourceName, Iterable<String> topLevelFieldNames) {
        for (String name : topLevelFieldNames) {
            record(resourceName, "no-response-mapping " + name);
        }
    }

    private void record(String resourceName, String finding) {
        Set<String> findings =
                findingsByResource.computeIfAbsent(
                        resourceName, key -> ConcurrentHashMap.newKeySet());
        if (findings.size() >= MAX_ENTRIES_PER_RESOURCE) {
            log.warn("{}: {} (coverage store full, not retained)", resourceName, finding);
            return;
        }
        if (findings.add(finding)) {
            log.info("{}: {}", resourceName, finding);
        }
    }

    /** Everything recorded so far, resource name to sorted findings. */
    public Map<String, Set<String>> findings() {
        Map<String, Set<String>> snapshot = new TreeMap<>();
        findingsByResource.forEach(
                (resource, findings) -> snapshot.put(resource, new TreeSet<>(findings)));
        return Map.copyOf(snapshot);
    }

    /** Whether nothing has been reported, i.e. the mappings covered every payload seen. */
    public boolean isClean() {
        return findingsByResource.values().stream().allMatch(Set::isEmpty);
    }
}
