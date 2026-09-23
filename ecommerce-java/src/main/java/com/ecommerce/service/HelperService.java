package com.ecommerce.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;

/**
 * Replaces the helper functions at the top of app.py:
 *   generate_tracking_id()
 *   generate_unit_code(category_id, product_id)
 *   CAT_PREFIX dict
 */
@Service
public class HelperService {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final Random RANDOM = new Random();

    // Replaces: CAT_PREFIX = {1:"PHN", 2:"WCH", 3:"LPT", 4:"TVS"}
    private static final Map<Integer, String> CAT_PREFIX = Map.of(
            1, "PHN",
            2, "WCH",
            3, "LPT",
            4, "TVS"
    );

    /**
     * Replaces:
     *   def generate_tracking_id():
     *       suffix = ''.join(random.choices(string.ascii_uppercase + string.digits, k=6))
     *       return f"TRK-2026-{suffix}"
     */
    public String generateTrackingId() {
        return "TRK-2026-" + randomString(6);
    }

    /**
     * Replaces:
     *   def generate_unit_code(category_id, product_id):
     *       prefix = CAT_PREFIX.get(category_id, "PRD")
     *       suffix = ''.join(random.choices(string.ascii_uppercase + string.digits, k=4))
     *       return f"{prefix}-{product_id:05d}-{suffix}"
     */
    public String generateUnitCode(int categoryId, long productId) {
        String prefix = CAT_PREFIX.getOrDefault(categoryId, "PRD");
        return String.format("%s-%05d-%s", prefix, productId, randomString(4));
    }

    private String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
