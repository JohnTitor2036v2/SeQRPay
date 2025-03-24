package com.example.seqrpay;

public class Transaction {
    private String description;
    private String amount;
    private String date;

    public Transaction(String description, String amount, String date) {
        this.description = description;
        this.amount = amount;
        this.date = date;
    }

    public String getDescription() {
        return description;
    }

    public String getAmount() {
        return amount;
    }

    public String getDate() {
        return date;
    }
}
