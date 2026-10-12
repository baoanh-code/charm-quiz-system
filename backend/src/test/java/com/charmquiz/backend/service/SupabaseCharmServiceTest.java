package com.charmquiz.backend.service;

import com.charmquiz.backend.dto.CharmCatalogItemResponse;
import com.charmquiz.backend.model.CharmStockStatus;
import com.charmquiz.backend.model.EligibleCharm;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseCharmServiceTest {

    @Test
    void readsActiveCatalogWithExplicitColumnsAndBothStockStatuses() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://project.supabase.co/rest/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SupabaseCharmService service = new SupabaseCharmService(builder.build(), new ObjectMapper());

        server.expect(request -> {
                    String query = request.getURI().getQuery();
                    assertThat(query)
                            .contains("status=eq.ACTIVE")
                            .contains("select=id,name,description,image_url,category,stock_status")
                            .contains("order=name.asc.nullslast,id.asc")
                            .doesNotContain("stock_status=eq.IN_STOCK")
                            .doesNotContain("select=*");
                })
                .andRespond(withSuccess(
                        """
                        [
                          {
                            "id": "charm-1",
                            "name": "Available Charm",
                            "description": null,
                            "image_url": "https://example.com/available.png",
                            "category": null,
                            "stock_status": "IN_STOCK"
                          },
                          {
                            "id": "charm-2",
                            "name": "Sold Out Charm",
                            "description": "Still visible",
                            "image_url": "https://example.com/sold-out.png",
                            "category": "seasonal",
                            "stock_status": "OUT_OF_STOCK"
                          }
                        ]
                        """,
                        MediaType.APPLICATION_JSON
                ));

        List<CharmCatalogItemResponse> result = service.getCatalogCharms();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).stockStatus()).isEqualTo(CharmStockStatus.IN_STOCK);
        assertThat(result.get(0).available()).isTrue();
        assertThat(result.get(1).stockStatus()).isEqualTo(CharmStockStatus.OUT_OF_STOCK);
        assertThat(result.get(1).available()).isFalse();
        server.verify();
    }

    @Test
    void rejectsInvalidCatalogStockStatusAsUpstreamDataError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://project.supabase.co/rest/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SupabaseCharmService service = new SupabaseCharmService(builder.build(), new ObjectMapper());

        server.expect(requestTo(containsString("/charm?status=eq.ACTIVE")))
                .andRespond(withSuccess(
                        """
                        [{
                          "id": "charm-1",
                          "name": "Broken Charm",
                          "description": null,
                          "image_url": "https://example.com/broken.png",
                          "category": null,
                          "stock_status": "AVAILABLE"
                        }]
                        """,
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(service::getCatalogCharms)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid stock_status from Supabase: AVAILABLE");
        server.verify();
    }

    @Test
    void readsEligibleCatalogAndParsesCommaSeparatedTagText() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://project.supabase.co/rest/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SupabaseCharmService service = new SupabaseCharmService(
                builder.build(),
                new ObjectMapper()
        );

        server.expect(request -> {
                    String query = request.getURI().getQuery();
                    assertThat(query)
                            .contains("status=eq.ACTIVE")
                            .contains("stock_status=eq.IN_STOCK");
                })
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
