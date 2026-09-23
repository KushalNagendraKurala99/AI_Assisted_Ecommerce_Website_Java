# Electronics E-commerce — Java Spring Boot Backend

Converted from Python/Flask to Java/Spring Boot. Full-featured e-commerce backend with AI-powered natural language queries via Ollama.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Database | MySQL |
| Connection Pool | HikariCP |
| HTTP Client (Ollama) | Spring WebFlux (WebClient) |
| AI / LLM | Ollama (Mistral) |
| Frontend | Vanilla HTML/CSS/JS (`app.html`) |

---

## Project Structure

```
src/main/java/com/ecommerce/
├── EcommerceApplication.java          ← Entry point
├── config/
│   └── CorsConfig.java                ← CORS (allows frontend to call API)
├── controller/
│   ├── AuthController.java            ← /login, /register, /forgot_password
│   ├── ProfileController.java         ← /profile
│   ├── ProductController.java         ← /products, /add_product, /review
│   ├── CartController.java            ← /cart, /add_to_cart
│   ├── OrderController.java           ← /checkout, /orders
│   ├── SellerController.java          ← /seller/orders, /seller/update_item_status
│   ├── AdminController.java           ← /admin/sellers, /admin/orders, /approve_seller
│   └── LlmController.java             ← /ask (NL → SQL via Ollama)
└── service/
    ├── DbService.java                 ← MySQL queries (replaces db.py)
    ├── LlmService.java                ← Ollama HTTP client (replaces llm.py)
    ├── OrderService.java              ← Checkout logic & order status sync
    └── HelperService.java             ← Tracking ID & unit code generators
```

---

## Prerequisites

- Java 17+ (JDK, not JRE) — https://adoptium.net
- Maven 3.8+ — https://maven.apache.org/download.cgi
- MySQL 8.0+
- Ollama — https://ollama.com

---

## Setup

### 1. Database
Import your schema into MySQL:
```sql
-- run ecommerce_schema.sql in MySQL Workbench or via CLI
mysql -u root -p ecommerce_db < ecommerce_schema.sql
```

### 2. Configure database connection
Edit `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/ecommerce_db
spring.datasource.username=root
spring.datasource.password=YOUR_PASSWORD
```

### 3. Start Ollama
```bash
ollama serve          # start the server (runs in background on Windows)
ollama pull mistral   # download Mistral model (first time only, ~4GB)
```

### 4. Run the backend
```bash
mvn clean install
mvn spring-boot:run
```
Server starts on **http://localhost:5000**

### 5. Open the frontend
Double-click `frontend/app.html` — it connects to `localhost:5000` automatically.

---

## API Endpoints

### Auth
| Method | Endpoint | Description |
|---|---|---|
| POST | `/login` | Sign in |
| POST | `/register_customer` | New customer account |
| POST | `/register_seller` | New seller application |
| POST | `/forgot_password/get_question` | Get security question |
| POST | `/forgot_password/reset` | Reset password |

### Products
| Method | Endpoint | Description |
|---|---|---|
| GET | `/products` | All products with ratings |
| GET | `/product/{id}` | Product detail + reviews + unit codes |
| GET | `/categories` | Category list |
| POST | `/add_product` | Seller adds a product |
| POST | `/seller/delete_product` | Seller deletes a product |
| GET | `/seller/products/{seller_id}` | Seller's products + unit codes |

### Cart & Checkout
| Method | Endpoint | Description |
|---|---|---|
| GET | `/cart/{user_id}` | View cart |
| POST | `/add_to_cart` | Add item |
| POST | `/update_cart` | Change quantity |
| POST | `/remove_from_cart` | Remove item |
| POST | `/checkout` | Checkout all cart items |
| POST | `/checkout_item` | Checkout single item |

### Orders
| Method | Endpoint | Description |
|---|---|---|
| GET | `/orders/{user_id}` | Customer's orders |
| GET | `/order/{order_id}` | Order detail + tracking log |
| GET | `/seller/orders/{seller_id}` | Orders for seller's products |
| POST | `/seller/update_item_status` | Seller updates item status |

### Admin
| Method | Endpoint | Description |
|---|---|---|
| GET | `/admin/sellers` | All sellers + approval status |
| GET | `/admin/orders` | All orders |
| POST | `/approve_seller` | Approve a seller |
| POST | `/admin/suspend_seller` | Suspend a seller |
| POST | `/admin/update_item_tracking` | Set tracking number (auto-generates if blank) |
| POST | `/admin/update_order_status` | Update order status |

### AI
| Method | Endpoint | Description |
|---|---|---|
| POST | `/ask` | Natural language → SQL → results via Ollama |

---

## Order Status Flow

```
Placed → Processing → AtWarehouse → Shipped → Delivered
                                             → Cancelled
```

- **Seller** moves items through: Placed → Processing → AtWarehouse
- **Admin** assigns tracking numbers (auto-generates `TRK-2026-XXXXXX`) which sets status to Shipped
- **Customer** can track in real time via My Orders

---

## Notes

- Each product gets individual **serial/unit codes** (e.g. `PHN-00001-A3K2`) generated at listing time
- Serial codes are assigned to orders at checkout — customers see their exact unit codes
- All order status changes are logged with timestamps in `Order_Status_Log` and `Order_Item_Status_Log`
- Seller accounts require **admin approval** before they can log in

---

*© 2026 Kushal — Electronics E-commerce Database System*
