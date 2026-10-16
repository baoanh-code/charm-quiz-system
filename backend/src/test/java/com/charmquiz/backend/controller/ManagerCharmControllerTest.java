package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.CharmImageUploadResponse;
import com.charmquiz.backend.dto.ManagerCharmResponse;
import com.charmquiz.backend.exception.ManagerCharmNotFoundException;
import com.charmquiz.backend.exception.ManagerPayloadTooLargeException;
import com.charmquiz.backend.exception.ManagerValidationException;
import com.charmquiz.backend.model.CharmStatus;
import com.charmquiz.backend.model.CharmStockStatus;
import com.charmquiz.backend.service.SupabaseCharmStorageService;
import com.charmquiz.backend.service.SupabaseManagerCharmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ManagerCharmController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
class ManagerCharmControllerTest {

    private static final UUID CHARM_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String MANAGER_ACCESS_TOKEN = "verified-manager-jwt";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupabaseManagerCharmService charmService;

    @MockitoBean
    private SupabaseCharmStorageService storageService;

    @Test
    void listsActiveAndInactiveCharmsUsingManagerContract() throws Exception {
        when(charmService.getAllCharms()).thenReturn(List.of(
                charm(CHARM_ID, CharmStatus.ACTIVE, CharmStockStatus.IN_STOCK),
                charm(UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        CharmStatus.INACTIVE, CharmStockStatus.OUT_OF_STOCK)
        ));

        mockMvc.perform(get("/api/manager/charms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(CHARM_ID.toString()))
                .andExpect(jsonPath("$[0].tags[0]").value("music"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].status").value("INACTIVE"))
                .andExpect(jsonPath("$[1].stockStatus").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[0].updatedAt").doesNotExist());
    }

    @Test
    void getsOneCharm() throws Exception {
        when(charmService.getCharm(CHARM_ID))
                .thenReturn(charm(CHARM_ID, CharmStatus.ACTIVE, CharmStockStatus.IN_STOCK));

        mockMvc.perform(get("/api/manager/charms/{id}", CHARM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Music Charm"));
    }

    @Test
    void createsCharmAndReturnsLocation() throws Exception {
        when(charmService.createCharm(any()))
                .thenReturn(charm(CHARM_ID, CharmStatus.ACTIVE, CharmStockStatus.IN_STOCK));

        mockMvc.perform(post("/api/manager/charms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/manager/charms/" + CHARM_ID))
                .andExpect(jsonPath("$.id").value(CHARM_ID.toString()));
    }

    @Test
    void createsCharmWhenOptionalFieldsAreOmitted() throws Exception {
        when(charmService.createCharm(any()))
                .thenReturn(charm(CHARM_ID, CharmStatus.ACTIVE, CharmStockStatus.IN_STOCK));

        mockMvc.perform(post("/api/manager/charms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Music Charm",
                                  "imageUrl": "https://example.com/music.png",
                                  "status": "ACTIVE",
                                  "stockStatus": "IN_STOCK"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void updatesCharm() throws Exception {
        when(charmService.updateCharm(any(), any()))
                .thenReturn(charm(CHARM_ID, CharmStatus.ACTIVE, CharmStockStatus.IN_STOCK));

        mockMvc.perform(put("/api/manager/charms/{id}", CHARM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CHARM_ID.toString()));
    }

    @Test
    void patchesOnlyVisibilityStatusThroughDedicatedEndpoint() throws Exception {
        when(charmService.updateStatus(CHARM_ID, CharmStatus.INACTIVE))
                .thenReturn(charm(CHARM_ID, CharmStatus.INACTIVE, CharmStockStatus.IN_STOCK));

        mockMvc.perform(patch("/api/manager/charms/{id}/status", CHARM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        verify(charmService).updateStatus(CHARM_ID, CharmStatus.INACTIVE);
    }

    @Test
    void patchesOnlyStockStatusThroughDedicatedEndpoint() throws Exception {
        when(charmService.updateStockStatus(CHARM_ID, CharmStockStatus.OUT_OF_STOCK))
                .thenReturn(charm(CHARM_ID, CharmStatus.ACTIVE, CharmStockStatus.OUT_OF_STOCK));

        mockMvc.perform(patch("/api/manager/charms/{id}/stock-status", CHARM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockStatus\":\"OUT_OF_STOCK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockStatus").value("OUT_OF_STOCK"));
    }

    @Test
    void deletesCharm() throws Exception {
        mockMvc.perform(delete("/api/manager/charms/{id}", CHARM_ID))
                .andExpect(status().isNoContent());

        verify(charmService).deleteCharm(CHARM_ID);
    }

    @Test
    void invalidUuidAndInvalidRequiredBodyReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/manager/charms/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/manager/charms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "imageUrl": "http://example.com/charm.png",
                                  "status": "ACTIVE",
                                  "stockStatus": "IN_STOCK"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(charmService, storageService);
    }

    @Test
    void missingCharmReturnsNotFound() throws Exception {
        when(charmService.getCharm(CHARM_ID)).thenThrow(new ManagerCharmNotFoundException());

        mockMvc.perform(get("/api/manager/charms/{id}", CHARM_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARM_NOT_FOUND"));
    }

    @Test
    void uploadsImageAndMapsValidationErrors() throws Exception {
        MockMultipartFile valid = new MockMultipartFile(
                "file", "charm.png", "image/png", new byte[]{1, 2, 3}
        );
        when(storageService.upload(any(), eq(MANAGER_ACCESS_TOKEN)))
                .thenReturn(new CharmImageUploadResponse(
                        "https://project.supabase.co/charm.png"
                ))
                .thenThrow(new ManagerValidationException("Invalid image."))
                .thenThrow(new ManagerPayloadTooLargeException());

        mockMvc.perform(multipart("/api/manager/charm-images").file(valid)
                        .principal(managerAuthentication()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl")
                        .value("https://project.supabase.co/charm.png"));

        mockMvc.perform(multipart("/api/manager/charm-images").file(valid)
                        .principal(managerAuthentication()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(multipart("/api/manager/charm-images").file(valid)
                        .principal(managerAuthentication()))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));

        verify(storageService, org.mockito.Mockito.times(3))
                .upload(any(), eq(MANAGER_ACCESS_TOKEN));
    }

    private ManagerCharmResponse charm(
            UUID id,
            CharmStatus status,
            CharmStockStatus stockStatus) {
        return new ManagerCharmResponse(
                id.toString(),
                "Music Charm",
                null,
                "https://example.com/music.png",
                null,
                List.of("music", "song"),
                status,
                stockStatus
        );
    }

    private JwtAuthenticationToken managerAuthentication() {
        Jwt jwt = Jwt.withTokenValue(MANAGER_ACCESS_TOKEN)
                .header("alg", "ES256")
                .claim("sub", CHARM_ID.toString())
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    private String validBody() {
        return """
                {
                  "name": "Music Charm",
                  "description": "For music lovers",
                  "imageUrl": "https://example.com/music.png",
                  "category": "Interests",
                  "tags": ["music", "song"],
                  "status": "ACTIVE",
                  "stockStatus": "IN_STOCK"
                }
                """;
    }
}
