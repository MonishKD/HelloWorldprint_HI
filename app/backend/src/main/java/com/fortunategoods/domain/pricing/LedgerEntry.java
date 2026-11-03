package com.fortunategoods.domain.pricing;

public class LedgerEntry {
    //the 2 options for entry
    public enum Type { CHARGE, NO_CHARGE }
    private final String orderId;
    private final Type type;
    private final long amountCents;

    public LedgerEntry(String orderId, Type type, long amountCents) {
        this.orderId = orderId;
        this.type = type;
        this.amountCents = amountCents;
    }

    public static LedgerEntry charge(String orderId, long amountCents) {
        return new LedgerEntry(orderId, Type.CHARGE, amountCents);
    }
    //0 so no charge
    public static LedgerEntry noCharge(String orderId) {
        return new LedgerEntry(orderId, Type.NO_CHARGE, 0);
    }

}
