package com.ecommerce.controller;

import com.ecommerce.service.DbService;
import com.ecommerce.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
public class OrderController {

    private final DbService db;
    private final OrderService orderService;

    public OrderController(DbService db, OrderService orderService) {
        this.db = db;
        this.orderService = orderService;
    }

    // NULL-SAFE: SQL NULL columns arrive as Java null, not as empty string
    private static List<String> parseSerialCodes(Map<String, Object> item) {
        Object raw = item.get("assigned_unit_codes");
        if (raw == null) return Collections.emptyList();
        String str = raw.toString().trim();
        if (str.isEmpty()) return Collections.emptyList();
        return Arrays.stream(str.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // POST /checkout
    @PostMapping("/checkout")
    public ResponseEntity<Map<String, Object>> checkout(@RequestBody Map<String, Object> d) {
        int userId = ((Number) d.get("user_id")).intValue();
        List<Map<String, Object>> allCart = db.queryList(
                "SELECT cart_item_id FROM Cart_Items WHERE user_id=? ORDER BY added_at ASC", userId);
        if (allCart.isEmpty()) return ResponseEntity.ok(Map.of("error", "Cart is empty"));

        List<Map<String, Object>> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Map<String, Object> row : allCart) {
            try {
                int cartItemId = ((Number) row.get("cart_item_id")).intValue();
                OrderService.OrderResult r = orderService.placeSingleItemOrder(
                        userId, cartItemId,
                        d.getOrDefault("card", "").toString(),
                        d.getOrDefault("card_holder", "").toString(),
                        Objects.toString(d.get("address"), ""));
                results.add(Map.of("order_id", r.orderId(), "total", r.total(), "serial_codes", r.serialCodes()));
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
            }
        }

        String msg = results.size() + " order(s) placed" + (errors.isEmpty() ? "" : "; " + errors.size() + " failed");
        return ResponseEntity.ok(Map.of("message", msg, "orders", results, "errors", errors));
    }

    // POST /checkout_item
    @PostMapping("/checkout_item")
    public ResponseEntity<Map<String, Object>> checkoutItem(@RequestBody Map<String, Object> d) {
        int userId     = ((Number) d.get("user_id")).intValue();
        int cartItemId = ((Number) d.get("cart_item_id")).intValue();
        try {
            OrderService.OrderResult r = orderService.placeSingleItemOrder(
                    userId, cartItemId,
                    d.getOrDefault("card", "").toString(),
                    d.getOrDefault("card_holder", "").toString(),
                    Objects.toString(d.get("address"), ""));
            return ResponseEntity.ok(Map.of(
                    "message", "Order placed",
                    "order_id", r.orderId(),
                    "total", r.total(),
                    "serial_codes", r.serialCodes()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(Map.of("error", e.getMessage()));
        }
    }

    // GET /orders/{user_id}
    @GetMapping("/orders/{userId}")
    public ResponseEntity<List<Map<String, Object>>> myOrders(@PathVariable int userId) {
        List<Map<String, Object>> orders = db.queryList("""
            SELECT o.order_id,o.status,o.total_amount,o.created_at,o.payment_last4,o.address
            FROM Orders o WHERE o.customer_id=? ORDER BY o.created_at DESC
            """, userId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> o : orders) {
            Map<String, Object> mO = new HashMap<>(o);
            List<Map<String, Object>> rawItems = db.queryList("""
                SELECT oi.order_item_id,oi.quantity,oi.price,oi.item_status,oi.item_tracking_number,
                       oi.assigned_unit_codes,
                       p.product_id,p.name,p.brand,c.name AS category_name
                FROM Order_Items oi
                JOIN Products p ON oi.product_id=p.product_id
                LEFT JOIN Categories c ON p.category_id=c.category_id
                WHERE oi.order_id=?
                """, o.get("order_id"));
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> item : rawItems) {
                Map<String, Object> mItem = new HashMap<>(item);
                mItem.put("serial_codes", parseSerialCodes(mItem));  // NULL-SAFE
                items.add(mItem);
            }
            mO.put("items", items);
            result.add(mO);
        }
        return ResponseEntity.ok(result);
    }

    // GET /order/{oid}
    @GetMapping("/order/{oid}")
    public ResponseEntity<Map<String, Object>> orderDetail(@PathVariable int oid) {
        List<Map<String, Object>> rows = db.queryList("""
            SELECT o.*,u.name AS customer_name FROM Orders o
            JOIN Users u ON o.customer_id=u.user_id WHERE o.order_id=?
            """, oid);
        if (rows.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Not found"));

        Map<String, Object> order = new HashMap<>(rows.get(0));
        List<Map<String, Object>> rawItems = db.queryList("""
            SELECT oi.*,p.name,p.brand,c.name AS category_name
            FROM Order_Items oi
            JOIN Products p ON oi.product_id=p.product_id
            LEFT JOIN Categories c ON p.category_id=c.category_id
            WHERE oi.order_id=?
            """, oid);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> item : rawItems) {
            Map<String, Object> mItem = new HashMap<>(item);
            mItem.put("serial_codes", parseSerialCodes(mItem));  // NULL-SAFE
            mItem.put("status_log", db.queryList("""
                SELECT status,note,changed_at FROM Order_Item_Status_Log
                WHERE order_item_id=? ORDER BY changed_at ASC
                """, mItem.get("order_item_id")));
            items.add(mItem);
        }
        order.put("items", items);
        order.put("status_log", db.queryList("""
            SELECT status,note,changed_at FROM Order_Status_Log
            WHERE order_id=? ORDER BY changed_at ASC
            """, oid));
        return ResponseEntity.ok(order);
    }
}
