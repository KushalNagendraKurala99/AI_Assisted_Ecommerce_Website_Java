package com.ecommerce.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final DbService db;
    private final HelperService helper;

    public OrderService(DbService db, HelperService helper) {
        this.db = db;
        this.helper = helper;
    }

    public record OrderResult(long orderId, double total, List<String> serialCodes) {}

    /**
     * Places ONE order for a single cart item.
     * Exact replica of _place_single_item_order() in Python app.py.
     * The whole method is one transaction — rolls back on any failure.
     */
    @Transactional
    public OrderResult placeSingleItemOrder(int userId, int cartItemId,
                                            String card, String holder, String address) {
        // 1. Fetch cart item + product
        List<Map<String, Object>> rows = db.queryList("""
            SELECT p.product_id, p.price, p.stock, p.name, c.quantity, c.cart_item_id
            FROM Cart_Items c
            JOIN Products p ON c.product_id = p.product_id
            WHERE c.user_id = ? AND c.cart_item_id = ?
            """, userId, cartItemId);

        if (rows.isEmpty()) throw new IllegalArgumentException("Cart item not found");

        Map<String, Object> item = rows.get(0);
        int qty       = ((Number) item.get("quantity")).intValue();
        int stock     = ((Number) item.get("stock")).intValue();
        int productId = ((Number) item.get("product_id")).intValue();
        double price  = ((Number) item.get("price")).doubleValue();
        String name   = String.valueOf(item.get("name"));

        // 2. Validate stock
        if (qty > stock)
            throw new IllegalArgumentException("Not enough stock for '" + name + "'");

        // 3. Grab the next `qty` available unit codes
        List<Map<String, Object>> availableUnits = db.queryList("""
            SELECT unit_id, unit_code FROM Product_Units
            WHERE product_id = ? AND status = 'available'
            ORDER BY unit_id ASC LIMIT ?
            """, productId, qty);

        if (availableUnits.size() < qty)
            throw new IllegalArgumentException("Not enough serialised units for '" + name + "'");

        List<Object> unitIds   = availableUnits.stream().map(u -> u.get("unit_id")).collect(Collectors.toList());
        List<String> unitCodes = availableUnits.stream().map(u -> u.get("unit_code").toString()).collect(Collectors.toList());
        String serialStr = String.join(", ", unitCodes);

        // 4. Resolve delivery address (fall back to user's saved address)
        if (address == null || address.isBlank()) {
            List<Map<String, Object>> cust = db.queryList("SELECT address FROM Users WHERE user_id=?", userId);
            address = cust.isEmpty() ? "" : String.valueOf(cust.get(0).getOrDefault("address", ""));
        }

        String last4 = (card != null && card.replace(" ", "").length() >= 4)
                ? card.replace(" ", "").substring(card.replace(" ", "").length() - 4)
                : "";
        double total = price * qty;

        // 5a. Insert parent order
        long orderId = db.insert("""
            INSERT INTO Orders(customer_id, total_amount, status,
                               payment_last4, payment_card_holder, address)
            VALUES(?, ?, 'Placed', ?, ?, ?)
            """, userId, total, last4, holder, address);

        // 5b. Insert order item (with serial codes)
        long oiId = db.insert("""
            INSERT INTO Order_Items(order_id, product_id, quantity, price,
                                    item_status, assigned_unit_codes)
            VALUES(?, ?, ?, ?, 'Placed', ?)
            """, orderId, productId, qty, price, serialStr);

        // 5c. Batch: logs + stock decrement + mark units sold + remove cart item
        String uidPlaceholders = unitIds.stream().map(x -> "?").collect(Collectors.joining(","));
        Object[] soldParams = unitIds.toArray();

        List<Object[]> stmts = new ArrayList<>();
        stmts.add(new Object[]{
            "INSERT INTO Order_Status_Log(order_id,status,note) VALUES(?,?,?)",
            new Object[]{orderId, "Placed", "Order placed by customer"}
        });
        stmts.add(new Object[]{
            "INSERT INTO Order_Item_Status_Log(order_item_id,status,note) VALUES(?,?,?)",
            new Object[]{oiId, "Placed", "Serials assigned: " + serialStr}
        });
        stmts.add(new Object[]{
            "UPDATE Products SET stock = stock - ? WHERE product_id = ?",
            new Object[]{qty, productId}
        });
        stmts.add(new Object[]{
            "UPDATE Product_Units SET status='sold' WHERE unit_id IN (" + uidPlaceholders + ")",
            soldParams
        });
        stmts.add(new Object[]{
            "DELETE FROM Cart_Items WHERE cart_item_id = ? AND user_id = ?",
            new Object[]{cartItemId, userId}
        });
        db.transaction(stmts);

        return new OrderResult(orderId, total, unitCodes);
    }

    /**
     * Updates an order item's status and syncs the parent order status.
     * Replaces seller_update_item_status + admin_update_item_tracking sync logic.
     */
    public void updateItemStatus(long orderItemId, String status, String note) {
        db.update("UPDATE Order_Items SET item_status=? WHERE order_item_id=?", status, orderItemId);
        db.update("INSERT INTO Order_Item_Status_Log(order_item_id,status,note) VALUES(?,?,?)",
                orderItemId, status, note);

        // Sync parent order to most-advanced item status
        List<Map<String, Object>> row = db.queryList(
                "SELECT order_id FROM Order_Items WHERE order_item_id=?", orderItemId);
        if (!row.isEmpty()) {
            long orderId = ((Number) row.get(0).get("order_id")).longValue();
            List<Map<String, Object>> statuses = db.queryList(
                    "SELECT item_status FROM Order_Items WHERE order_id=?", orderId);
            List<String> allS = statuses.stream()
                    .map(s -> s.get("item_status").toString())
                    .collect(Collectors.toList());
            String orderStatus = "Placed";
            for (String p : List.of("Delivered", "Shipped", "AtWarehouse", "Processing", "Placed")) {
                if (allS.contains(p)) { orderStatus = p; break; }
            }
            db.update("UPDATE Orders SET status=? WHERE order_id=?", orderStatus, orderId);
        }
    }
}
