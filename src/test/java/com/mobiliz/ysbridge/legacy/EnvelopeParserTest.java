package com.mobiliz.ysbridge.legacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class EnvelopeParserTest {

    private final EnvelopeParser parser = new EnvelopeParser(new ObjectMapper());

    @Nested
    class WellFormedEnvelopes {

        @Test
        void shouldReadASuccessfulEnvelopeWithAnObjectResult() {
            LegacyEnvelope envelope =
                    parser.parse(
                            """
                            {"success": true, "type": "SUCCESS", "message": null,
                             "result": {"adSoyad": "Saha Destek 1"}}""",
                            MediaType.APPLICATION_JSON);

            assertThat(envelope.success()).isTrue();
            assertThat(envelope.type()).isEqualTo("SUCCESS");
            assertThat(envelope.message()).isNull();
            assertThat(envelope.result().get("adSoyad").asText()).isEqualTo("Saha Destek 1");
            assertThat(envelope.isEmptySuccess()).isFalse();
        }

        @Test
        void shouldReadASuccessfulEnvelopeWithAnArrayResult() {
            LegacyEnvelope envelope =
                    parser.parse(
                            """
                            {"success": true, "type": "SUCCESS", "message": null,
                             "result": [{"detayId": "A"}]}""",
                            MediaType.APPLICATION_JSON);

            assertThat(envelope.result().isArray()).isTrue();
            assertThat(envelope.result()).hasSize(1);
        }

        @Test
        void shouldRecogniseASuccessCarryingNoPayload() {
            LegacyEnvelope envelope =
                    parser.parse(
                            """
                            {"success": true, "type": "SUCCESS", "message": null, "result": null}""",
                            MediaType.APPLICATION_JSON);

            assertThat(envelope.isEmptySuccess()).isTrue();
        }

        @Test
        void shouldTreatAMissingResultKeyAsAnEmptySuccess() {
            LegacyEnvelope envelope =
                    parser.parse(
                            """
                            {"success": true, "type": "SUCCESS"}""",
                            MediaType.APPLICATION_JSON);

            assertThat(envelope.isEmptySuccess()).isTrue();
        }

        @Test
        void shouldReadTheCapturedFailureEnvelope() {
            LegacyEnvelope envelope =
                    parser.parse(
                            """
                            {"success": false, "type": "ERROR",
                             "message": "Token Bilgisi Eksik", "result": null}""",
                            MediaType.APPLICATION_JSON);

            assertThat(envelope.success()).isFalse();
            assertThat(envelope.type()).isEqualTo("ERROR");
            assertThat(envelope.message()).isEqualTo("Token Bilgisi Eksik");
        }

        @Test
        void shouldParseEvenWhenTheContentTypeIsAbsent() {
            LegacyEnvelope envelope =
                    parser.parse(
                            """
                    {"success": true, "result": {}}""",
                            null);

            assertThat(envelope.success()).isTrue();
        }
    }

    @Nested
    class NonEnvelopeResponses {

        /** Condensed from the real Cloudflare interstitial captured on `sifremiunuttum`. */
        private static final String CLOUDFLARE_CHALLENGE =
                """
                <!DOCTYPE html><html lang="en-US"><head><title>Just a moment...</title>
                <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
                <script src="https://challenges.cloudflare.com/turnstile/v0/api.js"></script>
                </head><body><div class="main-wrapper" role="main">
                <script>window._cf_chl_opt = {cvId: '3'};</script></body></html>""";

        @Test
        void shouldRecogniseABotProtectionChallengeRatherThanBlamingTheApi() {
            assertThatThrownBy(() -> parser.parse(CLOUDFLARE_CHALLENGE, MediaType.TEXT_HTML))
                    .isInstanceOf(LegacyProtocolException.class)
                    .hasMessageContaining("bot-protection challenge")
                    .asInstanceOf(
                            org.assertj.core.api.InstanceOfAssertFactories.type(
                                    LegacyProtocolException.class))
                    .satisfies(e -> assertThat(e.isUpstreamChallenge()).isTrue());
        }

        @Test
        void shouldRecogniseAChallengeEvenWhenTheContentTypeIsMissing() {
            assertThatThrownBy(() -> parser.parse(CLOUDFLARE_CHALLENGE, null))
                    .isInstanceOf(LegacyProtocolException.class)
                    .hasMessageContaining("bot-protection challenge");
        }

        @Test
        void shouldRejectPlainHtmlAsAnApiFaultNotAChallenge() {
            assertThatThrownBy(
                            () ->
                                    parser.parse(
                                            "<html><body><h1>502 Bad Gateway</h1></body></html>",
                                            MediaType.TEXT_HTML))
                    .isInstanceOf(LegacyProtocolException.class)
                    .hasMessageContaining("returned HTML instead of JSON")
                    .asInstanceOf(
                            org.assertj.core.api.InstanceOfAssertFactories.type(
                                    LegacyProtocolException.class))
                    .satisfies(e -> assertThat(e.isUpstreamChallenge()).isFalse());
        }

        @Test
        void shouldRejectAnEmptyBody() {
            assertThatThrownBy(() -> parser.parse("   ", MediaType.APPLICATION_JSON))
                    .isInstanceOf(LegacyProtocolException.class)
                    .hasMessageContaining("empty body");
        }

        @Test
        void shouldRejectMalformedJson() {
            assertThatThrownBy(() -> parser.parse("{\"success\": tru", MediaType.APPLICATION_JSON))
                    .isInstanceOf(LegacyProtocolException.class)
                    .hasMessageContaining("not valid JSON");
        }

        @Test
        void shouldRejectJsonThatIsNotAnObject() {
            assertThatThrownBy(() -> parser.parse("[1, 2, 3]", MediaType.APPLICATION_JSON))
                    .isInstanceOf(LegacyProtocolException.class)
                    .hasMessageContaining("not an envelope object");
        }

        @Test
        void shouldRejectAnObjectWithoutASuccessFlag() {
            assertThatThrownBy(
                            () ->
                                    parser.parse(
                                            """
                                    {"type": "SUCCESS", "result": {}}""",
                                            MediaType.APPLICATION_JSON))
                    .isInstanceOf(LegacyProtocolException.class)
                    .hasMessageContaining("no boolean 'success' field");
        }

        @Test
        void shouldNotLeakTheResponseBodyIntoTheMessage() {
            String secretBearing =
                    """
                    {"success": "yes", "internalToken": "SHOULD-NOT-APPEAR"}""";

            assertThatThrownBy(() -> parser.parse(secretBearing, MediaType.APPLICATION_JSON))
                    .isInstanceOf(LegacyProtocolException.class)
                    .hasMessageNotContaining("SHOULD-NOT-APPEAR");
        }
    }
}
