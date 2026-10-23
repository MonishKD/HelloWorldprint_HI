package com.fortunategoods.technical.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.restaurant.MenuItemStatus;
import com.fortunategoods.domain.restaurant.menu.MenuItem;

// original_price is a DECIMAL column. Postgres' driver hands it over as a BigDecimal and refuses getObject(..., Double.class)
// ("conversion to class java.lang.Double from numeric not supported"), which made every menu read fail.
// The fake rows below refuse it the same way, so these tests fail if the repositories go back to reading it as a Double.
public class DecimalPriceReadTest {

    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        jdbc = mock(JdbcTemplate.class);
    }

    @Test
    void menuItemReadsItsDiscountAndItsAbsence() throws SQLException {
        MenuItemRepository menuItems = new MenuItemRepository(jdbc);

        ResultSet discounted = itemRow("5", new BigDecimal("15.25"));
        when(jdbc.query(any(String.class), any(RowMapper.class), eq(5))).thenAnswer(call -> List.of(mapped(call.getArgument(1), discounted)));
        assertEquals(15.25, menuItems.findById("5").getOriginalPrice());

        ResultSet regular = itemRow("6", null);
        when(jdbc.query(any(String.class), any(RowMapper.class), eq(6))).thenAnswer(call -> List.of(mapped(call.getArgument(1), regular)));
        assertNull(menuItems.findById("6").getOriginalPrice());
    }

    @Test
    void orderLinesKeepTheirDiscountAndCustomerOrdersComeBack() throws SQLException {
        MenuItemRepository menuItems = mock(MenuItemRepository.class);
        when(menuItems.findById("10")).thenReturn(new MenuItem("10", "1", "Quiche", MenuItemStatus.AVAILABLE, 3.0, 7.0));
        OrderRepository orders = new OrderRepository(jdbc, mock(OrderEventRepository.class), menuItems);

        ResultSet orderRow = mock(ResultSet.class);
        when(orderRow.getString("id")).thenReturn("100");
        when(orderRow.getString("customer_id")).thenReturn("7");
        when(orderRow.getString("restaurant_id")).thenReturn("1");
        when(orderRow.getString("status")).thenReturn("PLACED");
        when(jdbc.query(argThat((String sql) -> sql != null && sql.contains("FROM orders WHERE customer_id")), any(RowMapper.class), eq(7)))
        .thenAnswer(call -> List.of(mapped(call.getArgument(1), orderRow)));

        ResultSet lineRow = mock(ResultSet.class);
        when(lineRow.getString("id")).thenReturn("1");
        when(lineRow.getString("menu_item_id")).thenReturn("10");
        when(lineRow.getInt("quantity")).thenReturn(2);
        when(lineRow.getString("name")).thenReturn("Quiche");
        when(lineRow.getDouble("price")).thenReturn(3.0);
        when(lineRow.getBigDecimal("original_price")).thenReturn(new BigDecimal("7.00"));
        when(lineRow.getObject("original_price", Double.class)).thenThrow(new SQLException("conversion to class java.lang.Double from numeric not supported"));
        when(jdbc.query(argThat((String sql) -> sql != null && sql.contains("FROM order_lines")), any(RowMapper.class), eq(100)))
        .thenAnswer(call -> List.of(mapped(call.getArgument(1), lineRow)));

        List<Order> found = orders.findByCustomer("7");

        assertEquals(1, found.size());
        assertEquals("100", found.get(0).getId());
        assertEquals(7.0, found.get(0).getLines().get(0).getOriginalPrice());
    }

    private ResultSet itemRow(String id, BigDecimal originalPrice) throws SQLException {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("id")).thenReturn(id);
        when(row.getString("restaurant_id")).thenReturn("1");
        when(row.getString("name")).thenReturn("Bowl");
        when(row.getString("status")).thenReturn("AVAILABLE");
        when(row.getDouble("price")).thenReturn(7.5);
        when(row.getBigDecimal("original_price")).thenReturn(originalPrice);
        when(row.getObject("original_price", Double.class)).thenThrow(new SQLException("conversion to class java.lang.Double from numeric not supported"));
        return row;
    }

    private static Object mapped(RowMapper<?> mapper, ResultSet row) throws SQLException {
        return mapper.mapRow(row, 0);
    }
}
