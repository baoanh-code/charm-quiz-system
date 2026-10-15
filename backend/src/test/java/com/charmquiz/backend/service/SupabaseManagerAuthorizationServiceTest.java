package com.charmquiz.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseManagerAuthorizationServiceTest {

    private static final UUID USER_ID = UUID.fromString("12345678-1234-1234-1234-123456789012");
    private static final String SECRET = "test-secret-value";

    @Test
    void exactManagerLookupReturnsTrueForMatchingRowWithoutLeakingSecret() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://project.supabase.co/rest/v1")
                .defaultHeader("apikey", SECRET);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SupabaseManagerAuthorizationService service = new SupabaseManagerAuthorizationService(
                builder.build(),
                new ObjectMapper()
        );

        server.expect(request -> {
                    assertThat(request.getMethod().name()).isEqualTo("GET");
                    assertThat(request.getURI().getPath()).isEqualTo("/rest/v1/manager");
                    assertThat(request.getURI().getQuery())
                            .isEqualTo("id=eq." + USER_ID + "&select=id&limit=1")
                            .doesNotContain(SECRET);
                    assertThat(request.getHeaders().getFirst("apikey")).isEqualTo(SECRET);
                    assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
                })
                .andExpect(content().string(""))
                .andRespond(withSuccess(
                        "[{\"id\":\"" + USER_ID + "\"}]",
                        MediaType.APPLICATION_JSON
                ));

        assertThat(service.isManager(USER_ID)).isTrue();
        server.verify();
    }

    @Test
    void emptyManagerLookupReturnsFalse() {
        TestFixture fixture = fixture();
        fixture.server.expect(request -> assertThat(request.getURI().getQuery())
                        .isEqualTo("id=eq." + USER_ID + "&select=id&limit=1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(fixture.service.isManager(USER_ID)).isFalse();
        fixture.server.verify();
    }

    @Test
    void malformedManagerResponseIsRejected() {
        TestFixture fixture = fixture();
        fixture.server.expect(request -> {
                })
                .andRespond(withSuccess("{\"id\":\"not-an-array\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.service.isManager(USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("manager membership");
        fixture.server.verify();
    }

    @Test
    void mismatchedManagerIdIsRejected() {
        TestFixture fixture = fixture();
        fixture.server.expect(request -> {
                })
                .andRespond(withSuccess(
                        "[{\"id\":\"00000000-0000-0000-0000-000000000001\"}]",
                        MediaType.APPLICATION_JSON
                ));

        assertThatThrownBy(() -> fixture.service.isManager(USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid manager membership response");
        fixture.server.verify();
    }

    @Test
    void supabaseClientErrorFailsClosed() {
        TestFixture fixture = fixture();
        fixture.server.expect(request -> {
                })
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> fixture.service.isManager(USER_ID)).isInstanceOf(RuntimeException.class);
        fixture.server.verify();
    }

    @Test
    void supabaseServerErrorFailsClosed() {
        TestFixture fixture = fixture();
        fixture.server.expect(request -> {
                })
                .andRespond(withServerError());

        assertThatThrownBy(() -> fixture.service.isManager(USER_ID)).isInstanceOf(RuntimeException.class);
        fixture.server.verify();
    }

    private TestFixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://project.supabase.co/rest/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new TestFixture(
                server,
                new SupabaseManagerAuthorizationService(builder.build(), new ObjectMapper())
        );
    }

    private record TestFixture(
            MockRestServiceServer server,
            SupabaseManagerAuthorizationService service) {
    }
}
