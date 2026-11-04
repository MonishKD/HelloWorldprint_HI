package com.fortunategoods.technical.persistence;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fortunategoods.domain.courier.PotentialCourier;

/*
    Repository which will hold all couriers
*/
@Repository
public class CourierRepository {
    private final JdbcTemplate jdbc;

    public CourierRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<PotentialCourier> findIdleWithoutOffer() {
        jdbc.queryForList("SELECT user_id FROM couriers ORDER BY user_id FOR UPDATE", Integer.class);

        // Query all idle couriers who hasn't been assigned an order
        String sql = """
            SELECT c.user_id, c.latitude, c.longitude
            FROM couriers c
            WHERE c.status = 'IDLE'
            AND NOT EXISTS (
                SELECT 1 FROM assignments a
                WHERE a.courier_id = c.user_id
                AND a.status IN ('OFFERED', 'ACCEPTED')
                AND a.is_delivered = FALSE
            )
            ORDER BY c.user_id
            """;

        return jdbc.query(sql, (row, number) -> {
            Double latitude = row.getObject("latitude", Double.class);
            Double longitude = row.getObject("longitude", Double.class);

            // Courier must have coordinates
            if (latitude == null || longitude == null) {
                throw new IllegalStateException("Idle courier has no position: " + row.getString("user_id"));
            }

            return new PotentialCourier(row.getString("user_id"), latitude, longitude);
        });
    }
}
