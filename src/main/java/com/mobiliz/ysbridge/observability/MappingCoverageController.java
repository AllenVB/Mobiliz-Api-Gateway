package com.mobiliz.ysbridge.observability;

import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports what the mappings did not cover.
 *
 * <p>An empty report means every payload seen so far was fully translated. A non-empty one names
 * the resource and the path to fix, which is how the mapping documents get completed from evidence
 * rather than guesswork.
 *
 * <p>Under {@code /internal} because it describes the bridge, not the business domain.
 */
@RestController
public class MappingCoverageController {

    private final MappingCoverageRecorder recorder;

    public MappingCoverageController(MappingCoverageRecorder recorder) {
        this.recorder = recorder;
    }

    @GetMapping("/internal/mapping/coverage")
    public CoverageReport coverage() {
        Map<String, Set<String>> findings = recorder.findings();
        return new CoverageReport(findings.isEmpty(), findings);
    }

    /**
     * @param clean whether nothing has been reported
     * @param findings resource name to the gaps observed for it
     */
    public record CoverageReport(boolean clean, Map<String, Set<String>> findings) {}
}
