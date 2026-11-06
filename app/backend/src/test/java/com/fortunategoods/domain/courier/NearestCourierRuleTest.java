package com.fortunategoods.domain.courier;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NearestCourierRuleTest {
    @Test
    void selectsNearestCourier() {
        PotentialCourier far = new PotentialCourier("2", 8, 10);
        PotentialCourier near = new PotentialCourier("15", 11, 10);

        PotentialCourier selected = NearestCourierRule.select(List.of(far, near), 10, 10);

        assertEquals("15", selected.getId());
    }

    @Test
    void equalDistancesUseLowestNumericId() {
        PotentialCourier ten = new PotentialCourier("10", 9, 10);
        PotentialCourier two = new PotentialCourier("2", 11, 10);

        PotentialCourier selected = NearestCourierRule.select(List.of(ten, two), 10, 10);

        assertEquals("2", selected.getId());
    }

    @Test
    void noCouriersReturnsNone() {
        assertNull(NearestCourierRule.select(List.of(), 10, 10));
    }

    @Test
    void missingRestaurantLocationUsesLowestId() {
        PotentialCourier near = new PotentialCourier("10", 1, 1);
        PotentialCourier far = new PotentialCourier("2", 50, 50);

        PotentialCourier selected = NearestCourierRule.select(List.of(near, far), 0, 0);

        assertEquals("2", selected.getId());
    }
}
