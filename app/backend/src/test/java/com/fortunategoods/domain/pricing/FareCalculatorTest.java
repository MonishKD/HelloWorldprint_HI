package com.fortunategoods.domain.pricing;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.fortunategoods.domain.order.OrderLine;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;
import com.fortunategoods.domain.pricing.decorator.*;



class FareCalculatorTest {

    // delivery 3.50, service 10%, surcharge 2.00 when subtotal < 30.00
    private final FareCalculator calculator = new FareCalculator(350, 10, 3000, 200);

    private static OrderLine line(String id, double price, int qty) {
        MenuItem item = new MenuItem(id, "r1", "Item " + id, MenuItemStatus.AVAILABLE, price);
        return OrderLine.fromMenuItem(item, qty);
    }

    @Test
    void smallOrderGetsSurcharge() {
        // 12.50 x2 + 4.99 = 29.99, just under 30.00
        Fare fare = calculator.calculate(List.of(line("a", 12.50, 2), line("b", 4.99, 1)));
        Fare testFare = new Fare(2999, 350, 300, 200);
        assertEquals(testFare.getSutchargeCents(), fare.getSutchargeCents());
    }

    @Test
    void orderAtThresholdHasNoSurcharge() {
        Fare fare = calculator.calculate(List.of(line("a", 30.00, 1)));
        Fare testFare = new Fare(3000, 350, 300, 0);
        assertEquals(testFare.getSutchargeCents(), fare.getSutchargeCents());
    }

    @Test
    void sameLinesGiveSameFare() {
        List<OrderLine> lines = List.of(line("a", 8.25, 2), line("b", 3.10, 4));
        assertEquals(calculator.calculate(lines), calculator.calculate(lines));
    }

    @Test
    void eachRuleWorksOnItsOwn() {
        assertEquals(350, new DeliveryFeeRule(new BaseFare(), 350).compute(1000));
        assertEquals(300, new ServiceFeeRule(new BaseFare(),10).compute(2999));
        assertEquals(200, new SmallOrderSurchargeRule(new BaseFare(), 3000, 200).compute(2999));
        assertEquals(0, new SmallOrderSurchargeRule(new BaseFare(),3000, 200).compute(3000));
    }
}