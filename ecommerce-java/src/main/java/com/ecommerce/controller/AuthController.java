package com.ecommerce.controller;

import com.ecommerce.service.DbService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Replaces the AUTH section of app.py:
 *   /register_customer  POST
 *   /register_seller    POST
 *   /login              POST
 *   /forgot_password/get_question POST
 *   /forgot_password/reset        POST
 */
@RestController
@CrossOrigin(origins = "*")
public class AuthController {

    private final DbService db;

    public AuthController(DbService db) {
        this.db = db;
    }

    // ── POST /register_customer ───────────────────────────────────────────────
    @PostMapping("/register_customer")
    public ResponseEntity<Map<String, Object>> registerCustomer(@RequestBody Map<String, Object> d) {
        db.update("""
            INSERT INTO Users(login_id,name,email,password,phone,role,date_of_birth,address,
                              security_question,security_answer)
            VALUES(?,?,?,?,?,'customer',?,?,?,?)
            """,
                d.get("login_id"), d.get("name"), d.get("email"), d.get("password"),
                d.getOrDefault("phone", ""), d.getOrDefault("dob", null),
                d.getOrDefault("address", ""), d.getOrDefault("security_question", ""),
                d.getOrDefault("security_answer", "").toString().toLowerCase()
        );
        return ResponseEntity.ok(Map.of("message", "Customer registered"));
    }

    // ── POST /register_seller ─────────────────────────────────────────────────
    @PostMapping("/register_seller")
    public ResponseEntity<Map<String, Object>> registerSeller(@RequestBody Map<String, Object> d) {
        db.update("""
            INSERT INTO Users(login_id,name,email,password,role,security_question,security_answer)
            VALUES(?,?,?,?,'seller',?,?)
            """,
                d.get("login_id"), d.get("company"), d.get("email"), d.get("password"),
                d.getOrDefault("security_question", ""),
                d.getOrDefault("security_answer", "").toString().toLowerCase()
        );
        List<Map<String, Object>> user = db.queryList(
                "SELECT user_id FROM Users WHERE login_id=?", d.get("login_id"));
        long userId = ((Number) user.get(0).get("user_id")).longValue();
        db.update("""
            INSERT INTO Seller_Profiles(user_id,business_id,company_phone,company_address)
            VALUES(?,?,?,?)
            """,
                userId, d.get("business_id"),
                d.getOrDefault("company_phone", ""),
                d.getOrDefault("company_address", "")
        );
        return ResponseEntity.ok(Map.of("message", "Seller registered (pending approval)"));
    }

    // ── POST /login ───────────────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> d) {
        List<Map<String, Object>> users = db.queryList(
                "SELECT * FROM Users WHERE login_id=? AND password=?",
                d.get("login_id"), d.get("password")
        );
        if (users.isEmpty()) {
            return ResponseEntity.ok(Map.of("error", "Invalid credentials"));
        }
        Map<String, Object> u = users.get(0);
        String role = String.valueOf(u.get("role"));
        String approvalStatus = String.valueOf(u.getOrDefault("seller_approval_status", ""));

        if ("seller".equals(role)) {
            if ("suspended".equals(approvalStatus)) {
                return ResponseEntity.ok(Map.of("error", "Your account has been suspended. Contact admin."));
            }
            if (!"approved".equals(approvalStatus)) {
                return ResponseEntity.ok(Map.of("error", "Your seller account is pending admin approval."));
            }
        }
        return ResponseEntity.ok(u);
    }

    // ── POST /forgot_password/get_question ────────────────────────────────────
    @PostMapping("/forgot_password/get_question")
    public ResponseEntity<Map<String, Object>> forgotGetQuestion(@RequestBody Map<String, Object> d) {
        List<Map<String, Object>> rows = db.queryList(
                "SELECT security_question FROM Users WHERE login_id=?", d.get("login_id"));
        if (rows.isEmpty()) return ResponseEntity.ok(Map.of("error", "Login ID not found"));
        Object q = rows.get(0).get("security_question");
        if (q == null || q.toString().isBlank())
            return ResponseEntity.ok(Map.of("error", "No security question set for this account"));
        return ResponseEntity.ok(Map.of("security_question", q));
    }

    // ── POST /forgot_password/reset ───────────────────────────────────────────
    @PostMapping("/forgot_password/reset")
    public ResponseEntity<Map<String, Object>> forgotReset(@RequestBody Map<String, Object> d) {
        List<Map<String, Object>> rows = db.queryList(
                "SELECT user_id,security_answer FROM Users WHERE login_id=?", d.get("login_id"));
        if (rows.isEmpty()) return ResponseEntity.ok(Map.of("error", "Login ID not found"));
        Map<String, Object> u = rows.get(0);
        String storedAnswer = String.valueOf(u.get("security_answer"));
        String givenAnswer  = d.getOrDefault("answer", "").toString().strip().toLowerCase();
        if (!storedAnswer.equals(givenAnswer)) return ResponseEntity.ok(Map.of("error", "Incorrect answer"));

        String newPw = d.getOrDefault("new_password", "").toString().strip();
        if (newPw.length() < 4) return ResponseEntity.ok(Map.of("error", "Password must be at least 4 characters"));

        db.update("UPDATE Users SET password=? WHERE user_id=?", newPw, u.get("user_id"));
        return ResponseEntity.ok(Map.of("message", "Password reset successfully. You can now sign in."));
    }
}
