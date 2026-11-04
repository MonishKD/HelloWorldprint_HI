package com.fortunategoods.domain.courier;

import java.util.List;

/*
    Class for courier-order assignment rules
*/
public class NearestCourierRule {
    private NearestCourierRule() {}

    public static PotentialCourier select(List<PotentialCourier> couriers, double latitude, double longitude){
        PotentialCourier nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY; // initialize with +infty
        boolean missingLocation = (latitude == 0) && (longitude == 0); // handles missing location

        for (PotentialCourier courier: couriers){
            // if there's no longitude/latitude for restaurant, all couriers distance = 0, lowest id will get the order
            double distance = missingLocation ? 0: Math.hypot(courier.getLatitude() - latitude, courier.getLongitude() - longitude);
            
            // if there's no nearest, or the distance of that courier < nearest distance -> set new nearest courier and newest distance
            // Another case: if both distances are the same, check if id of current courier < the one of the nearest, if true --> set new nearest courier and newest distance
            if (nearest == null || distance < nearestDistance || (Double.compare(distance, nearestDistance)==0 
            && Long.parseLong(courier.getId()) < Long.parseLong(nearest.getId()))){
                nearest = courier;
                nearestDistance = distance;
            }
        }

        return nearest;        
    }
}
