# PawConnect – TV3: Nền tảng, Bảo mật & Real-time Chat

> **Người phụ trách:** TV3
> **Phân hệ:** Nền tảng dùng chung, Bảo mật & Real-time

---

## 1. Phạm vi phụ trách

TV3 xây dựng **hạ tầng kết nối toàn bộ hệ thống**: tài khoản/phân quyền, xác thực JWT, bảo mật WebSocket Chat real-time (Khách ↔ Admin về nhận nuôi), Dashboard Admin toàn hệ thống và triển khai ứng dụng.

TV3 quản lý **4 entity**:

```text
User, Role, Conversation, ChatMessage
```

**Vai trò:**

```text
CUSTOMER, BRANCH_MANAGER, ADMIN
```

---

## 2. Chức năng phải làm

```text
1. User & Role (CUSTOMER, BRANCH_MANAGER, ADMIN).
2. Đăng ký / Đăng nhập / Refresh Token / Me (JWT).
3. Spring Security + JWT, phân quyền @PreAuthorize cho toàn bộ API TV1 + TV2.
4. WebSocket STOMP over SockJS + ChannelInterceptor xác thực JWT ở STOMP CONNECT.
5. Chat real-time: Khách hàng ↔ Admin cửa hàng về đơn nhận nuôi (Conversation + ChatMessage).
6. Dashboard Admin toàn hệ thống (/api/admin/branches, /api/admin/users).
7. Tích hợp & triển khai toàn hệ thống.
```

---

## 3. Entity & API tương ứng

### Entity 

```text
User        (id, fullName, email, password, phone, avatarUrl, role, createdAt)
Role        (id, name — CUSTOMER, BRANCH_MANAGER, ADMIN)
Conversation (id, customerId, adminId, adoptionPostId, createdAt)
ChatMessage (id, conversationId, senderId, content, sentAt, isRead)
```

### Quan hệ quan trọng 

```text
User 1 ── N Order                  (Order do TV1)
User 1 ── N ServiceBooking         (do TV1)
AdoptionPost 1 ── N Conversation   (AdoptionPost do TV2)
Conversation 1 ── N ChatMessage
```

### API 

**Authentication:**

| Method | Endpoint                | Quyền         | Chức năng                              |
| ------ | ----------------------- | ------------- | -------------------------------------- |
| POST   | `/api/auth/register`    | Public        | Đăng ký CUSTOMER                        |
| POST   | `/api/auth/login`       | Public        | Đăng nhập → JWT Access & Refresh Token  |
| POST   | `/api/auth/refresh-token` | Public       | Cấp mới Access Token từ Refresh Token  |
| GET    | `/api/auth/me`          | Authenticated | Thông tin tài khoản hiện tại            |

**Admin (Dashboard toàn hệ thống):**

| Method | Endpoint             | Quyền | Chức năng                          |
| ------ | -------------------- | ----- | ---------------------------------- |
| GET    | `/api/admin/branches`| ADMIN | Danh sách chi nhánh toàn hệ thống  |
| GET    | `/api/admin/users`   | ADMIN | Danh sách tài khoản & phân quyền   |

**Chat (tùy triển khai):**

Không nằm trong bảng API chính thức, nhưng cần thiết cho Chat real-time:

```text
GET /api/conversations/my
GET /api/conversations/{id}/messages
```

---

## 4. Vị trí file / package cần tạo

Cây thư mục hiện tại được giữ nguyên. TV3 phát triển trong các package sau:

```text
src/main/java/com/pawconnect/
├── config/                 SecurityConfig, WebSocketConfig
├── security/               JwtUtil, JwtAuthenticationFilter, Security…
├── controller/auth/        register, login, refresh, me
├── controller/chat/        conversation, message
├── controller/admin/       branches, users, dashboard
├── service/auth/           đăng ký/đăng nhập/refresh/me
├── service/chat/           conversation, chat message
├── service/admin/          dashboard admin
├── entity/                 User, Role, Conversation, ChatMessage
├── repository/
├── dto/auth/
├── dto/chat/
├── exception/
└── websocket/              ChannelInterceptor xác thực JWT
```

Giao diện (templates):

```text
src/main/resources/templates/auth/      Đăng ký / Đăng nhập
src/main/resources/templates/chat/      Khung chat real-time
src/main/resources/templates/admin/     Dashboard Admin hệ thống
src/main/resources/templates/fragments/ Layout dùng chung
```

Static:

```text
src/main/resources/static/css/
src/main/resources/static/js/
src/main/resources/static/images/
```

---

## 5. Yêu cầu kỹ thuật quan trọng 

* **Shared Database**: toàn hệ thống dùng chung một CSDL; dữ liệu nghiệp vụ phân biệt theo `branch_id` (do TV1 quản lý), `User`/`Role` dùng chung.
* **JWT Authentication + Authorization**: đăng nhập không trạng thái; access token & refresh token; phân quyền bằng Role qua `@PreAuthorize`.
* **WebSocket STOMP over SockJS**: cấu hình real-time giữa Khách hàng và Admin.
* **ChannelInterceptor xác thực JWT ở STOMP CONNECT**:
  * Trình duyệt không gửi được HTTP Header Bearer Token khi mở WebSocket.
  * `ChannelInterceptor` đăng ký vào `clientInboundChannel`, chặn tin STOMP CONNECT, trích `Authorization` header (Bearer Token), xác thực qua `JwtUtil`.
  * Token hợp lệ → gán `Authentication` vào `StompHeaderAccessor.setUser(Principal)`.
  * Bảo vệ kênh riêng `/topic/conversation/{id}`.
* **Chat tập trung vào Customer ↔ Admin/Manager liên quan đến Adoption** (không chat mở giữa người dùng với nhau).
* **Cloudinary**: cấu hình tích hợp cho ảnh chó / sản phẩm / adoption (dùng chung bởi TV1, TV2).
* **Triển khai**: đóng gói và chạy ứng dụng.
* **Index**: `idx_chat_conversation_time` trên `ChatMessage(conversation_id, sent_at DESC)` — truy xuất lịch sử chat nhanh.

---

## 6. Test cần có

* **Security test**:
  * Không đăng nhập → API protected bị từ chối.
  * CUSTOMER không vào Admin API.
  * BRANCH_MANAGER không dùng chức năng ADMIN.
  * ADMIN truy cập Admin API.
  * User không xem được Conversation / dữ liệu của User khác.
  * User không có quyền không approve/reject adoption application.
* **WebSocket test**: kết nối STOMP; xác thực JWT qua ChannelInterceptor; Khách ↔ Admin gửi/nhận tin realtime; lưu ChatMessage; load history; từ chối token sai.
* **Integration test xuyên module** (bắt buộc):
  * Customer: Register → Login → Product (TV1) → Cart → Order → Booking → Adoption (TV2) → Chat (TV3).
  * Branch Manager: Login → quản lý Product/Order/Booking → không vào Admin trái phép.
  * Admin: Login → Dashboard → Users → Branches → Adoptions.
  * Adoption Chat: Admin tạo AdoptionPost (TV2) → Customer apply (TV2) → Conversation (TV3) → người nhận realtime → lưu & load history.

---

## 7. Dependency với thành viên khác

* **TV3 là module trung tâm**: mọi API của TV1 và TV2 đều được TV3 bảo vệ bằng JWT.
* **Nhận từ TV1**: `Shop/Order/Booking/Branch` API, `Role` requirements.
* **Nhận từ TV2**: `DogProfile`, `AdoptionPost`, `AdoptionApplication`, Cloudinary, thông tin mở Conversation.
* **Bàn giao cho TV1/TV2**: `User`, `Role`, xác thực JWT, phân quyền, cấu hình chung.
* Khi gặp conflict API/entity/security/database → phản hồi lại TV1/TV2, không tự ý sửa nghiệp vụ module khác.

---

## 8. Tiêu chí nghiệm thu (Demo)

```text
Register / Login ✅
JWT + Refresh Token ✅
Phân quyền CUSTOMER / BRANCH_MANAGER / ADMIN ✅
@PreAuthorize ✅
WebSocket STOMP over SockJS ✅
ChannelInterceptor xác thực JWT (CONNECT) ✅
Conversation / ChatMessage ✅
Chat history + realtime (Khách ↔ Admin) ✅
Admin User / Branch ✅
TV1 integration ✅
TV2 integration ✅
Security test ✅
Integration test ✅
Build / run / deploy ✅
```

**Quan trọng nhất:** TV3 phải chạy được demo **toàn bộ hệ thống** (mua hàng – đặt lịch – đăng ký nhận nuôi – chat), không chỉ demo riêng Authentication/Chat.

---

## 9. Bàn giao

* TV3 nhận toàn bộ API của TV1 + TV2 và tích hợp thành một hệ thống hoàn chỉnh.
* TV3 cấu hình JWT, phân quyền, WebSocket, Cloudinary và triển khai.
* TV3 phản hồi cho TV1/TV2 về conflict API/entity/security/database khi phát hiện.

---

## 10. Báo cáo & Git

* Báo cáo tiến độ cập nhật ngay trong file này (bảng dưới).
* Quy trình: **test → commit → push → cập nhật file này** sau mỗi chức năng.
* Làm việc trên **branch riêng** của TV3. Không merge code chưa test vào branch chung.

| Ngày | Hạng mục | File/Module | API/Feature | Test | Trạng thái | Người liên quan |
| ---- | -------- | ----------- | ----------- | ---- | ---------- | --------------- |
| 2026-09-08 | Khởi tạo nền tảng dùng chung | Maven/Spring Boot, `User`, `Role`, repositories, H2 dev config | 3 role chuẩn `CUSTOMER` / `BRANCH_MANAGER` / `ADMIN`; email duy nhất | `git diff --check` ✅; chờ môi trường có JDK/Maven để chạy build | 🟨 | TV1, TV2 |
| 2026-09-08 | Xác thực và phân quyền JWT | `config`, `security`, `controller/auth`, `service/auth`, `dto/auth` | `POST /api/auth/register`, `/login`, `/refresh-token`; `GET /me`; BCrypt, stateless JWT, method security | Đã thêm `AuthFlowIntegrationTest`; chờ môi trường có JDK/Maven để chạy | 🟨 | TV1, TV2 |

Đối với mỗi lần tích hợp, ghi rõ: đã nhận gì từ TV1/TV2 → đã tích hợp gì → có conflict không → test đã chạy → bug còn tồn tại → đã báo ai → đã fix chưa.

---

## 11. Tiến độ 8 tuần 

```text
Tuần 1–2  → User/Role + dựng khung + cấu hình JWT cơ bản
Tuần 3–5  → phối hợp bảo mật API của TV1/TV2
Tuần 5–6  → WebSocket Chat (Khách ↔ Admin) + Cloudinary + hoàn thiện phân quyền
Tuần 7    → Integration toàn hệ thống + test liên module + sửa lỗi
Tuần 8    → Hoàn thiện UI + deploy + báo cáo + demo
```
