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
| 2026-09-08 | Khởi tạo nền tảng dùng chung | Maven/Spring Boot, `User`, `Role`, repositories, H2 dev config | 3 role chuẩn `CUSTOMER` / `BRANCH_MANAGER` / `ADMIN`; email duy nhất | `mvn test` ✅ (5/5 tests pass) | ✅ | TV1, TV2 |
| 2026-09-08 | Xác thực và phân quyền JWT | `config`, `security`, `controller/auth`, `service/auth`, `dto/auth` | `POST /api/auth/register`, `/login`, `/refresh-token`; `GET /me`; BCrypt, stateless JWT, method security | `AuthFlowIntegrationTest` ✅ | ✅ | TV1, TV2 |
| 2026-09-08 | Chat nhận nuôi real-time | `Conversation`, `ChatMessage`, STOMP/SockJS, `controller/chat`, `service/chat` | JWT ở STOMP CONNECT; chỉ 2 thành viên hội thoại được subscribe/send; lịch sử có phân trang | `ConversationServiceIntegrationTest` ✅ | ✅ | TV2 |
| 2026-09-08 | Dashboard tài khoản | `controller/admin`, `service/admin` | `GET /api/admin/users` chỉ `ADMIN`, response không chứa password hash | `AdminUserControllerSecurityTest` ✅ | ✅ | TV1, TV2 |
| 2026-09-14 | Seed User/Role/Branch theo Seed V3 | `User`, `Branch`, `RoleDataInitializer`, `SeedUserImportService` | Map `role_code` / `branch_code` sang entity ID; `BRANCH_MANAGER` bắt buộc thuộc Branch; lưu `seedKey` và cấp `GET /api/admin/users/seed-mapping`; BCrypt password chỉ từ `SEED_USER_PASSWORD` runtime; chỉ chạy seed ở profile `dev` / `test` / `demo` | `SeedUserImportServiceIntegrationTest` ✅; `mvn test` 9/9 ✅ | ✅ | TV1, TV2 |
| 2026-09-14 | Cloudinary dùng chung & bảo mật media | `.env.example`, `config/Cloudinary*`, `service/media`, `controller/media` | Upload/prepare-replace/delete ảnh cho Product/PuppyListing/DogProfile/AdoptionPost; avatar User lưu `secure_url` + `public_id`; Customer chỉ avatar, Manager scope theo Branch, Admin toàn hệ thống; JPEG/PNG/GIF, tối đa 5 MB và 4096 px; xóa mock URL cũ | `CloudinaryMediaServiceTest`, `MediaAccessServiceIntegrationTest`; `mvn test` 9/9 ✅ | ✅ (chưa live upload) | TV1, TV2 |
| 2026-09-26 | Dashboard Branches & kiểm thử quyền WebSocket | `dto/admin`, `service/admin`, `controller/admin`, `websocket`, test | Bổ sung `GET /api/admin/branches` chỉ dành cho `ADMIN`; kiểm thử chặn STOMP `SEND` không có principal và `SUBSCRIBE` conversation không thuộc người dùng | `mvn clean test` 12/12 ✅; 0 failures, 0 errors | ✅ | TV1, TV2 |
| 2026-10-24 | Bảo mật branch/admin, nền mobile, chuẩn bị release | `controller`, `service`, `security`, `templates`, `static`, test; commit `aca4e18` | Admin tạo/sửa/xóa branch có kiểm tra dữ liệu phụ thuộc; manager không sửa booking khác nhánh; chỉ ADMIN xóa Product; bỏ fallback user ID; Bootstrap 5.3.8 và responsive cơ bản | `mvn clean test`: 81 tests, 0 failures, 0 errors, 1 skip; viewport local 320–1024 px | Đã commit phần độc lập; **mục 4 chưa hoàn tất** | TV1 regression; nhóm chốt thanh toán; owner Railway |

Đối với mỗi lần tích hợp, ghi rõ: đã nhận gì từ TV1/TV2 → đã tích hợp gì → có conflict không → test đã chạy → bug còn tồn tại → đã báo ai → đã fix chưa.

| Ngày | Đã nhận | Đã tích hợp | Conflict / blocker | Test | Phản hồi |
| ---- | ------- | ----------- | ------------------ | ---- | -------- |
| 2026-09-08 | Chưa có entity/API đã bàn giao từ TV1/TV2 trên nhánh `TV3` | Chat giữ `adoptionPostId` là khóa tích hợp, không tạo entity hoặc FK giả | Chờ TV2 bàn giao AdoptionPost/Application để chỉ mở Conversation theo đúng trạng thái đơn; chờ TV1 bàn giao Branch để thêm `/api/admin/branches` | Chưa thể chạy integration liên module | Cần TV1/TV2 bàn giao contract/entity trước giai đoạn ghép hệ thống |
| 2026-09-14 | Seed V3: `reference/roles.csv`, `reference/branches.csv`, `fixtures/users.csv`; Cloudinary SDK có sẵn trên `main`, không có credential hoặc ảnh được cấp | Bổ sung stable `Branch.code`, quan hệ `User–Branch`, mapping `seed_key → User.id`, API media chuẩn; `User.avatarUrl/avatarPublicId` do TV3 lưu; mock URL Cloudinary đã bị xóa | Product/PuppyListing (TV1) và DogProfile/AdoptionPost (TV2) chưa có trường/API lưu `public_id`; Branch phải được TV1 import có `code` trước User seed. Đây là dependency bàn giao, không tự sửa entity nghiệp vụ của TV1/TV2 | `mvn test` 9/9 ✅ | Đã phản hồi TV1/TV2: gọi API media, lưu `secureUrl` + `publicId` vào record sở hữu trước; chỉ sau DB update thành công mới gọi DELETE ảnh cũ. Cloudinary live chưa test vì không có credential/tài sản ảnh được cấp |
| 2026-09-26 | `Branch` dùng chung đã có trên nhánh `TV3`; chưa có entity/API Adoption của TV2 được bàn giao vào nhánh này | Hoàn thành API Dashboard Branches của TV3 và bổ sung test phân quyền WebSocket ở mức interceptor | Luồng `Customer apply (TV2) → Conversation (TV3)` cần TV2 chủ động gọi/handoff theo contract sau khi tạo application. TV3 không tự sửa `AdoptionService` hoặc thêm entity TV2. | `mvn clean test` 12/12 ✅ | Cần TV2 xác nhận contract/kích hoạt mở Conversation; sau bàn giao TV3 sẽ chạy integration test liên module. |
| 2026-10-24 | Merge `main` vào nhánh `TV3` hiện có (`d01314e`); nhận source shop/booking/adoption/auth mới, không tạo nhánh khác | Bổ sung admin Branch CRUD, booking cross-branch guard, rule Product delete ADMIN, Bootstrap/mobile và runbook | Checkout/bill/state/payment chưa thể code đúng vì nhóm chưa chốt thanh toán và hủy/hoàn tiền; Railway/staging và migration V4.5/V4.6 chưa được owner xác minh; TV1 chưa ký regression | H2 `clean test` 81 ca: 0 failure/error, 1 skip; browser local viewport; không coi là Railway pass | Cần nhóm chốt chính sách; owner hạ tầng cấp staging/backup; TV1 chạy regression theo mục 5 kế hoạch tổng hợp. |

---

## 10.1. Bàn giao TV3 — Seed V3 và Cloudinary (2026-09-14)

**Commit code:** `4ad99e3` — `feat(tv3): add Seed V3 user mapping and secure media service`.

### CSV/dataset đã sử dụng

- `data-pipeline/data/seed/v3/reference/roles.csv`: 3 Role chuẩn.
- `data-pipeline/data/seed/v3/reference/branches.csv`: `branch_code` để liên kết Branch Manager.
- `data-pipeline/data/seed/v3/fixtures/users.csv`: 6 User bootstrap.
- Không sửa Seed V3 và không dùng `password`, hash, token, Cloudinary secret hay URL ảnh giả từ CSV.

### Entity, mapping và API đã bàn giao

- `Role`: xác nhận enum `CUSTOMER`, `BRANCH_MANAGER`, `ADMIN`; import theo `role_code` → `Role.id`.
- `Branch`: thêm stable `code`; `branch_code` → `Branch.id`. Branch phải được TV1 import trước User seed.
- `User`: thêm `seedKey`, quan hệ tùy chọn `branch`, `avatarPublicId`; `seed_key` → `User.id` được trả cho module khác qua `GET /api/admin/users/seed-mapping` (ADMIN).
- Credential User seed chỉ được BCrypt hash tại runtime từ `SEED_USER_PASSWORD`; seed chỉ chạy khi `SEED_USERS_ENABLED=true` và profile là `dev`, `test` hoặc `demo`.
- Media: `POST /api/media/{PRODUCT|PUPPY_LISTING|DOG_PROFILE|ADOPTION_POST}`, `POST /api/media/{type}/replace`, `DELETE /api/media/{type}`, `PUT|DELETE /api/media/avatar`.
- Upload trả `secureUrl` và `publicId`. Customer chỉ thay avatar của chính mình; Branch Manager bị giới hạn thư mục Branch; Admin quản lý toàn bộ thư mục `pawconnect/`.

### File code chính

- `config/CloudinaryConfiguration`, `CloudinaryProperties`, `SeedUserInitializer`, `SeedUserProperties`.
- `service/seed/SeedUserImportService`, `service/media/*`, `controller/media/MediaController`.
- `entity/User`, `entity/Branch`, repositories và `controller/admin/AdminUserController`.
- `.env.example` chỉ chứa tên biến môi trường; service mock trả URL Cloudinary giả đã bị xóa.

### Hoàn thành, giới hạn và việc tiếp theo

- Hoàn thành: mapping Role/Branch/User, User ID mapping, runtime BCrypt credential, API/config Cloudinary và kiểm soát quyền upload/xóa/thay ảnh; xác thực JPEG/PNG/GIF, tối đa 5 MB và 4096 px.
- Đã test: `mvn test` — 9 tests pass, 0 failures, 0 errors; gồm auth, admin security, chat, Seed User và media.
- Chưa thực hiện live upload Cloudinary vì chưa có credential và asset được cấp phép; không commit secret hoặc upload thử.
- TV1/TV2 còn phải thêm/lưu `secure_url` và `public_id` cho record nghiệp vụ do họ sở hữu. Luồng thay ảnh: upload ảnh mới → lưu metadata mới vào database thành công → gọi DELETE với `previousPublicId`; không sửa `Seed V3`.

## 11. Tiến độ 8 tuần 

```text
Tuần 1–2  → User/Role + dựng khung + cấu hình JWT cơ bản
Tuần 3–5  → phối hợp bảo mật API của TV1/TV2
Tuần 5–6  → WebSocket Chat (Khách ↔ Admin) + Cloudinary + hoàn thiện phân quyền
Tuần 7    → Integration toàn hệ thống + test liên module + sửa lỗi
Tuần 8    → Hoàn thiện UI + deploy + báo cáo + demo
```

## 12. Bàn giao tăng dần theo kế hoạch tổng hợp (24/10/2026)

**Nguồn việc hiện hành:** [KeHoach_TongHop_Va_BanGiao.md](../Project/KeHoach_TongHop_Va_BanGiao.md), mục 4. Những dấu ✅ trong checklist lịch sử ở mục 8 của tài liệu này **không thay thế nghiệm thu staging/release hiện tại**. Chưa xác nhận TV3 hoàn tất mục 4.

**Commit code đã kiểm thử:** `aca4e18` trên nhánh `TV3` sẵn có. Merge nền `main` vào `TV3`: `d01314e`. Không tạo nhánh tích hợp mới, không merge ngược vào `main`, chưa push remote trong lần bàn giao này.

### Đã làm và tác dụng nghiệp vụ

- `POST/PATCH/DELETE /api/admin/branches` chỉ ADMIN. Mã branch cố định sau tạo; xóa bị từ chối nếu đã có tài khoản, sản phẩm, puppy listing, booking, order, dog profile hoặc conversation liên quan. Giao diện `/admin/branches` hỗ trợ quản trị chi nhánh; khách/manager không thấy thao tác quản trị và API chặn 403.
- Manager chỉ cập nhật booking thuộc chi nhánh của mình; ADMIN có thể cập nhật mọi chi nhánh. Rule xóa Product thống nhất với đặc tả: chỉ ADMIN ở controller/service/UI. Bỏ fallback user ID `1` khi thiếu principal JWT, tránh cart/order gắn nhầm tài khoản seed.
- Dùng Bootstrap 5.3.8 cố định qua fragment dùng chung, class thực tế ở form, bảng và lưới footer. Cải thiện header/shop/modal/booking/auth/bảng admin cho màn nhỏ, giữ CSS và màu PawConnect.
- Viết [schema_review_tv3.md](../Project/schema_review_tv3.md) để đối chiếu Product/PuppyListing/DogProfile, FK/index/cascade thực tế và nợ snapshot OrderItem; viết [release_runbook.md](../Project/release_runbook.md) cho backup, migration, deploy, smoke và rollback. Không thay đổi schema và không chạy migration.

### Kiểm thử/evidence đã có

- Chạy `mvn clean test` nhiều vòng; vòng cuối 24/10: **81 tests, 0 failures, 0 errors, 1 skipped** (MySQL integration cần DB riêng). Test mới bao gồm quyền admin branch CRUD, từ chối xóa branch có Order, booking sai/đúng chi nhánh và ADMIN, Product delete ADMIN, ngăn thiếu JWT thành user ID 1. `node --check` cho JS mới/sửa và `git diff --check` đều qua.
- Chạy JAR local H2 trên cổng 18080; `/shop`, `/auth/login`, `/admin/users`, `/admin/branches` trả HTML 200. Browser headless kiểm tra chiều rộng 320, 375, 390, 768, 1024 px: không tràn ngang ở bốn trang này sau sửa. `/service` và `/profile` được đo layout khi tắt JS để tránh redirect do không có tài khoản thử; `/adoptions` cũng đã đo viewport. Đây là kiểm tra layout local, **không phải** E2E các thao tác đã đăng nhập hay ảnh chứng cứ thiết bị thật.
- Không gửi SMTP OTP thật, không upload/xóa Cloudinary thật, không đo WSS/Railway hay áp migration trên shared DB. Không có ảnh desktop/mobile đã được nhóm duyệt hoặc TV1 regression pass, nên không đánh dấu các tiêu chí ấy hoàn thành.

### Việc còn mở / cần phối hợp

1. **Nhóm chốt trước khi code checkout:** COD hay thanh toán mô phỏng; khách được hủy tới trạng thái nào, có/không hoàn tiền và cách xử lý sau xác nhận. Sau quyết định mới làm checkout, idempotency/stock race, snapshot bill, state/audit, một menu Đơn của tôi và test phân quyền/đồng thời. Không tự suy diễn nghiệp vụ này.
2. Owner hạ tầng xác nhận Railway/staging, trạng thái migration V4.5/V4.6, backup, biến môi trường theo [production_configuration.md](../Project/production_configuration.md), rồi mới deploy/smoke HTTPS/WSS/SMTP/Cloudinary. Plugin Railway đã được gợi ý kết nối nhưng chưa có phiên truy cập; không commit secret hay reseed DB.
3. TV1 chạy checklist regression mục 5 của kế hoạch tổng hợp, gồm shop, adoption, auth và chat **một customer một inbox**. Chỉ đánh dấu hoàn tất mục 4 khi có evidence/ảnh desktop-mobile, nghiệm thu checkout, staging và xác nhận TV1, không có lỗi P0/P1.

**Rollback code:** trên nhánh TV3, sau khi nhóm review có thể `git revert aca4e18`; không cần rollback database cho commit này vì không có migration. Khi đã deploy sau này, thực hiện quy trình backup/redeploy trong runbook, không tự sửa shared DB.

## 13. Bàn giao checkout, thanh toán và biên lai (24/10/2026)

**Trạng thái:** code đã được người phụ trách duyệt để commit/push: `bacd628` trên nhánh `TV3`, đã push `origin/TV3` ngày 24/10/2026; **chưa deploy hoặc áp migration trên staging/Railway**. Mục 4 của kế hoạch tổng hợp chưa được đánh dấu hoàn tất vì còn nghiệm thu môi trường và TV1 regression. Mục 12 phía trên là lịch sử của commit `aca4e18`; quyết định nghiệp vụ checkout dưới đây thay thế blocker chính sách cũ ở thời điểm đó.

### Quyết định nghiệp vụ và phạm vi đã làm

- Theo quyết định nghiệp vụ đã được người phụ trách xác nhận, bản này dùng **thanh toán mô phỏng thành công lúc xác nhận đơn**, không kết nối ngân hàng/cổng thanh toán. Khách chỉ được hủy khi đơn còn `PAID`, trước khi chi nhánh xác nhận; thao tác hủy chuyển đơn sang `CANCELLED`, thanh toán sang `REFUNDED` và hoàn tồn kho trong cùng transaction. Sau `CONFIRMED`, khách không tự hủy/hoàn tiền. Giao diện chỉ hiển thị phương thức/trạng thái cần thiết, không chèn ghi chú “giao dịch demo”; tài liệu này giữ rõ giới hạn kỹ thuật để không nhầm với tiền thật.
- Nút **Mua ngay** ở `/shop` đưa vào `/checkout`; khách thấy item, số lượng, đơn giá, chi nhánh, tổng tiền, nhập người nhận/số điện thoại/địa chỉ và xác nhận. Backend lấy người mua từ JWT, tính lại giá/tổng từ catalog, kiểm tra một chi nhánh, kiểm tra tồn kho và dùng `idempotencyKey` để chặn tạo đơn trùng khi xác nhận lại.
- Lưu snapshot tên, đơn giá và số lượng từng item; tên/địa chỉ/số điện thoại chi nhánh, tên người mua và thông tin nhận hàng. `/orders/{id}/receipt` hiển thị mã đơn, ngày giờ, khách/người nhận/liên hệ/địa chỉ, chi nhánh, từng mặt hàng, số lượng, đơn giá, thành tiền, tổng tiền, phương thức/trạng thái thanh toán và trạng thái đơn. Có nút in A4 bằng CSS print; gọi là **biên lai đơn hàng**, không nhận là hóa đơn VAT. Thông tin lịch sử không đổi khi sửa tên/giá catalog sau mua.
- Một mục menu **Đơn của tôi** dẫn đến `/orders`, gồm tab mua hàng và tab nhận nuôi dùng API hiện có. Khách xem/hủy đơn còn hợp lệ; manager xử lý đúng chi nhánh; admin xem toàn hệ thống. Mỗi chuyển trạng thái hợp lệ được ghi audit actor, trước/sau, thời gian và lý do; chi nhánh có thể hủy trước xác nhận với lý do. Truy cập biên lai qua API kiểm tra chủ đơn/manager đúng chi nhánh/admin, không dựa vào việc giấu đường dẫn.
- Chuẩn bị `data-pipeline/migrations/V4_7_0__checkout_receipt_mysql.sql` cho MySQL: cột snapshot/payment/idempotency, unique key và audit table; backfill dữ liệu lịch sử khi có thể. **Chưa áp dụng lên Railway/shared DB**; cần backup, review schema và owner phê duyệt theo runbook. H2 local dùng `ddl-auto=update` không chứng minh migration MySQL đã chạy được.

### Kiểm thử và bằng chứng local

| Mục tiêu | Role/dữ liệu, bước kiểm tra | Kết quả và bằng chứng | Giới hạn |
| --- | --- | --- | --- |
| API checkout/biên lai | Customer, customer khác, manager đúng/sai chi nhánh, admin; mua product/puppy, đọc biên lai, hủy, chuyển trạng thái, sửa catalog rồi đọc lại | `CheckoutReceiptIntegrationTest`: quyền `401/403`, snapshot, hoàn tồn, payment state và nhánh đúng; `CartAndOrderServiceTest`, `OrderServiceAuthorizationTest` được cập nhật | Chạy H2, không phải Railway/MySQL |
| Đồng thời | Hai khách tranh item stock 1; hai lần xác nhận cùng cart/key; hai staff cùng xác nhận một đơn | `CheckoutConcurrencyTest`: chỉ một đơn/stock đúng; idempotent retry; một transition/audit thành công | Không có payment callback thực vì không tích hợp gateway |
| Luồng trình duyệt | Customer local: bấm Mua ngay → checkout → xác nhận → biên lai → hard refresh → Đơn của tôi → tab nhận nuôi; đo 320/375/390/768/1024/1280 px | Headless Chrome: không tràn ngang; biên lai khớp tổng và item server; đã tạo `target/checkout-desktop.png`, `checkout-mobile.png`, `receipt-desktop.png`, `receipt-mobile.png`, `receipt-a4.pdf` (artifact local, không đưa vào Git) | Chưa xác nhận thiết bị thật, TV1 regression, console/network toàn site |
| Build/tĩnh | `mvn test`, `mvn -q package -DskipTests`, `node --check` JS sửa/mới, `git diff --check` | Vòng test cuối: **93 tests, 0 failures, 0 errors, 1 skipped**; package và browser local đã qua | Không thay cho staging smoke |

### Việc cần duyệt/nghiệm thu trước khi gọi là hoàn tất

1. Đã commit/push đúng nhánh `TV3` theo duyệt của người phụ trách; không tạo nhánh khác hay merge vào `main`. Nhóm vẫn cần review/đối chiếu nghiệp vụ trên bản được triển khai trước khi nghiệm thu.
2. Owner DB đối chiếu schema MySQL hiện tại, backup và duyệt migration V4.7; owner staging chạy/smoke checkout, auth, quyền, HTTPS/WSS, SMTP, Cloudinary và rollback. Không tự áp migration/reseed database dùng chung.
3. TV1 chạy regression mục 5 kế hoạch tổng hợp, gồm shop/adoption/chat một khách một inbox, responsive trên thiết bị thật và lỗi console/network; cập nhật evidence/lỗi theo mẫu. Chỉ khi có xác nhận này cùng commit/evidence mới đánh dấu mục 4 hoàn tất.

**Rollback dự kiến:** trước khi áp migration chỉ cần hoàn nguyên code bằng `git revert bacd628` trên nhánh được phép. Nếu đã triển khai, thực hiện backup/rollback DB và redeploy theo `docs/Project/release_runbook.md`; không tự xóa dữ liệu hoặc chạy downgrade trên shared DB.
