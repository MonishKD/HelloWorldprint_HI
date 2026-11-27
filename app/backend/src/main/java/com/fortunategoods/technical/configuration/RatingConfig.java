package com.fortunategoods.technical.configuration;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import com.fortunategoods.domain.rating.RatingScale;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// Reads the ratings block of seed-config.json at startup (Phase IV 5.1), the same way PricingConfig reads pricing.
// A missing or invalid value stops the app with the reason. The star range is never written in the code.
//   @Configuration / @Bean: Spring runs the @Bean method once at startup and gives the returned RatingScale
//   to every class that asks for one in its constructor.
@Configuration
public class RatingConfig {

    // Wrapper type (Integer) so a missing value arrives as null and can be reported by name.
    record ConfigFile(RatingValues ratings) {}
    record RatingValues(Integer minStars, Integer maxStars) {}

    private final JsonMapper jsonReader = JsonMapper.builder().build();

    @Bean
    public RatingScale ratingScale(@Value("${app.seed.file}") Resource seedFile) throws IOException {
        if (!seedFile.exists()) {
            throw new IllegalStateException("Seed file not found: " + seedFile.getDescription());
        }
        ConfigFile file;
        try {
            file = jsonReader.readValue(seedFile.getInputStream(), ConfigFile.class);
        } catch (JacksonException wrongShape) {
            throw new IllegalStateException("Ratings in the seed file are invalid: " + wrongShape.getOriginalMessage());
        }
        RatingValues ratings = file.ratings();
        if (ratings == null || ratings.minStars() == null || ratings.maxStars() == null) {
            throw new IllegalStateException("Ratings in the seed file are invalid: \"minStars\" and \"maxStars\" are required");
        }
        if (ratings.minStars() < 1 || ratings.maxStars() < ratings.minStars()) {
            throw new IllegalStateException("Ratings in the seed file are invalid: need 1 <= minStars <= maxStars");
        }
        return new RatingScale(ratings.minStars(), ratings.maxStars());
    }
}
