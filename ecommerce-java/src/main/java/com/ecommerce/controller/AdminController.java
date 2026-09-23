package com.ecommerce.controller;

import com.ecommerce.service.DbService;
import com.ecommerce.service.HelperService;
import com.ecommerce.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
public class AdminController {

    private final DbService db;
    private final HelperService helper;
    private final OrderService orderService;

    public AdminController(DbService db, HelperService helper, OrderService orderService) {
        this.db = db;
        this.helper = helper;
        this.orderService = orderService;
    }

    // NULL-SAFE helper: handles SQL NULL columns that Java maps to null
    // Map.getOrDefault("key","") still returns null when the key exists but the value is null
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

    // GET /admin/sellers
    @GetMapping("/admin/sellers")
    public ResponseEntity<List<Map<String, Object>>> adminSellers() {
        return ResponseEntity.ok(db.queryList("""
            SELECT u.user_id,u.name,u.email,u.seller_approval_status,
                   sp.business_id,sp.company_phone,sp.company_address,
                   COUNT(DISTINCT p.product_id) AS product_count
            FROM Users u
            LEFT JOIN Seller_Profiles sp ON u.user_id=sp.user_id
            LEFT JOIN Products p ON u.user_id=p.seller_id
            WHERE u.role='seller' GROUP BY u.user_id
            """));
    }

    // GET /admin/orders
    @GetMapping("/admin/orders")
    public ResponseEntity<List<Map<String, Object>>> adminOrders() {
        List<Map<String, Object>> orders = db.queryList("""
            SELECT o.*,u.name AS customer_name FROM Orders o
            JOIN Users u ON o.customer_id=u.user_id ORDER BY o.created_at DESC
            """);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> o : orders) {
            Map<String, Object> mO = new HashMap<>(o);
            List<Map<String, Object>> rawItems = db.queryList("""
                SELECT oi.order_item_id, p.name, p.brand, oi.quantity, oi.price,
                       oi.item_status, oi.item_tracking_number, oi.assigned_unit_codes
                FROM Order_Items oi JOIN Products p ON oi.product_id=p.product_id
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

    // POST /approve_seller
    @PostMapping("/approve_seller")
    public ResponseEntity<Map<String, Object>> approveSeller(@RequestBody Map<String, Object> d) {
        db.update("UPDATE Users SET seller_approval_status='approved' WHERE user_id=?", d.get("user_id"));
        return ResponseEntity.ok(Map.of("message", "Seller approved"));
    }

    // POST /admin/suspend_seller
    @PostMapping("/admin/suspend_seller")
    public ResponseEntity<Map<String, Object>> suspendSeller(@RequestBody Map<String, Object> d) {
        db.update("UPDATE Users SET seller_approval_status='suspended' WHERE user_id=?", d.get("user_id"));
        return ResponseEntity.ok(Map.of("message", "Seller suspended"));
    }

    // POST /admin/update_item_tracking
    @PostMapping("/admin/update_item_tracking")
    public ResponseEntity<Map<String, Object>> updateItemTracking(@RequestBody Map<String, Object> d) {
        long orderItemId = ((Number) d.get("order_item_id")).longValue();
        String trk = Objects.toString(d.get("tracking_number"), "").strip();
        if (trk.isEmpty()) trk = helper.generateTrackingId();

        db.update("""
            UPDATE Order_Items SET item_tracking_number=?, item_status='Shipped'
            WHERE order_item_id=?
            """, trk, orderItemId);

        db.update("INSERT INTO Order_Item_Status_Log(order_item_id,status,note) VALUES(?,?,?)",
                orderItemId, "Shipped", "Tracking assigned: " + trk);

        // Sync parent order status
        List<Map<String, Object>> row = db.queryList(
                "SELECT order_id FROM Order_Items WHERE order_item_id=?", orderItemId);
        if (!row.isEmpty()) {
            long orderId = ((Number) row.get(0).get("order_id")).longValue();
            List<Map<String, Object>> statuses = db.queryList(
                    "SELECT item_status FROM Order_Items WHERE order_id=?", orderId);
            List<String> allS = statuses.stream()
                    .map(s -> Objects.toString(s.get("item_status"), "Placed"))
                    .collect(Collectors.toList());
            String orderStatus = "Placed";
            for (String p : List.of("Delivered", "Shipped", "AtWarehouse", "Processing", "Placed")) {
                if (allS.contains(p)) { orderStatus = p; break; }
            }
            db.update("UPDATE Orders SET status=? WHERE order_id=?", orderStatus, orderId);
        }

        return ResponseEntity.ok(Map.of("message", "Item tracking set", "tracking_number", trk));
    }

    // POST /admin/update_order_status
    @PostMapping("/admin/update_order_status")
    public ResponseEntity<Map<String, Object>> updateOrderStatus(@RequestBody Map<String, Object> d) {
        db.update("UPDATE Orders SET status=? WHERE order_id=?", d.get("status"), d.get("order_id"));
        return ResponseEntity.ok(Map.of("message", "Status updated"));
    }
}
