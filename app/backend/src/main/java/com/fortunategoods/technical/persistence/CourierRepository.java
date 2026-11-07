package com.fortunategoods.technical.persistence;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fortunategoods.domain.courier.PotentialCourier;
import com.fortunategoods.domain.courier.Courier;
import com.fortunategoods.domain.courier.CourierStatus;

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
        lockAll();

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

    //searches for a courier by their ID 
    public Courier findById(String courierID){
        List<Courier> found = jdbc.query("""
        SELECT * FROM couriers 
        WHERE user_id = ?
        """, 
        (row, number) -> {
            String id = row.getString("user_id");
            String name = row.getString("name");
            Double latitude = row.getObject("latitude", Double.class);
            Double longitude = row.getObject("longitude", Double.class);
            String checkStatus = row.getString("status");
            CourierStatus status = checkStatus == null ? CourierStatus.OFFLINE : CourierStatus.valueOf(checkStatus);
            return new Courier(id, name, latitude, longitude, status);
        },
        Integer.parseInt(courierID));
        return found.isEmpty() ? null : found.get(0);
    }



    // Transactional thread-lock method that finds all couriers using the courier id
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAll() {
        jdbc.queryForList("SELECT user_id FROM couriers ORDER BY user_id FOR UPDATE", Integer.class);
    }

    // Transactional thread-lock to fetch the status of user with null status to offline and return it
    @Transactional(propagation = Propagation.MANDATORY)
    public CourierStatus lockStatusById(String courierId) {
        List<CourierStatus> found = jdbc.query("SELECT status FROM couriers WHERE user_id = ? FOR UPDATE",
            (row, number) -> row.getString("status") == null ? CourierStatus.OFFLINE: CourierStatus.valueOf(row.getString("status")),
            Integer.parseInt(courierId)
        );

        return found.isEmpty() ? null : found.get(0);
    }

    // Transactional thread-lock method to update status and position of courier
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean updateStatusAndPosition(String courierId, CourierStatus expected, CourierStatus next, Double latitude, Double longitude) {
        String sql = """
        UPDATE couriers
        SET status = ?,
            latitude = COALESCE(?, latitude),
            longitude = COALESCE(?, longitude)
        WHERE user_id = ? AND status = ?
        """; // COALESCE helps to either change it to the latitude/longitude set, or keep their current location if null

        return jdbc.update(sql, next.name(), latitude, longitude, Integer.parseInt(courierId), expected.name()) == 1;
    }
}
