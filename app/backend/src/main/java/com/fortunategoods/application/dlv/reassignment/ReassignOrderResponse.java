package com.fortunategoods.application.dlv.reassignment;

/*
    Class used to send response to frontend
*/
public class ReassignOrderResponse {
    private final String orderId;
    private final String offeredCourierId;

    public ReassignOrderResponse(String orderId, String offeredCourierId) {
        this.orderId = orderId;
        this.offeredCourierId = offeredCourierId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getOfferedCourierId() {
        return offeredCourierId;
    }

    public String getDispatchStatus() {
        return offeredCourierId == null ? "waiting" : "offered";
    }
}
