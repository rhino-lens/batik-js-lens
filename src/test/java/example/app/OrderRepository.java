package example.app;

import java.util.ArrayList;
import java.util.List;

/** A CRUD-style service (recognized by its name ending in "Repository"). */
public class OrderRepository {

    public final List<Order> saved = new ArrayList<>();

    public Order save(Order order) {
        saved.add(order);
        return order;
    }

    public Order findById(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("no order " + id);
        }
        return new Order(id);
    }
}
