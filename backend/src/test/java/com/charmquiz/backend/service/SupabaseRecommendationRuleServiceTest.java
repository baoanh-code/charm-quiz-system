package com.charmquiz.backend.service;

import com.charmquiz.backend.model.DerivedProfile;
import com.charmquiz.backend.model.RecommendationSectionType;
import com.charmquiz.backend.model.RuleRecommendation;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseRecommendationRuleServiceTest {

    @Test
    void callsGroupedRpcAndParsesMultipleOrderedRows() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://project.supabase.co/rest/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SupabaseRecommendationRuleService service = new SupabaseRecommendationRuleService(
                builder.build(),
                new ObjectMapper()
        );
        server.expect(requestTo(containsString("/rpc/get_grouped_rule_recommendations")))
                .andExpect(content().string(containsString("p_name_initial")))
                .andExpect(content().string(containsString("music")))
                .andRespond(withSuccess(
                        """
                        [
                          {
                            "section": "INTERESTS",
                            "matched_field": "INTEREST",
                            "matched_value": "music",
                            "priority": 20,
                            "charm_id": "charm-high"
                          },
                          {
                            "section": "INTERESTS",
                            "matched_field": "INTEREST",
                            "matched_value": "music",
                            "priority": 10,
                            "charm_id": "charm-low"
                          }
                        ]
                        """,
                        MediaType.APPLICATION_JSON
                ));

        List<RuleRecommendation> result = service.getMatches(new DerivedProfile(
                "Anh",
                "A",
                "LEO",
                "3",
                List.of("music"),
                "cat",
                "red"
        ));

        assertThat(result).extracting(RuleRecommendation::section)
                .containsOnly(RecommendationSectionType.INTERESTS);
        assertThat(result).extracting(RuleRecommendation::priority)
                .containsExactly(20, 10);
        server.verify();
    }
}
