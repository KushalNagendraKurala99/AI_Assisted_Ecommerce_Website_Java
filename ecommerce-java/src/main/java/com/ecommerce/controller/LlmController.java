package com.ecommerce.controller;

import com.ecommerce.service.DbService;
import com.ecommerce.service.LlmService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Replaces the LLM section of app.py:
 *   POST /ask
 *
 * Sends a natural-language question to Ollama (Mistral),
 * gets back a SQL query, runs it, and returns the result.
 */
@RestController
@CrossOrigin(origins = "*")
public class LlmController {

    private final DbService db;
    private final LlmService llm;

    // Replaces: SCHEMA and EXAMPLE_QA constants at top of app.py
    private static final String SCHEMA = """
        Database: ecommerce_db (MySQL)
        Tables:
          Users(user_id PK, login_id, name, email, password, phone, role ENUM[customer,seller,admin],
                date_of_birth, address, seller_approval_status, security_question, security_answer, created_at)
          Categories(category_id PK, name)
          Products(product_id PK, name, brand, price DECIMAL, stock INT, category_id FK,
                   seller_id FK->Users, description, created_at)
          Product_Units(unit_id PK, product_id FK, unit_code UNIQUE, status ENUM[available,sold], created_at)
          Cart_Items(cart_item_id PK, user_id FK, product_id FK, quantity, added_at)
          Orders(order_id PK, customer_id FK->Users, total_amount DECIMAL, status,
                 payment_last4, payment_card_holder, address, created_at)
          Order_Items(order_item_id PK, order_id FK, product_id FK, quantity, price DECIMAL,
                      item_status VARCHAR, item_tracking_number VARCHAR,
                      assigned_unit_codes VARCHAR [CSV of serial codes assigned at purchase])
          Order_Item_Status_Log(log_id PK, order_item_id FK, status, note, changed_at)
          Order_Status_Log(log_id PK, order_id FK, status, note, changed_at)
          Seller_Profiles(profile_id PK, user_id FK, business_id, company_phone, company_address, created_at)
          Reviews(review_id PK, user_id FK, product_id FK, rating INT 1-5, comment, created_at)
        item_status values: Placed, Processing, AtWarehouse, Shipped, Delivered, Cancelled
        """;

    private static final String EXAMPLE_QA = """
        Q: Total revenue? A: SELECT SUM(total_amount) AS total_revenue FROM Orders WHERE status!='Cancelled';
        Q: Top 5 products by sales? A: SELECT p.name,SUM(oi.quantity) AS sold FROM Order_Items oi JOIN Products p ON oi.product_id=p.product_id GROUP BY p.product_id ORDER BY sold DESC LIMIT 5;
        Q: Low stock products? A: SELECT name,brand,stock FROM Products WHERE stock<5 ORDER BY stock;
        Q: Show all unit codes for a product? A: SELECT unit_code,status FROM Product_Units WHERE product_id=1;
        Q: How many unit codes are sold? A: SELECT COUNT(*) AS sold_units FROM Product_Units WHERE status='sold';
        Q: Items that have been shipped? A: SELECT oi.order_item_id,p.name,oi.item_tracking_number FROM Order_Items oi JOIN Products p ON oi.product_id=p.product_id WHERE oi.item_status='Shipped';
        """;

    public LlmController(DbService db, LlmService llm) {
        this.db = db;
        this.llm = llm;
    }

    // ── POST /ask ─────────────────────────────────────────────────────────────
    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(@RequestBody Map<String, Object> d) {
        String question = d.getOrDefault("question", "").toString().strip();
        if (question.isEmpty()) return ResponseEntity.ok(Map.of("error", "No question"));

        String prompt = "You are a MySQL expert. Write a single valid MySQL SELECT query. " +
                "Return ONLY raw SQL, no markdown, no backticks, no semicolon.\n" +
                SCHEMA + "\n" + EXAMPLE_QA + "\nQuestion: " + question + "\nSQL:";

        String sql = llm.askLlm(prompt)
                .strip()
                .replace("```sql", "")
                .replace("```", "")
                .strip();
        // Remove trailing semicolon if present
        if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1).strip();

        if (!sql.toUpperCase().stripLeading().startsWith("SELECT")) {
            return ResponseEntity.ok(Map.of("error", "Only SELECT queries allowed", "sql", sql));
        }

        try {
            List<Map<String, Object>> result = db.queryList(sql);
            return ResponseEntity.ok(Map.of("sql", sql, "result", result, "count", result.size()));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("error", e.getMessage(), "sql", sql));
        }
    }
}
