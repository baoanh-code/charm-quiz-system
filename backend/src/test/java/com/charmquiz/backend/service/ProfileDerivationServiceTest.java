package com.charmquiz.backend.service;

import com.charmquiz.backend.dto.RecommendationRequest;
import com.charmquiz.backend.model.DerivedProfile;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileDerivationServiceTest {

    private final ProfileDerivationService service = new ProfileDerivationService();

    @Test
    void derivesGivenNameInitialZodiacAndLifePath() {
        RecommendationRequest request = new RecommendationRequest(
                "Lâm Phú Bảo Anh",
                LocalDate.of(2006, 7, 24),
                List.of("Music", "Football"),
                "Cat",
                "Red"
        );

        DerivedProfile result = service.derive(request);

        assertThat(result.givenName()).isEqualTo("Anh");
        assertThat(result.nameInitial()).isEqualTo("A");
        assertThat(result.zodiac()).isEqualTo("LEO");
        assertThat(result.lifePath()).isEqualTo("3");
    }

    @Test
    void preservesMasterNumbersDuringLifePathReduction() {
        assertThat(service.lifePath(LocalDate.of(2000, 9, 29))).isEqualTo("22");
    }
}
