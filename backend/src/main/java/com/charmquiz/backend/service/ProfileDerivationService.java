package com.charmquiz.backend.service;

import com.charmquiz.backend.dto.RecommendationRequest;
import com.charmquiz.backend.model.DerivedProfile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class ProfileDerivationService {

    public DerivedProfile derive(RecommendationRequest request) {
        String fullName = request.fullName().trim();
        String givenName = fullName.substring(fullName.lastIndexOf(' ') + 1);
        int firstCodePointEnd = givenName.offsetByCodePoints(0, 1);
        String initial = givenName.substring(0, firstCodePointEnd).toUpperCase(Locale.ROOT);

        List<String> interests = request.interests().stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();

        return new DerivedProfile(
                givenName,
                initial,
                zodiac(request.birthDate()),
                lifePath(request.birthDate()),
                interests,
                request.favoriteAnimal().trim(),
                request.favoriteColor().trim()
        );
    }

    String lifePath(LocalDate birthDate) {
        String digits = birthDate.toString().replace("-", "");
        int sum = digits.chars().map(character -> character - '0').sum();

        while (sum > 9 && sum != 11 && sum != 22 && sum != 33) {
            int reduced = 0;
            while (sum > 0) {
                reduced += sum % 10;
                sum /= 10;
            }
            sum = reduced;
        }

        return Integer.toString(sum);
    }

    String zodiac(LocalDate birthDate) {
        int month = birthDate.getMonthValue();
        int day = birthDate.getDayOfMonth();

        return switch (month) {
            case 1 -> day >= 20 ? "AQUARIUS" : "CAPRICORN";
            case 2 -> day >= 19 ? "PISCES" : "AQUARIUS";
            case 3 -> day >= 21 ? "ARIES" : "PISCES";
            case 4 -> day >= 20 ? "TAURUS" : "ARIES";
            case 5 -> day >= 21 ? "GEMINI" : "TAURUS";
            case 6 -> day >= 21 ? "CANCER" : "GEMINI";
            case 7 -> day >= 23 ? "LEO" : "CANCER";
            case 8 -> day >= 23 ? "VIRGO" : "LEO";
            case 9 -> day >= 23 ? "LIBRA" : "VIRGO";
            case 10 -> day >= 23 ? "SCORPIO" : "LIBRA";
            case 11 -> day >= 22 ? "SAGITTARIUS" : "SCORPIO";
            case 12 -> day >= 22 ? "CAPRICORN" : "SAGITTARIUS";
            default -> throw new IllegalArgumentException("Unsupported birth month");
        };
    }
}
