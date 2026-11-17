package com.ganzithon.moyang.sheep.domain;
public enum SheepTransactionType {
    MEETING_CREATE(-1), MEETING_JOIN(-1), MEETING_REFUND(1), RECORD(1), AI_EXTRA_CHAT(-1);
    private final int amount;
    SheepTransactionType(int amount){this.amount=amount;}
    public int amount(){return amount;}
    public boolean meeting(){return this==MEETING_CREATE||this==MEETING_JOIN||this==MEETING_REFUND;}
}
