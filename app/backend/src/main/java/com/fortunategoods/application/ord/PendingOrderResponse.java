package com.fortunategoods.application.ord;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fortunategoods.domain.order.Order;
import com.fortunategoods.domain.order.OrderLine;

/*
    This class will be used to send a proper formatted response
    which consists of a list of PendingOrderLineResponse, to the
    frontend
*/
public class PendingOrderResponse {
    private final String orderId;
    private final String status;
    private final String instructions;
    private final List<PendingOrderLineResponse> lines;

    public PendingOrderResponse(Order order) {
        this.orderId = order.getId();
        this.status = order.getStatus().name().toLowerCase(Locale.ROOT);
        this.instructions = order.getSpecialInstructions();

        List<PendingOrderLineResponse> responses = new ArrayList<>();

        for (OrderLine line : order.getLines()) {
            responses.add(new PendingOrderLineResponse(line));
        }

        this.lines = List.copyOf(responses);
    }

    public String getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    public String getInstructions() {
        return instructions;
    }

    public List<PendingOrderLineResponse> getLines() {
        return lines;
    }
}
