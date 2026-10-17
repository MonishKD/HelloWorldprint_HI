package com.fortunategoods.technical.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

// The team assumes the committed seed-config.json is valid (#12), so this test fails if it stops loading.
// No database is needed: it only reads and checks the file.
class SeedLoaderTest {

    @Test
    void committedSeedFileIsValid() throws Exception {
        SeedLoader seedLoader = new SeedLoader(null, null, null, null, null,
                new ClassPathResource("seed-config.json"), "keep");

        // Throws if the file is missing or invalid, which fails the test.
        seedLoader.readSeedFile();
    }
}
