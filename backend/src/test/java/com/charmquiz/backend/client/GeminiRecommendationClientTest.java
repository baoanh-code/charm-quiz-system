package com.charmquiz.backend.client;

import com.charmquiz.backend.config.GeminiProperties;
import com.charmquiz.backend.model.EligibleCharm;
import com.charmquiz.backend.model.RecommendationSectionType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GeminiRecommendationClientTest {

    @Test
    void sendsOnlyProvidedSectionsAndParsesStructuredCharmIds() throws Exception {
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey("test-key");
        properties.setModel("gemini-2.5-flash-lite");
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/models");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ObjectMapper objectMapper = new ObjectMapper();
        GeminiRecommendationClient client = new GeminiRecommendationClient(
                properties,
                builder.build(),
                objectMapper
        );
        String structuredText = objectMapper.writeValueAsString(Map.of(
                "sections", List.of(Map.of(
                        "type", "INTERESTS",
                        "charmIds", List.of("charm-1")
                ))
        ));
        String providerResponse = objectMapper.writeValueAsString(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of("text", structuredText))
                        )
                ))
        ));

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/"
                        + "gemini-2.5-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(content().string(containsString("INTERESTS")))
                .andExpect(content().string(containsString("music")))
                .andExpect(content().string(not(containsString("private-full-name"))))
                .andExpect(content().string(not(containsString("2006-07-24"))))
                .andRespond(withSuccess(providerResponse, MediaType.APPLICATION_JSON));

        Map<RecommendationSectionType, List<String>> result = client.recommend(
                Map.of(RecommendationSectionType.INTERESTS, List.of("music")),
                List.of(charm("charm-1"))
        );

        assertThat(result).containsEntry(
                RecommendationSectionType.INTERESTS,
                List.of("charm-1")
        );
        server.verify();
    }

    @Test
    void wrapsProviderRateLimitAsRecoverableAiError() {
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey("test-key");
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/models");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiRecommendationClient client = new GeminiRecommendationClient(
                properties,
                builder.build(),
                new ObjectMapper()
        );
        server.expect(requestTo(containsString(":generateContent")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.recommend(
                Map.of(RecommendationSectionType.INTERESTS, List.of("music")),
                List.of(charm("charm-1"))
        )).isInstanceOf(AiRecommendationException.class);
        server.verify();
    }

    @Test
    void rejectsMalformedStructuredJsonAsRecoverableAiError() throws Exception {
        GeminiProperties properties = configuredProperties();
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/models");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ObjectMapper objectMapper = new ObjectMapper();
        GeminiRecommendationClient client = new GeminiRecommendationClient(
                properties,
                builder.build(),
                objectMapper
        );

        server.expect(requestTo(containsString(":generateContent")))
                .andRespond(withSuccess(
                        providerResponse(objectMapper, "{not-json"),
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> client.recommend(
                Map.of(RecommendationSectionType.INTERESTS, List.of("music")),
                List.of(charm("charm-1"))
        )).isInstanceOf(AiRecommendationException.class);
        server.verify();
    }

    @Test
    void rejectsUnknownSectionAsRecoverableAiError() throws Exception {
        GeminiProperties properties = configuredProperties();
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/models");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ObjectMapper objectMapper = new ObjectMapper();
        GeminiRecommendationClient client = new GeminiRecommendationClient(
                properties,
                builder.build(),
                objectMapper
        );
        String structuredText = objectMapper.writeValueAsString(Map.of(
                "sections", List.of(Map.of(
                        "type", "UNKNOWN",
                        "charmIds", List.of("charm-1")
                ))
        ));

        server.expect(requestTo(containsString(":generateContent")))
                .andRespond(withSuccess(
                        providerResponse(objectMapper, structuredText),
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> client.recommend(
                Map.of(RecommendationSectionType.INTERESTS, List.of("music")),
                List.of(charm("charm-1"))
        )).isInstanceOf(AiRecommendationException.class)
                .hasMessageContaining("unknown section");
        server.verify();
    }

    private GeminiProperties configuredProperties() {
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey("test-key");
        properties.setModel("gemini-2.5-flash-lite");
        return properties;
    }

    private String providerResponse(ObjectMapper objectMapper, String structuredText) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of("text", structuredText))
                        )
                ))
        ));
    }

    private EligibleCharm charm(String id) {
        return new EligibleCharm(
                id,
                "Music Charm",
                "For music lovers",
                "https://example.com/charm.png",
                "music",
                "music,song",
                "ACTIVE",
                "IN_STOCK"
        );
    }
}
