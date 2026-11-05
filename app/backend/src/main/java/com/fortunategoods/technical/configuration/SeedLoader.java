package com.fortunategoods.technical.configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
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

import com.fortunategoods.domain.courier.CourierStatus;
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

// Fills the database from seed-config.json at startup (#25 accounts, #26 restaurants and menus, R-REG-03,
// #233 customer locations, couriers and shelters).
//   1. read and check the file: a missing or invalid one stops the app with the reason
//   2. SEED_MODE keep (default): if the database already has accounts, change nothing
//      SEED_MODE reset: wipe every table first
//   3. save the accounts (passwords hashed like registration), the restaurants, their menu items and owner links,
//      the customers' saved addresses, the couriers' positions and the shelters
//   4. confirm in the console
//
// The seeded couriers and the dispatch rule (NearestCourierRule: straight-line distance on lat/long, lowest id on ties):
//   carl (id 3) waits at Guy / De Maisonneuve, next to Poulet Rouge, so a Poulet Rouge order goes to carl.
//   dana (id 17) at Hôtel-de-Ville / Sherbrooke and eli (id 18) at Berri / Sainte-Catherine are the exact same
//   distance from Uniburger (same latitude, longitudes mirrored around it), so a Uniburger order goes to dana, the lower id.
//   fay (id 19) waits at Saint-Laurent / Sherbrooke, farther from both.
// The ids come from the order of the accounts in the file, so new accounts go at the end of that list.
//
// ApplicationRunner: Spring calls run() once the app has started, so the tables already exist.
// @Transactional: everything run() saves is kept together or not at all, so a failed reset puts the old data back.
@Component
public class SeedLoader implements ApplicationRunner {

    // The shape of seed-config.json. Jackson fills these from the file.
    record SeedFile(List<SeedAccount> accounts, List<SeedRestaurant> restaurants, List<SeedCustomer> customers,
            List<SeedCourier> couriers, List<SeedShelter> shelters) {}
    record SeedAccount(String username, String email, String password, UserRole role) {}
    record SeedRestaurant(String name, String owner, RestaurantStatus status, String cuisine, String address,
            double latitude, double longitude, List<SeedMenuItem> menu) {}
    record SeedMenuItem(String id, String name, String description, double price, Double originalPrice,
            MenuItemStatus status) {}
    // account is the username of a CUSTOMER or COURIER account above, the way a restaurant points to its owner.
    record SeedCustomer(String account, String name, String address, double latitude, double longitude) {}
    // Double (not double) for the coordinates, so a null in the file stays null and the check below refuses it.
    record SeedCourier(String account, String name, Double latitude, Double longitude, CourierStatus status) {}
    record SeedShelter(String name, String address, Double latitude, Double longitude) {}

    // Empties every table and restarts the ids at 1, so the seeded rows always get the same ids.
    private static final String WIPE_ALL_TABLES = "TRUNCATE TABLE command_log, assignments, ledger_entries, "
            + "order_events, order_lines, orders, restaurant_personnel, operators, couriers, customers, users, "
            + "menu_items, menus, restaurants, shelters RESTART IDENTITY CASCADE";

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

        int menuItemCount = saveSeed(seed);

        log.info("Seed loader: {} accounts, {} restaurants, {} menu items, {} customer locations, {} couriers and "
                + "{} shelters loaded (SEED_MODE={}). Environment ready.", seed.accounts().size(),
                seed.restaurants().size(), menuItemCount, seed.customers().size(), seed.couriers().size(),
                seed.shelters().size(), seedMode);
    }

    // The operator's Reset (#276): wipes every table and seeds again from the file, while the app keeps running.
    // ResetCommand calls this. It is the same as starting the app with SEED_MODE=reset.
    // Stripe is not called: the test payments stay in Stripe, only our copy of their ids (in orders) is wiped.
    // @Transactional: Spring runs it as one transaction, so if anything fails the old data is put back.
    @Transactional
    public void resetToSeed() {
        SeedFile seed;
        try {
            seed = readSeedFile();
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Seed file could not be read", unreadable);
        }
        jdbc.execute(WIPE_ALL_TABLES);
        saveSeed(seed);
    }

    // Saves everything in the file into empty tables, and returns how many menu items it saved.
    private int saveSeed(SeedFile seed) {
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

        // userRepository.add() already made an empty customers / couriers row for each of these accounts, this fills it in.
        for (SeedCustomer customer : seed.customers()) {
            userRepository.saveCustomerLocation(accountIds.get(customer.account()), customer.name(), customer.address(),
                    customer.latitude(), customer.longitude());
        }
        for (SeedCourier courier : seed.couriers()) {
            userRepository.saveCourierProfile(accountIds.get(courier.account()), courier.name(), courier.latitude(),
                    courier.longitude(), courier.status());
        }

        // Shelters are only written here for now. Reading them for checkout is #241.
        for (SeedShelter shelter : seed.shelters()) {
            jdbc.update("INSERT INTO shelters (name, address, latitude, longitude) VALUES (?, ?, ?, ?)",
                    shelter.name(), shelter.address(), shelter.latitude(), shelter.longitude());
        }
        return menuItemCount;
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

        // #233: each customer and courier entry must point to an account of that role,
        // every courier needs coordinates and a status, and every shelter needs coordinates.
        Map<String, UserRole> roleByUsername = new HashMap<>();
        for (SeedAccount account : seed.accounts()) {
            roleByUsername.put(account.username(), account.role());
        }
        for (SeedCustomer customer : seed.customers()) {
            if (roleByUsername.get(customer.account()) != UserRole.CUSTOMER) {
                throw new IllegalStateException("Seed file is invalid: customer \"" + customer.account()
                        + "\" is not a CUSTOMER account");
            }
        }
        for (SeedCourier courier : seed.couriers()) {
            if (roleByUsername.get(courier.account()) != UserRole.COURIER) {
                throw new IllegalStateException("Seed file is invalid: courier \"" + courier.account()
                        + "\" is not a COURIER account");
            }
            if (courier.latitude() == null || courier.longitude() == null) {
                throw new IllegalStateException("Seed file is invalid: courier \"" + courier.account()
                        + "\" has no coordinates");
            }
            if (courier.status() == null) {
                throw new IllegalStateException("Seed file is invalid: courier \"" + courier.account()
                        + "\" has no status");
            }
        }
        for (SeedShelter shelter : seed.shelters()) {
            if (shelter.latitude() == null || shelter.longitude() == null) {
                throw new IllegalStateException("Seed file is invalid: shelter \"" + shelter.name()
                        + "\" has no coordinates");
            }
        }
        return seed;
    }
}
