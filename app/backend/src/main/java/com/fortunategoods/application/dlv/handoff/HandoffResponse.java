package com.fortunategoods.application.dlv.handoff;
import java.util.Locale;
import com.fortunategoods.domain.order.OrderStatus;

/*
    Class used to send to frontend after successfull handoff
*/
public class HandoffResponse {
    private final String orderId;
    private final String status;

    public HandoffResponse(String orderId, OrderStatus status) {
        this.orderId = orderId;
        this.status = status.name().toLowerCase(Locale.ROOT);
    }

    public String getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }
}
