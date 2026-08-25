package com.mobiliz.ysbridge.observability;

import com.mobiliz.ysbridge.gateway.exception.MissingTokenException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;
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
 *
 * <p>It requires the same bearer token every other endpoint does. What it reveals — the legacy
 * field names of endpoints that are not fully mapped — is exactly what a caller already sees in
 * those endpoints' untranslated responses, so gating it at the same level is coherent: someone who
 * can call the API learns nothing new here, and someone who cannot learns nothing at all.
 *
 * <p>The token is only checked for presence, because this service does not validate tokens — it
 * relays them. That is a weaker gate than it looks, and the report should still not be routed from
 * outside; see C17 in docs/open-items.md.
 */
@RestController
public class MappingCoverageController {

    private final MappingCoverageRecorder recorder;

    public MappingCoverageController(MappingCoverageRecorder recorder) {
        this.recorder = recorder;
    }

    @GetMapping("/internal/mapping/coverage")
    public CoverageReport coverage(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new MissingTokenException();
        }
        Map<String, Set<String>> findings = recorder.findings();
        return new CoverageReport(findings.isEmpty(), findings);
    }

    /**
     * @param clean whether nothing has been reported
     * @param findings resource name to the gaps observed for it
     */
    public record CoverageReport(boolean clean, Map<String, Set<String>> findings) {}
}
