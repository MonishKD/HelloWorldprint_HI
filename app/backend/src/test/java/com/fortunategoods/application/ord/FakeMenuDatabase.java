package com.fortunategoods.application.ord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fortunategoods.application.ord.menu.MenuService;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.Restaurant;
import com.fortunategoods.domain.restaurant.RestaurantStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.technical.persistence.MenuItemRepository;
import com.fortunategoods.technical.persistence.RestaurantRepository;
import com.fortunategoods.technical.persistence.UserRepository;

/*
    An in-memory stand-in for the menu tables, shared by the menu editing tests. No database.
    Starts with:
      restaurant "1" Cafe,   item "10" Wrap  (staff user 8 works here)
      restaurant "2" Deli,   item "30" Soup  (staff user 9 works here)
    It behaves like the real MenuItemRepository: removing only hides an item, new items get the next id (20, 21...).
*/
public class FakeMenuDatabase {

    public final Map<String, MenuItem> items = new LinkedHashMap<>(); // every item, hidden ones too
    public final Set<String> hidden = new HashSet<>();
    public final Map<String, String> descriptions = new HashMap<>();
    private int nextId = 20;

    public FakeMenuDatabase() {
        reset();
    }

    public void reset() {
        items.clear();
        hidden.clear();
        descriptions.clear();
        nextId = 20;
        items.put("10", new MenuItem("10", "1", "Wrap", MenuItemStatus.AVAILABLE, 4.50, 9.00));
        items.put("30", new MenuItem("30", "2", "Soup", MenuItemStatus.AVAILABLE, 3.00, 6.00));
    }

    public MenuService menuService() {
        return new MenuService(restaurants(), users(), menuItems());
    }

    public MenuItemRepository menuItems() {
        return new MenuItemRepository(null) {
            @Override
            public List<MenuItem> findByRestaurantId(String restaurantId) {
                List<MenuItem> onMenu = new ArrayList<>();
                for (MenuItem item : items.values()) {
                    if (item.getRestaurantId().equals(restaurantId) && !hidden.contains(item.getId())) {
                        onMenu.add(item);
                    }
                }
                return onMenu;
            }

            @Override
            public MenuItem findById(String id) {
                return items.get(id);
            }

            @Override
            public String add(String restaurantId, MenuItem item, String description, Double originalPrice) {
                String id = String.valueOf(nextId++);
                items.put(id, new MenuItem(id, restaurantId, item.getName(), item.getStatus(), item.getPrice(), originalPrice));
                descriptions.put(id, description);
                return id;
            }

            @Override
            public void update(MenuItem item, String description, Double originalPrice) {
                items.put(item.getId(), item);
                descriptions.put(item.getId(), description);
            }

            @Override
            public void hide(String id) {
                hidden.add(id);
            }
        };
    }

    public RestaurantRepository restaurants() {
        MenuItemRepository menuItems = menuItems();
        return new RestaurantRepository(null, null) {
            @Override
            public Restaurant findById(String id) {
                if (id.equals("1")) {
                    return new Restaurant("1", "Cafe", RestaurantStatus.OPEN, 45.5, -73.6, menuItems.findByRestaurantId("1"));
                }
                if (id.equals("2")) {
                    return new Restaurant("2", "Deli", RestaurantStatus.OPEN, 45.4, -73.5, menuItems.findByRestaurantId("2"));
                }
                return null;
            }
        };
    }

    public UserRepository users() {
        return new UserRepository(null) {
            @Override
            public String findRestaurantIdByUserId(String userId) {
                if (userId.equals("8")) {
                    return "1";
                }
                if (userId.equals("9")) {
                    return "2";
                }
                return null;
            }
        };
    }
}
