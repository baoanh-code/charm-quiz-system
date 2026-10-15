package com.charmquiz.backend.service;

import com.charmquiz.backend.dto.ManagerCharmResponse;
import com.charmquiz.backend.dto.ManagerCharmUpsertRequest;
import com.charmquiz.backend.exception.ManagerCharmNotFoundException;
import com.charmquiz.backend.exception.ManagerConflictException;
import com.charmquiz.backend.exception.ManagerUpstreamException;
import com.charmquiz.backend.model.CharmStatus;
import com.charmquiz.backend.model.CharmStockStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseManagerCharmServiceTest {

    private static final UUID CHARM_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String SECRET = "test-secret-value";

    private MockRestServiceServer server;
    private SupabaseManagerCharmService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://project.supabase.co/rest/v1")
                .defaultHeader("apikey", SECRET);
        server = MockRestServiceServer.bindTo(builder).build();
        service = new SupabaseManagerCharmService(builder.build(), new ObjectMapper());
    }

    @Test
    void listsAllCharmsWithExplicitColumnsStableOrderAndTagConversion() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/rest/v1/charm");
                    assertThat(request.getURI().getQuery())
                            .contains("select=" + SupabaseManagerCharmService.SELECT_COLUMNS)
                            .contains("order=name.asc.nullslast,id.asc")
                            .doesNotContain("status=eq.ACTIVE")
                            .doesNotContain("select=*")
                            .doesNotContain(SECRET);
                    assertThat(request.getHeaders().getFirst("apikey")).isEqualTo(SECRET);
                    assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
                })
                .andRespond(withSuccess("""
                        [
                          {
                            "id": "11111111-1111-1111-1111-111111111111",
                            "name": "Music Charm",
                            "description": "  ",
                            "image_url": "https://example.com/music.png",
                            "category": null,
                            "tags": " music, song, MUSIC ",
                            "status": "ACTIVE",
                            "stock_status": "IN_STOCK"
                          },
                          {
                            "id": "22222222-2222-2222-2222-222222222222",
                            "name": "Hidden Charm",
                            "description": null,
                            "image_url": "https://example.com/hidden.png",
                            "category": "Legacy",
                            "tags": null,
                            "status": "INACTIVE",
                            "stock_status": "OUT_OF_STOCK"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<ManagerCharmResponse> result = service.getAllCharms();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ManagerCharmResponse::status)
                .containsExactly(CharmStatus.ACTIVE, CharmStatus.INACTIVE);
        assertThat(result.getFirst().tags()).containsExactly("music", "song");
        assertThat(result.getFirst().description()).isNull();
        assertThat(result.getFirst().category()).isNull();
        server.verify();
    }

    @Test
    void getsOneCharmUsingExactUuidFilter() {
        server.expect(request -> assertThat(request.getURI().getQuery())
                        .isEqualTo("id=eq." + CHARM_ID
                                + "&select=" + SupabaseManagerCharmService.SELECT_COLUMNS
                                + "&limit=1"))
                .andRespond(withSuccess(rowJson(CharmStatus.ACTIVE, CharmStockStatus.IN_STOCK),
                        MediaType.APPLICATION_JSON));

        assertThat(service.getCharm(CHARM_ID).id()).isEqualTo(CHARM_ID.toString());
        server.verify();
    }

    @Test
    void createNormalizesOptionalFieldsAndSerializesTagsAsText() {
        server.expect(request -> {
                    assertThat(request.getMethod().name()).isEqualTo("POST");
                    assertThat(request.getHeaders().getFirst("Prefer"))
                            .isEqualTo("return=representation");
                })
                .andExpect(content().json("""
                        {
                          "name": "Music Charm",
                          "description": null,
                          "image_url": "https://example.com/music.png",
                          "category": null,
                          "tags": "Music,song",
                          "status": "ACTIVE",
                          "stock_status": "IN_STOCK"
                        }
                        """))
                .andRespond(withSuccess(rowJson(CharmStatus.ACTIVE, CharmStockStatus.IN_STOCK),
                        MediaType.APPLICATION_JSON));

        ManagerCharmResponse created = service.createCharm(new ManagerCharmUpsertRequest(
                " Music Charm ",
                " ",
                "https://example.com/music.png",
                "",
                List.of("Music", " song ", "music"),
                CharmStatus.ACTIVE,
                CharmStockStatus.IN_STOCK
        ));

        assertThat(created.id()).isEqualTo(CHARM_ID.toString());
        server.verify();
    }

    @Test
    void updateUsesPatchWithExactUuidFilter() {
        server.expect(request -> {
                    assertThat(request.getMethod().name()).isEqualTo("PATCH");
                    assertThat(request.getURI().getQuery())
                            .startsWith("id=eq." + CHARM_ID + "&select=")
                            .endsWith("&limit=1");
                })
                .andRespond(withSuccess(rowJson(CharmStatus.ACTIVE, CharmStockStatus.IN_STOCK),
                        MediaType.APPLICATION_JSON));

        service.updateCharm(CHARM_ID, request());
        server.verify();
    }

    @Test
    void statusPatchContainsOnlyStatus() {
        server.expect(request -> assertThat(request.getMethod().name()).isEqualTo("PATCH"))
                .andExpect(content().string("{\"status\":\"INACTIVE\"}"))
                .andRespond(withSuccess(rowJson(CharmStatus.INACTIVE, CharmStockStatus.IN_STOCK),
                        MediaType.APPLICATION_JSON));

        service.updateStatus(CHARM_ID, CharmStatus.INACTIVE);
        server.verify();
    }

    @Test
    void stockPatchContainsOnlyStockStatus() {
        server.expect(request -> assertThat(request.getMethod().name()).isEqualTo("PATCH"))
                .andExpect(content().string("{\"stock_status\":\"OUT_OF_STOCK\"}"))
                .andRespond(withSuccess(rowJson(CharmStatus.ACTIVE, CharmStockStatus.OUT_OF_STOCK),
                        MediaType.APPLICATION_JSON));

        service.updateStockStatus(CHARM_ID, CharmStockStatus.OUT_OF_STOCK);
        server.verify();
    }

    @Test
    void emptyExactLookupAndUpdateAreNotFound() {
        server.expect(request -> {
                }).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.getCharm(CHARM_ID))
                .isInstanceOf(ManagerCharmNotFoundException.class);
        server.verify();

        server.reset();
        server.expect(request -> {
                }).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.updateStatus(CHARM_ID, CharmStatus.INACTIVE))
                .isInstanceOf(ManagerCharmNotFoundException.class);
        server.verify();
    }

    @Test
    void upstreamFailuresAreMappedWithoutExposingResponse() {
        server.expect(request -> {
                }).andRespond(withServerError());

        assertThatThrownBy(service::getAllCharms)
                .isInstanceOf(ManagerUpstreamException.class)
                .hasMessage("Supabase charm request failed.");
        server.verify();
    }

    @Test
    void upstreamClientFailuresAreMappedWithoutExposingResponse() {
        server.expect(request -> {
                }).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("sensitive upstream detail")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(service::getAllCharms)
                .isInstanceOf(ManagerUpstreamException.class)
                .hasMessage("Supabase charm request failed.")
                .hasMessageNotContaining("sensitive upstream detail");
        server.verify();
    }

    @Test
    void upstreamConflictIsMappedToConflict() {
        server.expect(request -> {
                }).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> service.createCharm(request()))
                .isInstanceOf(ManagerConflictException.class)
                .hasMessage("Charm operation conflicts with existing data.");
        server.verify();
    }

    private ManagerCharmUpsertRequest request() {
        return new ManagerCharmUpsertRequest(
                "Music Charm",
                "For music lovers",
                "https://example.com/music.png",
                "Interests",
                List.of("music", "song"),
                CharmStatus.ACTIVE,
                CharmStockStatus.IN_STOCK
        );
    }

    private String rowJson(CharmStatus status, CharmStockStatus stockStatus) {
        return """
                [{
                  "id": "11111111-1111-1111-1111-111111111111",
                  "name": "Music Charm",
                  "description": "For music lovers",
                  "image_url": "https://example.com/music.png",
                  "category": "Interests",
                  "tags": "music,song",
                  "status": "%s",
                  "stock_status": "%s"
                }]
                """.formatted(status, stockStatus);
    }
}
