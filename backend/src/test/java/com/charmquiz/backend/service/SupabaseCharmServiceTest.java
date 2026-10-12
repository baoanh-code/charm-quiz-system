package com.charmquiz.backend.service;

import com.charmquiz.backend.model.EligibleCharm;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseCharmServiceTest {

    @Test
    void readsEligibleCatalogAndParsesCommaSeparatedTagText() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://project.supabase.co/rest/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SupabaseCharmService service = new SupabaseCharmService(
                builder.build(),
                new ObjectMapper()
        );

        server.expect(requestTo(containsString(
                        "/charm?status=eq.ACTIVE&stock_status=eq.IN_STOCK")))
                .andRespond(withSuccess(
                        """
                        [{
                          "id": "charm-1",
                          "name": "Music Charm",
                          "description": "For songs",
                          "image_url": "https://example.com/music.png",
                          "category": "music",
                          "tags": " music, song ",
                          "status": "ACTIVE",
                          "stock_status": "IN_STOCK"
                        }]
                        """,
                        MediaType.APPLICATION_JSON
                ));

        List<EligibleCharm> result = service.getEligibleCharms();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().normalizedTags()).containsExactly("music", "song");
        server.verify();
    }
}
