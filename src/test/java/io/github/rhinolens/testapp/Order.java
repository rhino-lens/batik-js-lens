package io.github.rhinolens.testapp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A JPA-entity-like object for tests: id, simple properties, a relation, collections, an array, a public field. */
public class Order {

    /** Counts calls to {@link #getCustomer()}, to prove old-value modes avoid "lazy" getters. */
    public static int customerGetterCalls;

    /** A public field: {@code order.note = "…"} assigns it directly. */
    public String note;

    private final long id;
    private String status = "NEW";
    private double finalTotal;
    private Customer customer = new Customer(1, "Ann");
    private String auditJson;
    private final List<LineItem> items = new ArrayList<>();
    private final String[] tags = { "retail", "web" };
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    public Order(long id) {
        this.id = id;
        attributes.put("channel", "store");
        items.add(new LineItem("MUG-RED", 2, 12.5));
        items.add(new LineItem("TEA-200G", 1, 8.99));
    }

    public long getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getFinalTotal() {
        return finalTotal;
    }

    public void setFinalTotal(double finalTotal) {
        this.finalTotal = finalTotal;
    }

    public Customer getCustomer() {
        customerGetterCalls++;
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public String getAuditJson() {
        return auditJson;
    }

    public void setAuditJson(String auditJson) {
        this.auditJson = auditJson;
    }

    public List<LineItem> getItems() {
        return items;
    }

    public String[] getTags() {
        return tags;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String toString() {
        return "Order#" + id;
    }
}
