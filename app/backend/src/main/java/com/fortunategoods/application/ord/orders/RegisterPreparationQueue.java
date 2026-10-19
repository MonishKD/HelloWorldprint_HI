package com.fortunategoods.application.ord.orders;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import java.util.Map;
import com.fortunategoods.domain.order.PreparationQueue;

@Component 
public class RegisterPreparationQueue {
    private final Map<String, PreparationQueue> queues = new ConcurrentHashMap<>();

    public PreparationQueue forRestaurant(String restaurantId){
        if (restaurantId == null || restaurantId.trim().isEmpty()){
            throw new IllegalArgumentException("Restaurant ID given to PreparationQueue registry is null/empty!");
        }
    return queues.computeIfAbsent(restaurantId, id -> new PreparationQueue(id));
    }
}
