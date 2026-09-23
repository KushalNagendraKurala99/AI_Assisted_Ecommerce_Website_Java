package com.ecommerce.controller;

import com.ecommerce.service.DbService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replaces the PROFILE section of app.py:
 *   GET  /profile/{user_id}
 *   POST /profile/update
 *   POST /profile/delete_field
 */
@RestController
@CrossOrigin(origins = "*")
public class ProfileController {

    private final DbService db;

    public ProfileController(DbService db) {
        this.db = db;
    }

    // ── GET /profile/{user_id} ────────────────────────────────────────────────
    @GetMapping("/profile/{userId}")
    public ResponseEntity<Map<String, Object>> getProfile(@PathVariable int userId) {
        List<Map<String, Object>> rows = db.queryList("""
            SELECT u.user_id,u.login_id,u.name,u.email,u.phone,u.address,
                   u.date_of_birth,u.role,u.created_at,
                   sp.business_id,sp.company_phone,sp.company_address
            FROM Users u LEFT JOIN Seller_Profiles sp ON u.user_id=sp.user_id
            WHERE u.user_id=?
            """, userId);
        if (rows.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Not found"));
        return ResponseEntity.ok(rows.get(0));
    }

    // ── POST /profile/update ──────────────────────────────────────────────────
    @PostMapping("/profile/update")
    public ResponseEntity<Map<String, Object>> updateProfile(@RequestBody Map<String, Object> d) {
        int uid = ((Number) d.get("user_id")).intValue();
        String field = String.valueOf(d.get("field"));
        Object value = d.getOrDefault("value", "");

        Set<String> userFields   = Set.of("phone", "address", "name");
        Set<String> sellerFields = Set.of("company_phone", "company_address");

        if (userFields.contains(field)) {
            db.update("UPDATE Users SET " + field + "=? WHERE user_id=?", value, uid);
        } else if (sellerFields.contains(field)) {
            db.update("UPDATE Seller_Profiles SET " + field + "=? WHERE user_id=?", value, uid);
        } else {
            return ResponseEntity.status(400).body(Map.of("error", "Invalid field"));
        }
        return ResponseEntity.ok(Map.of("message", field + " updated"));
    }

    // ── POST /profile/delete_field ────────────────────────────────────────────
    @PostMapping("/profile/delete_field")
    public ResponseEntity<Map<String, Object>> deleteProfileField(@RequestBody Map<String, Object> d) {
        int uid = ((Number) d.get("user_id")).intValue();
        String field = String.valueOf(d.get("field"));

        Set<String> userFields   = Set.of("phone", "address");
        Set<String> sellerFields = Set.of("company_phone", "company_address");

        if (userFields.contains(field)) {
            db.update("UPDATE Users SET " + field + "=NULL WHERE user_id=?", uid);
        } else if (sellerFields.contains(field)) {
            db.update("UPDATE Seller_Profiles SET " + field + "=NULL WHERE user_id=?", uid);
        } else {
            return ResponseEntity.status(400).body(Map.of("error", "Invalid field"));
        }
        return ResponseEntity.ok(Map.of("message", field + " removed"));
    }
}
