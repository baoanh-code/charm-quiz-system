package com.charmquiz.backend.service;

import com.charmquiz.backend.dto.CharmImageUploadResponse;
import com.charmquiz.backend.exception.ManagerConflictException;
import com.charmquiz.backend.exception.ManagerPayloadTooLargeException;
import com.charmquiz.backend.exception.ManagerUpstreamException;
import com.charmquiz.backend.exception.ManagerValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

@Service
public class SupabaseCharmStorageService {

    static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final Map<String, ImageType> IMAGE_TYPES = Map.of(
            MediaType.IMAGE_JPEG_VALUE, new ImageType("jpg", SupabaseCharmStorageService::isJpeg),
            MediaType.IMAGE_PNG_VALUE, new ImageType("png", SupabaseCharmStorageService::isPng),
            "image/webp", new ImageType("webp", SupabaseCharmStorageService::isWebp)
    );

    private final RestClient restClient;
    private final String publicStorageBaseUrl;
    private final String bucket;

    @Autowired
    public SupabaseCharmStorageService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.secret-key}") String secretKey,
            @Value("${supabase.storage.charm-images-bucket}") String bucket) {
        this(
                RestClient.builder()
                        .baseUrl(supabaseUrl + "/storage/v1")
                        .defaultHeader("apikey", secretKey)
                        .build(),
                supabaseUrl + "/storage/v1/object/public",
                bucket
        );
    }

    SupabaseCharmStorageService(
            RestClient restClient,
            String publicStorageBaseUrl,
            String bucket) {
        this.restClient = restClient;
        this.publicStorageBaseUrl = publicStorageBaseUrl;
        this.bucket = bucket;
    }

    public CharmImageUploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ManagerValidationException("An image file is required.");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new ManagerPayloadTooLargeException();
        }

        ImageType imageType = IMAGE_TYPES.get(file.getContentType());
        if (imageType == null) {
            throw new ManagerValidationException("Image must be JPEG, PNG, or WebP.");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ManagerValidationException("Image could not be read.");
        }
        if (!imageType.signature().matches(content)) {
            throw new ManagerValidationException("Image content does not match its declared type.");
        }

        String objectPath = "manager/" + UUID.randomUUID() + "." + imageType.extension();
        try {
            restClient.post()
                    .uri("/object/" + bucket + "/" + objectPath)
                    .contentType(MediaType.parseMediaType(file.getContentType()))
                    .header("x-upsert", "false")
                    .body(content)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 409) {
                throw new ManagerConflictException("Image object already exists.", exception);
            }
            throw new ManagerUpstreamException("Supabase Storage upload failed.", exception);
        } catch (RestClientException exception) {
            throw new ManagerUpstreamException("Supabase Storage upload failed.", exception);
        }

        return new CharmImageUploadResponse(
                publicStorageBaseUrl + "/" + bucket + "/" + objectPath
        );
    }

    private static boolean isJpeg(byte[] content) {
        return content.length >= 3
                && unsigned(content[0]) == 0xFF
                && unsigned(content[1]) == 0xD8
                && unsigned(content[2]) == 0xFF;
    }

    private static boolean isPng(byte[] content) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        return content.length >= signature.length
                && Arrays.equals(Arrays.copyOf(content, signature.length), signature);
    }

    private static boolean isWebp(byte[] content) {
        return content.length >= 12
                && asciiEquals(content, 0, "RIFF")
                && asciiEquals(content, 8, "WEBP");
    }

    private static boolean asciiEquals(byte[] content, int offset, String value) {
        for (int index = 0; index < value.length(); index++) {
            if (content[offset + index] != (byte) value.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }

    private record ImageType(String extension, Signature signature) {
    }

    @FunctionalInterface
    private interface Signature {
        boolean matches(byte[] content);
    }
}
