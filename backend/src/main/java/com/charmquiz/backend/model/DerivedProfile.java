package com.charmquiz.backend.model;

import java.util.List;

public record DerivedProfile(
        String givenName,
        String nameInitial,
        String zodiac,
        String lifePath,
        List<String> interests,
        String favoriteAnimal,
        String favoriteColor
) {
}
