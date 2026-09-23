package com.ecommerce.controller;

import com.ecommerce.service.DbService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Replaces the CART section of app.py:
 *   POST /add_to_cart
 *   POST /update_cart
 *   POST /remove_from_cart
 *   GET  /cart/{user_id}
 */
@RestController
@CrossOrigin(origins = "*")
public class CartController {

    private final DbService db;

    public CartController(DbService db) {
        this.db = db;
    }

    // ── POST /add_to_cart ─────────────────────────────────────────────────────
    // Python: ON DUPLICATE KEY UPDATE quantity=quantity+1
    @PostMapping("/add_to_cart")
    public ResponseEntity<Map<String, Object>> addToCart(@RequestBody Map<String, Object> d) {
        db.update("""
            INSERT INTO Cart_Items(user_id,product_id,quantity) VALUES(?,?,1)
            ON DUPLICATE KEY UPDATE quantity=quantity+1
            """, d.get("user_id"), d.get("product_id"));
        return ResponseEntity.ok(Map.of("message", "Added to cart"));
    }

    // ── POST /update_cart ─────────────────────────────────────────────────────
    @PostMapping("/update_cart")
    public ResponseEntity<Map<String, Object>> updateCart(@RequestBody Map<String, Object> d) {
        int qty = ((Number) d.getOrDefault("quantity", 0)).intValue();
        int cid = ((Number) d.get("cart_item_id")).intValue();
        int uid = ((Number) d.get("user_id")).intValue();
        if (qty <= 0) {
            db.update("DELETE FROM Cart_Items WHERE cart_item_id=? AND user_id=?", cid, uid);
        } else {
            db.update("UPDATE Cart_Items SET quantity=? WHERE cart_item_id=? AND user_id=?", qty, cid, uid);
        }
        return ResponseEntity.ok(Map.of("message", "Cart updated"));
    }

    // ── POST /remove_from_cart ────────────────────────────────────────────────
    @PostMapping("/remove_from_cart")
    public ResponseEntity<Map<String, Object>> removeFromCart(@RequestBody Map<String, Object> d) {
        db.update("DELETE FROM Cart_Items WHERE cart_item_id=? AND user_id=?",
                d.get("cart_item_id"), d.get("user_id"));
        return ResponseEntity.ok(Map.of("message", "Removed"));
    }

    // ── GET /cart/{user_id} ───────────────────────────────────────────────────
    @GetMapping("/cart/{userId}")
    public ResponseEntity<List<Map<String, Object>>> viewCart(@PathVariable int userId) {
        return ResponseEntity.ok(db.queryList("""
            SELECT c.cart_item_id,p.product_id,p.name,p.price,c.quantity,p.stock,
                   p.brand,c.added_at,u.name AS seller_name,cat.name AS category_name
            FROM Cart_Items c
            JOIN Products p ON c.product_id=p.product_id
            JOIN Users u ON p.seller_id=u.user_id
            LEFT JOIN Categories cat ON p.category_id=cat.category_id
            WHERE c.user_id=? ORDER BY c.added_at ASC
            """, userId));
    }
}
