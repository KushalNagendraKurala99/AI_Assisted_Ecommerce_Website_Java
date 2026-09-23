package com.ecommerce.controller;

import com.ecommerce.service.DbService;
import com.ecommerce.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
public class SellerController {

    private final DbService db;
    private final OrderService orderService;

    public SellerController(DbService db, OrderService orderService) {
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

    // GET /seller/orders/{seller_id}
    @GetMapping("/seller/orders/{sellerId}")
    public ResponseEntity<List<Map<String, Object>>> sellerOrders(@PathVariable int sellerId) {
        List<Map<String, Object>> orders = db.queryList("""
            SELECT DISTINCT o.order_id,o.status,o.total_amount,o.created_at,
                   u.name AS customer_name,o.address
            FROM Orders o
            JOIN Order_Items oi ON o.order_id=oi.order_id
            JOIN Products p ON oi.product_id=p.product_id
            JOIN Users u ON o.customer_id=u.user_id
            WHERE p.seller_id=? ORDER BY o.created_at DESC
            """, sellerId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> o : orders) {
            Map<String, Object> mO = new HashMap<>(o);
            List<Map<String, Object>> rawItems = db.queryList("""
                SELECT oi.order_item_id, p.name, p.brand, oi.quantity, oi.price,
                       oi.item_status, oi.item_tracking_number, oi.assigned_unit_codes
                FROM Order_Items oi JOIN Products p ON oi.product_id=p.product_id
                WHERE oi.order_id=? AND p.seller_id=?
                """, o.get("order_id"), sellerId);
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

    // POST /seller/update_item_status
    @PostMapping("/seller/update_item_status")
    public ResponseEntity<Map<String, Object>> updateItemStatus(@RequestBody Map<String, Object> d) {
        long orderItemId = ((Number) d.get("order_item_id")).longValue();
        String status    = d.get("status").toString();
        String note      = Objects.toString(d.get("note"), "Updated by seller");
        orderService.updateItemStatus(orderItemId, status, note);
        return ResponseEntity.ok(Map.of("message", "Item status updated"));
    }
}
