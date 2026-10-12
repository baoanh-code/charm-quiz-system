package com.charmquiz.backend.controller;

import com.charmquiz.backend.dto.CharmCatalogItemResponse;
import com.charmquiz.backend.model.CharmStockStatus;
import com.charmquiz.backend.service.SupabaseCharmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CharmController.class)
class CharmControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupabaseCharmService charmService;

    @Test
    void returnsTypedCustomerCatalogWithoutInternalFields() throws Exception {
        when(charmService.getCatalogCharms()).thenReturn(List.of(
                CharmCatalogItemResponse.from(
                        "00000000-0000-0000-0000-000000000001",
                        "Available Charm",
                        null,
                        "https://example.com/available.png",
                        null,
                        CharmStockStatus.IN_STOCK
                ),
                CharmCatalogItemResponse.from(
                        "00000000-0000-0000-0000-000000000002",
                        "Sold Out Charm",
                        "Still visible",
                        "https://example.com/sold-out.png",
                        "seasonal",
                        CharmStockStatus.OUT_OF_STOCK
                )
        ));

        mockMvc.perform(get("/api/charms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$[0].name").value("Available Charm"))
                .andExpect(jsonPath("$[0].description").isEmpty())
                .andExpect(jsonPath("$[0].imageUrl").value("https://example.com/available.png"))
                .andExpect(jsonPath("$[0].category").isEmpty())
                .andExpect(jsonPath("$[0].stockStatus").value("IN_STOCK"))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[1].stockStatus").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$[1].available").value(false))
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].tags").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[0].updatedAt").doesNotExist());
    }
}
