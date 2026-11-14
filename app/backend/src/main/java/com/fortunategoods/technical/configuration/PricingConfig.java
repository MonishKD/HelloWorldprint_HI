package com.fortunategoods.technical.configuration;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import com.fortunategoods.domain.pricing.FareCalculator;
import com.fortunategoods.domain.pricing.PricingRules;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// Reads the pricing block of seed-config.json at startup (#234, PH3 5.4): no fee or threshold is written in the code.
//   1. read the "pricing" block of the same file SeedLoader reads
//   2. a missing or negative value stops the app with the reason
//   3. give Spring one PricingRules and one FareCalculator for the whole app
//
// This runs at every startup, in SEED_MODE keep too: pricing is not stored in the database, it always comes from the file.
//
// The Spring words used in this file:
//   @Configuration   this class gives Spring setup objects. Spring runs its @Bean methods once, at startup.
//   @Bean            Spring keeps the object the method returns and hands it to every class that asks for that type.
//                    LedgerService and PaymentService ask for a FareCalculator in their constructor, so they get this one.
@Configuration
public class PricingConfig {

    // The part of seed-config.json this class reads. Jackson fills it from the file and skips the other keys
    // (accounts, restaurants, ...), which SeedLoader reads.
    // Wrapper type (Double) so a missing value arrives as null and can be reported by name.
    // serviceFeePercent is a Double too, so a decimal like 12.5 reaches the check below instead of being cut to 12.
    record ConfigFile(PricingValues pricing) {}
    record PricingValues(Double deliveryFee, Double smallOrderThreshold, Double smallOrderSurcharge,
            Double serviceFeePercent) {}

    private final JsonMapper jsonReader = JsonMapper.builder().build();

    // @Value fills seedFile from app.seed.file in application.properties, the same file SeedLoader reads.
    @Bean
    public PricingRules pricingRules(@Value("${app.seed.file}") Resource seedFile) throws IOException {
        PricingValues pricing = readPricing(seedFile);

        // The amounts are dollars with 2 decimals in the file (2.99). The PRC code works in cents (299).
        return new PricingRules(
                toCents(pricing.deliveryFee()),
                pricing.serviceFeePercent().intValue(), // readPricing already refused a decimal
                toCents(pricing.smallOrderThreshold()),
                toCents(pricing.smallOrderSurcharge()));
    }

    // The one FareCalculator the app uses. Spring passes in the PricingRules from the method above.
    @Bean
    public FareCalculator fareCalculator(PricingRules pricingRules) {
        return new FareCalculator(pricingRules);
    }

    // Reads and checks the pricing block. Any problem stops startup with a message saying what to fix.
    private PricingValues readPricing(Resource seedFile) throws IOException {
        if (!seedFile.exists()) {
            throw new IllegalStateException("Seed file not found: " + seedFile.getDescription());
        }

        ConfigFile file;
        try {
            file = jsonReader.readValue(seedFile.getInputStream(), ConfigFile.class);
        } catch (JacksonException wrongShape) {
            throw new IllegalStateException("Pricing in the seed file is invalid: " + wrongShape.getOriginalMessage());
        }

        PricingValues pricing = file.pricing();
        if (pricing == null) {
            throw new IllegalStateException("Pricing in the seed file is invalid: the \"pricing\" block is missing");
        }
        checkValue("deliveryFee", pricing.deliveryFee());
        checkValue("smallOrderThreshold", pricing.smallOrderThreshold());
        checkValue("smallOrderSurcharge", pricing.smallOrderSurcharge());
        checkValue("serviceFeePercent", pricing.serviceFeePercent());
        // ServiceFeeRule works with a whole percent, so 12.5 is refused instead of silently charging 12%.
        if (pricing.serviceFeePercent() % 1 != 0) {
            throw new IllegalStateException("Pricing in the seed file is invalid: \"serviceFeePercent\" must be a whole number");
        }
        return pricing;
    }

    // Checks one value: present and not negative.
    private void checkValue(String name, Double value) {
        if (value == null) {
            throw new IllegalStateException("Pricing in the seed file is invalid: \"" + name + "\" is missing");
        }
        if (value.doubleValue() < 0) {
            throw new IllegalStateException("Pricing in the seed file is invalid: \"" + name + "\" is negative");
        }
    }

    // Rounded, the same way FareCalculator turns a menu price into cents, so 2.99 is exactly 299.
    private long toCents(double dollars) {
        return Math.round(dollars * 100);
    }
}
