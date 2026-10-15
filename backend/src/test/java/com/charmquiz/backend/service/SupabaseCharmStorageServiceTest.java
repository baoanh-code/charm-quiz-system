package com.charmquiz.backend.service;

import com.charmquiz.backend.dto.CharmImageUploadResponse;
import com.charmquiz.backend.exception.ManagerPayloadTooLargeException;
import com.charmquiz.backend.exception.ManagerUpstreamException;
import com.charmquiz.backend.exception.ManagerValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseCharmStorageServiceTest {

    private static final String SECRET = "test-secret-value";

    @ParameterizedTest
    @MethodSource("validImages")
    void uploadsValidatedImageWithGeneratedPathAndReturnsPublicUrl(
            String contentType,
            String extension,
            byte[] bytes) {
        Fixture fixture = fixture();
        fixture.server.expect(request -> {
                    assertThat(request.getMethod().name()).isEqualTo("POST");
                    assertThat(request.getURI().getPath())
                            .matches("/storage/v1/object/charm-images/manager/"
                                    + "[0-9a-f-]{36}\\." + extension);
                    assertThat(request.getURI().toString()).doesNotContain(SECRET);
                    assertThat(request.getHeaders().getFirst("apikey")).isEqualTo(SECRET);
                    assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
                    assertThat(request.getHeaders().getFirst("x-upsert")).isEqualTo("false");
                    assertThat(request.getHeaders().getContentType())
                            .isEqualTo(MediaType.parseMediaType(contentType));
                })
                .andExpect(content().bytes(bytes))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        CharmImageUploadResponse result = fixture.service.upload(new MockMultipartFile(
                "file", "user-controlled-name.anything", contentType, bytes
        ));

        assertThat(result.imageUrl())
                .matches("https://project.supabase.co/storage/v1/object/public/"
                        + "charm-images/manager/[0-9a-f-]{36}\\." + extension);
        fixture.server.verify();
    }

    @Test
    void rejectsSpoofedMimeTypeBeforeCallingStorage() {
        Fixture fixture = fixture();
        MockMultipartFile spoofed = new MockMultipartFile(
                "file", "fake.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3, 4}
        );

        assertThatThrownBy(() -> fixture.service.upload(spoofed))
                .isInstanceOf(ManagerValidationException.class)
                .hasMessageContaining("does not match");
        fixture.server.verify();
    }

    @Test
    void rejectsUnsupportedDeclaredContentType() {
        Fixture fixture = fixture();
        MockMultipartFile gif = new MockMultipartFile(
                "file", "image.gif", MediaType.IMAGE_GIF_VALUE, new byte[]{'G', 'I', 'F'}
        );

        assertThatThrownBy(() -> fixture.service.upload(gif))
                .isInstanceOf(ManagerValidationException.class)
                .hasMessageContaining("JPEG, PNG, or WebP");
        fixture.server.verify();
    }

    @Test
    void rejectsOversizedFileBeforeCallingStorage() {
        Fixture fixture = fixture();
        byte[] oversized = new byte[(int) SupabaseCharmStorageService.MAX_IMAGE_SIZE + 1];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;

        assertThatThrownBy(() -> fixture.service.upload(new MockMultipartFile(
                "file", "large.jpg", MediaType.IMAGE_JPEG_VALUE, oversized
        )))
                .isInstanceOf(ManagerPayloadTooLargeException.class);
        fixture.server.verify();
    }

    @Test
    void mapsStorageFailureToUpstreamError() {
        Fixture fixture = fixture();
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        fixture.server.expect(request -> {
                }).andRespond(withServerError());

        assertThatThrownBy(() -> fixture.service.upload(new MockMultipartFile(
                "file", "image.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg
        )))
                .isInstanceOf(ManagerUpstreamException.class)
                .hasMessage("Supabase Storage upload failed.");
        fixture.server.verify();
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://project.supabase.co/storage/v1")
                .defaultHeader("apikey", SECRET);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(
                server,
                new SupabaseCharmStorageService(
                        builder.build(),
                        "https://project.supabase.co/storage/v1/object/public",
                        "charm-images"
                )
        );
    }

    private static Stream<Arguments> validImages() {
        return Stream.of(
                Arguments.of(
                        MediaType.IMAGE_JPEG_VALUE,
                        "jpg",
                        new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
                ),
                Arguments.of(
                        MediaType.IMAGE_PNG_VALUE,
                        "png",
                        new byte[]{
                                (byte) 0x89, 0x50, 0x4E, 0x47,
                                0x0D, 0x0A, 0x1A, 0x0A, 0x00
                        }
                ),
                Arguments.of(
                        "image/webp",
                        "webp",
                        new byte[]{
                                'R', 'I', 'F', 'F', 0, 0, 0, 0,
                                'W', 'E', 'B', 'P'
                        }
                )
        );
    }

    private record Fixture(
            MockRestServiceServer server,
            SupabaseCharmStorageService service) {
    }
}
