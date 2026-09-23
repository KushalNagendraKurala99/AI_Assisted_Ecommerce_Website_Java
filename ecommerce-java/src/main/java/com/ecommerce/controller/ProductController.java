package com.ecommerce.controller;

import com.ecommerce.service.DbService;
import com.ecommerce.service.HelperService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.Objects;

@RestController
@CrossOrigin(origins = "*")
public class ProductController {

    private final DbService db;
    private final HelperService helper;

    public ProductController(DbService db, HelperService helper) {
        this.db = db;
        this.helper = helper;
    }

    @GetMapping("/categories")
    public ResponseEntity<List<Map<String, Object>>> categories() {
        return ResponseEntity.ok(List.of(
                Map.of("category_id", 1, "name", "Phone"),
                Map.of("category_id", 2, "name", "Watch"),
                Map.of("category_id", 3, "name", "Laptop"),
                Map.of("category_id", 4, "name", "TV")
        ));
    }

    @GetMapping("/products")
    public ResponseEntity<List<Map<String, Object>>> products() {
        return ResponseEntity.ok(db.queryList("""
            SELECT p.*,c.name AS category_name,u.name AS seller_name,
                   IFNULL(AVG(r.rating),0) AS avg_rating,
                   COUNT(DISTINCT r.review_id) AS review_count
            FROM Products p
            LEFT JOIN Categories c ON p.category_id=c.category_id
            LEFT JOIN Users u ON p.seller_id=u.user_id
            LEFT JOIN Reviews r ON p.product_id=r.product_id
            GROUP BY p.product_id
            """));
    }

    @GetMapping("/product/{pid}")
    public ResponseEntity<Map<String, Object>> productDetail(@PathVariable int pid) {
        List<Map<String, Object>> rows = db.queryList("""
            SELECT p.*,c.name AS category_name,u.name AS seller_name,
                   IFNULL(AVG(r.rating),0) AS avg_rating,
                   COUNT(DISTINCT r.review_id) AS review_count
            FROM Products p
            LEFT JOIN Categories c ON p.category_id=c.category_id
            LEFT JOIN Users u ON p.seller_id=u.user_id
            LEFT JOIN Reviews r ON p.product_id=r.product_id
            WHERE p.product_id=? GROUP BY p.product_id
            """, pid);
        if (rows.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Not found"));

        Map<String, Object> product = new HashMap<>(rows.get(0));
        product.put("reviews", db.queryList("""
            SELECT r.*,u.name AS reviewer_name FROM Reviews r
            JOIN Users u ON r.user_id=u.user_id
            WHERE r.product_id=? ORDER BY r.created_at DESC
            """, pid));
        product.put("unit_codes", db.queryList(
                "SELECT unit_id,unit_code,status FROM Product_Units WHERE product_id=? ORDER BY unit_id", pid));
        return ResponseEntity.ok(product);
    }

    @GetMapping("/seller/products/{sellerId}")
    public ResponseEntity<List<Map<String, Object>>> sellerProducts(@PathVariable int sellerId) {
        List<Map<String, Object>> prods = db.queryList("""
            SELECT p.product_id,p.name,p.brand,p.price,p.stock,p.category_id,
                   c.name AS category_name
            FROM Products p
            LEFT JOIN Categories c ON p.category_id=c.category_id
            WHERE p.seller_id=? ORDER BY p.product_id
            """, sellerId);

        // FIX: build new list instead of mutating while iterating
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> p : prods) {
            Map<String, Object> mp = new HashMap<>(p);
            mp.put("units", db.queryList(
                    "SELECT unit_id,unit_code,status FROM Product_Units WHERE product_id=? ORDER BY unit_id",
                    p.get("product_id")));
            result.add(mp);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/add_product")
    public ResponseEntity<Map<String, Object>> addProduct(@RequestBody Map<String, Object> d) {
        // HTML form inputs always arrive as JSON strings (e.g. "999.99", "10", "1")
        // We must parse them ourselves — casting to Number crashes on String values
        String name     = Objects.toString(d.get("name"), "").trim();
        String brand    = Objects.toString(d.getOrDefault("brand", "Generic"), "Generic").trim();
        String desc     = Objects.toString(d.getOrDefault("description", ""), "");
        String imageUrl = Objects.toString(d.getOrDefault("image_url", ""), "");

        if (name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Product name is required"));
        }

        double price;
        int stock, catId, sellerId;
        try {
            price    = Double.parseDouble(Objects.toString(d.get("price"), "0"));
            stock    = Integer.parseInt(Objects.toString(d.getOrDefault("stock", "0"), "0"));
            catId    = Integer.parseInt(Objects.toString(d.getOrDefault("category_id", "1"), "1"));
            sellerId = Integer.parseInt(Objects.toString(d.get("seller_id"), "0"));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid number value: " + e.getMessage()));
        }

        if (price <= 0) return ResponseEntity.badRequest().body(Map.of("error", "Price must be greater than 0"));
        if (stock < 0)  return ResponseEntity.badRequest().body(Map.of("error", "Stock cannot be negative"));
        if (sellerId == 0) return ResponseEntity.badRequest().body(Map.of("error", "seller_id is required"));

        long prodId = db.insert("""
            INSERT INTO Products(name,brand,price,stock,category_id,seller_id,description,image_url)
            VALUES(?,?,?,?,?,?,?,?)
            """, name, brand, price, stock, catId, sellerId, desc, imageUrl);

        // Generate one unique unit code per stock item
        List<Object[]> unitStmts = new ArrayList<>();
        for (int i = 0; i < stock; i++) {
            String code = helper.generateUnitCode(catId, prodId);
            unitStmts.add(new Object[]{
                    "INSERT INTO Product_Units(product_id,unit_code) VALUES(?,?)",
                    new Object[]{prodId, code}
            });
        }
        if (!unitStmts.isEmpty()) db.transaction(unitStmts);

        return ResponseEntity.ok(Map.of(
                "message", "Product added with " + stock + " unit code(s)",
                "product_id", prodId));
    }

    @PostMapping("/seller/delete_product")
    public ResponseEntity<Map<String, Object>> deleteProduct(@RequestBody Map<String, Object> d) {
        int productId = ((Number) d.get("product_id")).intValue();
        int sellerId  = ((Number) d.get("seller_id")).intValue();

        List<Map<String, Object>> rows = db.queryList(
                "SELECT product_id FROM Products WHERE product_id=? AND seller_id=?",
                productId, sellerId);
        if (rows.isEmpty())
            return ResponseEntity.status(403).body(Map.of("error", "Product not found or not yours"));

        db.transaction(List.of(
                new Object[]{"DELETE FROM Cart_Items WHERE product_id=?",   new Object[]{productId}},
                new Object[]{"DELETE FROM Reviews WHERE product_id=?",       new Object[]{productId}},
                new Object[]{"DELETE FROM Product_Units WHERE product_id=?", new Object[]{productId}},
                new Object[]{"DELETE FROM Products WHERE product_id=? AND seller_id=?", new Object[]{productId, sellerId}}
        ));
        return ResponseEntity.ok(Map.of("message", "Product deleted"));
    }

    @PostMapping("/review/add")
    public ResponseEntity<Map<String, Object>> addReview(@RequestBody Map<String, Object> d) {
        int uid    = ((Number) d.get("user_id")).intValue();
        int pid    = ((Number) d.get("product_id")).intValue();
        int rating = ((Number) d.get("rating")).intValue();
        String comment = d.getOrDefault("comment", "").toString();

        List<Map<String, Object>> existing = db.queryList(
                "SELECT review_id FROM Reviews WHERE user_id=? AND product_id=?", uid, pid);
        if (!existing.isEmpty()) {
            db.update("UPDATE Reviews SET rating=?,comment=? WHERE user_id=? AND product_id=?",
                    rating, comment, uid, pid);
            return ResponseEntity.ok(Map.of("message", "Review updated"));
        }
        db.update("INSERT INTO Reviews(user_id,product_id,rating,comment) VALUES(?,?,?,?)",
                uid, pid, rating, comment);
        return ResponseEntity.ok(Map.of("message", "Review added"));
    }

    @PostMapping("/review/delete")
    public ResponseEntity<Map<String, Object>> deleteReview(@RequestBody Map<String, Object> d) {
        db.update("DELETE FROM Reviews WHERE review_id=? AND user_id=?",
                d.get("review_id"), d.get("user_id"));
        return ResponseEntity.ok(Map.of("message", "Review deleted"));
    }
}
