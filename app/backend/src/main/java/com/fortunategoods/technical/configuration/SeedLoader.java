package com.fortunategoods.technical.configuration;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.user.User;
import com.fortunategoods.domain.user.UserRole;
import com.fortunategoods.technical.persistence.MenuItemRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

// Fills the database from seed-config.json at startup (#25 accounts, #26 restaurants and menus, R-REG-03).
//   1. read and check the file: a missing or invalid one stops the app with the reason
//   2. SEED_MODE keep (default): if the database already has accounts, change nothing
//      SEED_MODE reset: wipe every table first
//   3. save the accounts (passwords hashed like registration), the restaurants, their menu items and owner links
//   4. confirm in the console
//
// ApplicationRunner: Spring calls run() once the app has started, so the tables already exist.
// @Transactional: everything run() saves is kept together or not at all, so a failed reset puts the old data back.
@Component
public class SeedLoader implements ApplicationRunner {

    // The shape of seed-config.json. Jackson fills these from the file.
    record SeedFile(List<SeedAccount> accounts, List<SeedRestaurant> restaurants) {}
    record SeedAccount(String username, String email, String password, UserRole role) {}
    record SeedRestaurant(String name, String owner, RestaurantStatus status, String cuisine, String address,
            double latitude, double longitude, List<SeedMenuItem> menu) {}
    record SeedMenuItem(String id, String name, String description, double price, Double originalPrice,
            MenuItemStatus status) {}

    // Empties every table and restarts the ids at 1, so the seeded rows always get the same ids.
    private static final String WIPE_ALL_TABLES = "TRUNCATE TABLE command_log, assignments, ledger_entries, "
            + "order_events, order_lines, orders, restaurant_personnel, operators, couriers, customers, users, "
            + "menu_items, menus, restaurants RESTART IDENTITY CASCADE";

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

    // Jackson comes with Spring's web starter. It refuses a file that is not JSON, has a missing field,
    // the wrong type, or a role or status that doesn't exist.
    private final JsonMapper jsonReader = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .build();

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final PasswordEncoder passwordEncoder;
    private final Resource seedFile;
    private final String seedMode;

    // passwordEncoder is the BCrypt one from SecurityConfig, the same one registration uses.
    // @Value fills seedFile and seedMode from app.seed.file and app.seed.mode in application.properties.
    public SeedLoader(JdbcTemplate jdbc, UserRepository userRepository, RestaurantRepository restaurantRepository,
            MenuItemRepository menuItemRepository, PasswordEncoder passwordEncoder,
            @Value("${app.seed.file}") Resource seedFile, @Value("${app.seed.mode}") String seedMode) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedFile = seedFile;
        this.seedMode = seedMode;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments startupArguments) throws IOException {
        SeedFile seed = readSeedFile();

        if (seedMode.equals("keep")) {
            Integer savedAccounts = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            if (savedAccounts > 0) {
                log.info("Seed file is valid. The database already has data, so it was kept (SEED_MODE=keep).");
                return;
            }
        } else if (seedMode.equals("reset")) {
            jdbc.execute(WIPE_ALL_TABLES);
        } else {
            throw new IllegalStateException("SEED_MODE must be keep or reset, not \"" + seedMode + "\"");
        }

        // The new account ids by username
        Map<String, String> accountIds = new HashMap<>();
        for (SeedAccount account : seed.accounts()) {
            String passwordHash = passwordEncoder.encode(account.password());
            User newAccount = new User(null, account.username(), account.email(), null, passwordHash, account.role());
            accountIds.put(account.username(), userRepository.add(newAccount));
        }

        // Saves each restaurant, links it to its owner account, then saves its menu items.
        int menuItemCount = 0;
        for (SeedRestaurant restaurant : seed.restaurants()) {
            Restaurant newRestaurant = new Restaurant(null, restaurant.name(), restaurant.status(),
                    restaurant.latitude(), restaurant.longitude(), List.of());
            String restaurantId = restaurantRepository.add(newRestaurant, restaurant.cuisine(), restaurant.address());
            // The same owner link restaurant sign-up makes (#143).
            userRepository.addRestaurantPersonnel(accountIds.get(restaurant.owner()), restaurantId, true);

            for (SeedMenuItem item : restaurant.menu()) {
                // The id in the file is only a label. The database gives the item its real id.
                MenuItem newItem = new MenuItem(item.id(), restaurantId, item.name(), item.status(), item.price(),
                        item.originalPrice());
                menuItemRepository.add(restaurantId, newItem, item.description(), item.originalPrice());
                menuItemCount++;
            }
        }

        log.info("Seed loader: {} accounts, {} restaurants and {} menu items loaded (SEED_MODE={}). Environment ready.",
                seed.accounts().size(), seed.restaurants().size(), menuItemCount, seedMode);
    }

    // Reads and checks the file. Not private, so SeedLoaderTest can check the committed file without a database.
    SeedFile readSeedFile() throws IOException {
        if (!seedFile.exists()) {
            throw new IllegalStateException("Seed file not found: " + seedFile.getDescription());
        }

        SeedFile seed;
        try {
            seed = jsonReader.readValue(seedFile.getInputStream(), SeedFile.class);
        } catch (JacksonException wrongShape) {
            throw new IllegalStateException("Seed file is invalid: " + wrongShape.getOriginalMessage());
        }

        // R-REG-03: at least one account per role. The Operator can only come from here.
        Set<UserRole> roles = new HashSet<>();
        for (SeedAccount account : seed.accounts()) {
            roles.add(account.role());
        }
        if (!roles.containsAll(List.of(UserRole.values()))) {
            throw new IllegalStateException("Seed file is invalid: it needs at least one account per role");
        }
        return seed;
    }
}
