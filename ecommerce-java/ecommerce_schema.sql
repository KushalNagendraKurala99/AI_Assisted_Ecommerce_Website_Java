-- ============================================================
--  ecommerce_db — Full Schema
--  Creation order respects foreign key dependencies
-- ============================================================

CREATE DATABASE IF NOT EXISTS `ecommerce_db`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `ecommerce_db`;

-- ------------------------------------------------------------
-- 1. Users
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `users` (
  `user_id`                INT          NOT NULL AUTO_INCREMENT,
  `login_id`               VARCHAR(50)  NOT NULL,
  `name`                   VARCHAR(100) NOT NULL,
  `email`                  VARCHAR(150) NOT NULL,
  `password`               VARCHAR(255) NOT NULL,
  `phone`                  VARCHAR(20)  NULL DEFAULT NULL,
  `role`                   ENUM('customer', 'seller', 'admin') NOT NULL DEFAULT 'customer',
  `date_of_birth`          DATE         NULL DEFAULT NULL,
  `address`                VARCHAR(300) NULL DEFAULT NULL,
  `seller_approval_status` VARCHAR(20)  NULL DEFAULT NULL,
  `created_at`             TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP,
  `security_question`      VARCHAR(255) NULL DEFAULT NULL,
  `security_answer`        VARCHAR(255) NULL DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE INDEX `uq_login_id` (`login_id`),
  UNIQUE INDEX `uq_email`    (`email`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 12
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ------------------------------------------------------------
-- 2. Categories
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `categories` (
  `category_id` INT          NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(100) NOT NULL,
  PRIMARY KEY (`category_id`),
  UNIQUE INDEX `uq_category_name` (`name`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 5
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ------------------------------------------------------------
-- 3. Seller Profiles
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `seller_profiles` (
  `profile_id`      INT          NOT NULL AUTO_INCREMENT,
  `user_id`         INT          NOT NULL,
  `business_id`     VARCHAR(100) NULL DEFAULT NULL,
  `company_phone`   VARCHAR(30)  NULL DEFAULT NULL,
  `company_address` VARCHAR(300) NULL DEFAULT NULL,
  `created_at`      TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`profile_id`),
  UNIQUE INDEX `uq_seller_user` (`user_id`),
  CONSTRAINT `fk_seller_profiles_user`
    FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`)
    ON DELETE CASCADE
) ENGINE = InnoDB
  AUTO_INCREMENT = 6
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ------------------------------------------------------------
-- 4. Products
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `products` (
  `product_id`  INT           NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(200)  NOT NULL,
  `brand`       VARCHAR(100)  NULL DEFAULT 'Generic',
  `price`       DECIMAL(10,2) NOT NULL,
  `stock`       INT           NOT NULL DEFAULT 0,
  `category_id` INT           NULL DEFAULT NULL,
  `seller_id`   INT           NULL DEFAULT NULL,
  `description` TEXT          NULL DEFAULT NULL,
  `image_url`   VARCHAR(500)  NULL DEFAULT NULL,
  `created_at`  TIMESTAMP     NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`product_id`),
  INDEX `idx_products_category` (`category_id`),
  INDEX `idx_products_seller`   (`seller_id`),
  CONSTRAINT `fk_products_category`
    FOREIGN KEY (`category_id`)
    REFERENCES `categories` (`category_id`)
    ON DELETE SET NULL,
  CONSTRAINT `fk_products_seller`
    FOREIGN KEY (`seller_id`)
    REFERENCES `users` (`user_id`)
    ON DELETE SET NULL
) ENGINE = InnoDB
  AUTO_INCREMENT = 12
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ------------------------------------------------------------
-- 5. Product Units
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `product_units` (
  `unit_id`    INT         NOT NULL AUTO_INCREMENT,
  `product_id` INT         NOT NULL,
  `unit_code`  VARCHAR(20) NOT NULL,
  `status`     ENUM('available', 'sold') NOT NULL DEFAULT 'available',
  `created_at` TIMESTAMP   NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`unit_id`),
  UNIQUE INDEX `uq_unit_code`              (`unit_code`),
  INDEX        `idx_product_units_product` (`product_id`),
  CONSTRAINT `fk_product_units_product`
    FOREIGN KEY (`product_id`)
    REFERENCES `products` (`product_id`)
    ON DELETE CASCADE
) ENGINE = InnoDB
  AUTO_INCREMENT = 52
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ------------------------------------------------------------
-- 6. Cart Items
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `cart_items` (
  `cart_item_id` INT       NOT NULL AUTO_INCREMENT,
  `user_id`      INT       NOT NULL,
  `product_id`   INT       NOT NULL,
  `quantity`     INT       NOT NULL DEFAULT 1,
  `added_at`     TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`cart_item_id`),
  UNIQUE INDEX `uq_cart`               (`user_id`, `product_id`),
  INDEX        `idx_cart_items_product` (`product_id`),
  CONSTRAINT `fk_cart_items_user`
    FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_cart_items_product`
    FOREIGN KEY (`product_id`)
    REFERENCES `products` (`product_id`)
    ON DELETE CASCADE
) ENGINE = InnoDB
  AUTO_INCREMENT = 31
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ------------------------------------------------------------
-- 7. Orders
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `orders` (
  `order_id`            INT           NOT NULL AUTO_INCREMENT,
  `customer_id`         INT           NOT NULL,
  `total_amount`        DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  `status`              VARCHAR(30)   NOT NULL DEFAULT 'Placed',
  `payment_last4`       VARCHAR(4)    NULL DEFAULT NULL,
  `payment_card_holder` VARCHAR(150)  NULL DEFAULT NULL,
  `address`             VARCHAR(300)  NULL DEFAULT NULL,
  `tracking_number`     VARCHAR(100)  NULL DEFAULT NULL,
  `created_at`          TIMESTAMP     NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`order_id`),
  INDEX `idx_orders_customer` (`customer_id`),
  CONSTRAINT `fk_orders_customer`
    FOREIGN KEY (`customer_id`)
    REFERENCES `users` (`user_id`)
    ON DELETE CASCADE
) ENGINE = InnoDB
  AUTO_INCREMENT = 24
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ------------------------------------------------------------
-- 8. Order Items
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_items` (
  `order_item_id`        INT           NOT NULL AUTO_INCREMENT,
  `order_id`             INT           NOT NULL,
  `product_id`           INT           NOT NULL,
  `quantity`             INT           NOT NULL DEFAULT 1,
  `price`                DECIMAL(10,2) NOT NULL,
  `item_status`          VARCHAR(30)   NULL DEFAULT 'Placed',
  `item_tracking_number` VARCHAR(100)  NULL DEFAULT NULL,
  `assigned_unit_codes`  TEXT          NULL DEFAULT NULL,
  PRIMARY KEY (`order_item_id`),
  INDEX `idx_order_items_order`   (`order_id`),
  INDEX `idx_order_items_product` (`product_id`),
  CONSTRAINT `fk_order_items_order`
    FOREIGN KEY (`order_id`)
    REFERENCES `orders` (`order_id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_order_items_product`
    FOREIGN KEY (`product_id`)
    REFERENCES `products` (`product_id`)
    ON DELETE CASCADE
) ENGINE = InnoDB
  AUTO_INCREMENT = 27
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ------------------------------------------------------------
-- 9. Order Status Log
--    FIX: moved after orders (table 7), which it references
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_status_log` (
  `log_id`     INT          NOT NULL AUTO_INCREMENT,
  `order_id`   INT          NOT NULL,
  `status`     VARCHAR(30)  NOT NULL,
  `note`       VARCHAR(255) NULL DEFAULT '',
  `changed_at` TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`log_id`),
  INDEX `idx_order_status_log_order` (`order_id`),
  CONSTRAINT `fk_order_status_log_order`
    FOREIGN KEY (`order_id`)
    REFERENCES `orders` (`order_id`)
    ON DELETE CASCADE
) ENGINE = InnoDB
  AUTO_INCREMENT = 39
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ------------------------------------------------------------
-- 10. Order Item Status Log
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_item_status_log` (
  `log_id`        INT          NOT NULL AUTO_INCREMENT,
  `order_item_id` INT          NOT NULL,
  `status`        VARCHAR(30)  NOT NULL,
  `note`          VARCHAR(255) NULL DEFAULT '',
  `changed_at`    TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`log_id`),
  INDEX `idx_order_item_status_log` (`order_item_id`),
  CONSTRAINT `fk_order_item_status_log`
    FOREIGN KEY (`order_item_id`)
    REFERENCES `order_items` (`order_item_id`)
    ON DELETE CASCADE
) ENGINE = InnoDB
  AUTO_INCREMENT = 11
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ------------------------------------------------------------
-- 11. Reviews
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `reviews` (
  `review_id`  INT       NOT NULL AUTO_INCREMENT,
  `user_id`    INT       NOT NULL,
  `product_id` INT       NOT NULL,
  `rating`     INT       NOT NULL,
  `comment`    TEXT      NULL DEFAULT NULL,
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`review_id`),
  UNIQUE INDEX `uq_review`           (`user_id`, `product_id`),
  INDEX        `idx_reviews_product` (`product_id`),
  CONSTRAINT `fk_reviews_user`
    FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_reviews_product`
    FOREIGN KEY (`product_id`)
    REFERENCES `products` (`product_id`)
    ON DELETE CASCADE
) ENGINE = InnoDB
  AUTO_INCREMENT = 3
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
