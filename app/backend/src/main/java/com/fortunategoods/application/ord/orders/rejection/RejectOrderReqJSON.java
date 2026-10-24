package com.fortunategoods.application.ord.orders.rejection;

/*
    This class will be used to get request body from frontend,
    which will contain the rejectReason
*/
public class RejectOrderReqJSON {
    private String reason;
    public RejectOrderReqJSON(String reason){
        this.reason = reason;
    }

    public String getReason(){
        return reason;
    }

    public void setReason(String reason){
        this.reason = reason;
    }

}
