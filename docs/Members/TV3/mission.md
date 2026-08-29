# PawConnect – TV3: Hướng dẫn xây dựng Authentication, Security, Chat, Admin & Integration

> **Người phụ trách:** TV3
> **Module:** Nền tảng dùng chung
> **Trọng tâm:** JWT, phân quyền, WebSocket Chat, Admin, tích hợp TV1 + TV2 và triển khai toàn hệ thống.

---

# 1. TV3 phải chịu trách nhiệm những gì?

TV3 không chỉ làm Login. TV3 là người phụ trách **hạ tầng kết nối toàn bộ PawConnect**.

| Nhóm           | Phạm vi                                        |
| -------------- | ---------------------------------------------- |
| User           | `User`                                         |
| Role           | `Role`: CUSTOMER, BRANCH_MANAGER, ADMIN        |
| Authentication | Register, Login, Refresh Token, `/me`          |
| Security       | JWT + authorization + `@PreAuthorize`          |
| Realtime       | WebSocket + STOMP over SockJS                  |
| Chat           | `Conversation`, `ChatMessage`                  |
| Admin          | Branch, User, kiểm duyệt Post/Adoption         |
| Integration    | Kết nối TV1 + TV2                              |
| Configuration  | Security, WebSocket, Database, Cloudinary      |
| Deploy         | Đóng gói và triển khai                         |
| Test           | Security Test + Integration Test toàn hệ thống |
| UI             | Login/Register, Chat, Admin                    |

Tất cả nội dung trên là phạm vi của thành viên C trong tài liệu gốc.

---

# 2. Vị trí code

```text
src/main/java/com/pawconnect/
│
├── config/
│
├── security/
│
├── controller/
│   ├── auth/
│   ├── chat/
│   └── admin/
│
├── service/
│   ├── auth/
│   ├── chat/
│   └── admin/
│
├── entity/
│   ├── User.java
│   ├── Role.java
│   ├── Conversation.java
│   └── ChatMessage.java
│
├── repository/
│
├── dto/
│   ├── auth/
│   └── chat/
│
├── exception/
│
└── websocket/
```

Giao diện:

```text
src/main/resources/templates/
├── fragments/
├── auth/
├── chat/
└── admin/
```

Test:

```text
src/test/java/com/pawconnect/
```

---

# 3. Quy trình TV3 phải thực hiện

TV3 nên xây theo thứ tự:

```text
User + Role
     ↓
Authentication
     ↓
JWT
     ↓
Security / Authorization
     ↓
TV1 + TV2 API có bảo vệ
     ↓
WebSocket
     ↓
Conversation + ChatMessage
     ↓
Admin
     ↓
Integration
     ↓
Security Test
     ↓
Integration Test
     ↓
Build
     ↓
Deploy
```

Đây là module nền tảng nên TV3 phải phối hợp liên tục với TV1 và TV2, không đợi hai người làm xong toàn bộ mới bắt đầu.

---

# 4. Bước 1 – User & Role

Tạo:

```text
User
Role
```

`User` theo ERD:

```text
id
fullName
email
password
phone
avatarUrl
role
createdAt
```

Role:

```text
CUSTOMER
BRANCH_MANAGER
ADMIN
```

Quan hệ:

```text
User → Role
```

Các module khác sử dụng User ID của TV3 để xác định chủ thể của:

```text
Order
ServiceBooking
DogProfile
CommunityPost
AdoptionPost
Conversation
ChatMessage
```

Vì User là entity dùng chung, TV3 phải thống nhất với TV1 và TV2 trước khi thay đổi field hoặc relationship.

---

# 5. Bước 2 – Authentication

Xây toàn bộ luồng:

```text
Register
 ↓
Password hashing
 ↓
Login
 ↓
Generate Access Token
 ↓
Generate Refresh Token
 ↓
Access protected API
 ↓
Refresh token khi access token hết hạn
 ↓
GET /api/auth/me
```

API bắt buộc:

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh-token
GET  /api/auth/me
```

Đây là toàn bộ nhóm API 5.1.

Phải đảm bảo:

* Email/user hợp lệ.
* Password không lưu plaintext.
* Login thất bại phải trả lỗi phù hợp.
* JWT được tạo đúng.
* Access token và refresh token được xử lý đúng.
* `/me` trả đúng User hiện tại.

---

# 6. Bước 3 – Security & phân quyền

Sau Authentication, bảo vệ toàn bộ hệ thống.

Role:

```text
CUSTOMER
BRANCH_MANAGER
ADMIN
```

TV3 phải cấu hình:

```text
SecurityConfig
JWT authentication
PasswordEncoder
UserDetailsService
JWT validation
Authorization
```

Phân quyền phải bám API trong tài liệu.

Ví dụ:

```text
GET /api/products                  PUBLIC
GET /api/categories                PUBLIC
GET /api/products/{id}             PUBLIC

POST /api/products                 BRANCH_MANAGER / ADMIN
PUT /api/products/{id}             BRANCH_MANAGER / ADMIN
DELETE /api/products/{id}          ADMIN

PUT /api/orders/{id}/status        BRANCH_MANAGER / ADMIN

PUT /api/bookings/{id}/status      BRANCH_MANAGER

GET /api/adoptions?status=AVAILABLE PUBLIC
```

TV3 phải phối hợp TV1/TV2 để bảo đảm API thực tế đúng với quyền đã thống nhất.

`@PreAuthorize` được tài liệu xác định là cơ chế phân quyền sử dụng cho hệ thống.

---

# 7. Bước 4 – WebSocket & Chat

Sau khi Authentication/Security ổn định, xây realtime.

Entity:

```text
Conversation
ChatMessage
```

ERD:

```text
User N ─── Conversation ─── N User
AdoptionPost 1 ─── N Conversation
Conversation 1 ─── N ChatMessage
```

`Conversation`:

```text
id
user1Id
user2Id
adoptionPostId
createdAt
```

`ChatMessage`:

```text
id
conversationId
senderId
content
sentAt
isRead
```

Các field/quan hệ này phải bám ERD gốc.

---

# 8. Bước 5 – Luồng Chat nhận nuôi

TV3 nhận dữ liệu từ TV2:

```text
AdoptionPost ID
Owner ID
Applicant ID
```

Sau đó xây:

```text
AdoptionPost
     ↓
Applicant
     ↓
Conversation
     ↓
ChatMessage
```

WebSocket:

```text
/ws/chat
```

Subscribe:

```text
/topic/conversation/{id}
```

API:

```text
GET /api/conversations/my
GET /api/conversations/{id}/messages
```

Cần đảm bảo:

```text
User A
 ↓
Connect
 ↓
Subscribe conversation
 ↓
Send message
 ↓
Server nhận
 ↓
Save ChatMessage
 ↓
Broadcast
 ↓
User B nhận realtime
```

Không cho User không thuộc Conversation đọc hoặc gửi message vào Conversation đó.

---

# 9. Bước 6 – Giao diện Chat

Tạo:

```text
src/main/resources/templates/chat/
```

Phải có ít nhất:

```text
Danh sách conversation
Khung chat
Lịch sử message
Ô nhập message
Trạng thái đọc nếu triển khai theo field isRead
```

Frontend phải kết nối:

```text
SockJS
STOMP
WebSocket endpoint
```

Giao diện phải thể hiện được chat trực tiếp giữa:

```text
Người đăng tin nhận nuôi
          ↕
Người xin nhận nuôi
```

Đây là đúng mục tiêu realtime của đề tài.

---

# 10. Bước 7 – Admin

Sau Chat, xây khu vực Admin:

```text
src/main/java/com/pawconnect/
├── controller/admin/
├── service/admin/
└── ...
```

UI:

```text
src/main/resources/templates/admin/
```

Phải có:

```text
Dashboard
Users
Branches
Posts
Adoptions
```

Chức năng:

```text
Quản lý chi nhánh
Quản lý người dùng
Kiểm duyệt CommunityPost
Kiểm duyệt AdoptionPost
```

API:

```text
GET /api/admin/branches
GET /api/admin/users

DELETE /api/admin/posts/{id}

DELETE /api/admin/adoptions/{id}
```

Tất cả Admin API phải giới hạn:

```text
ADMIN
```

Các API này thuộc nhóm 5.4 trong tài liệu.

---

# 11. Bước 8 – Tích hợp TV1 + TV2

Đây là phần TV3 phải chịu trách nhiệm cuối cùng.

## Tích hợp với TV1

Kiểm tra:

```text
JWT
 ↓
Product
 ↓
Cart
 ↓
Order
 ↓
Branch Manager
 ↓
Order status
```

và:

```text
JWT
 ↓
Service
 ↓
Booking
 ↓
Branch Manager
 ↓
Booking status
```

## Tích hợp với TV2

Kiểm tra:

```text
JWT
 ↓
DogProfile
 ↓
AdoptionPost
 ↓
AdoptionApplication
 ↓
Conversation
 ↓
WebSocket
 ↓
ChatMessage
```

Đặc biệt kiểm tra dữ liệu dùng chung:

```text
User
DogProfile
AdoptionPost
```

Tài liệu gốc xác định TV3 chịu trách nhiệm kết nối hai module thông qua các entity dùng chung, đặc biệt là `User` và `DogProfile`.

---

# 12. Bước 9 – Integration Test

TV3 phải test hệ thống như một người dùng thực tế chứ không chỉ test từng Controller.

### Scenario 1 – Customer

```text
Register
→ Login
→ JWT
→ Xem Product
→ Cart
→ Order
→ Xem Order
→ Booking
→ Xem Booking
→ Community
→ Adoption
→ Chat
```

### Scenario 2 – Branch Manager

```text
Login
→ JWT
→ Quản lý Product theo quyền
→ Xem/xử lý Order
→ Cập nhật Booking
→ Không truy cập Admin trái phép
```

### Scenario 3 – Admin

```text
Login
→ Admin Dashboard
→ Users
→ Branches
→ Posts
→ Adoptions
```

### Scenario 4 – Adoption Chat

```text
User A tạo AdoptionPost
        ↓
User B Apply
        ↓
Conversation
        ↓
WebSocket
        ↓
User A gửi message
        ↓
User B nhận realtime
        ↓
Lưu ChatMessage
        ↓
Load history
```

---

# 13. Security Test

TV3 phải test các trường hợp:

```text
Không login → protected API bị từ chối

CUSTOMER → không vào Admin API

BRANCH_MANAGER → không dùng chức năng ADMIN

ADMIN → truy cập Admin API

User A → không xem Conversation của User B

User A → không sửa dữ liệu User B

User không có quyền → không approve/reject adoption application
```

Đây là phần bắt buộc vì JWT và phân quyền là một trong các mục tiêu chính của đề tài.

---

# 14. Bước 10 – Build & Deploy

TV3 kiểm tra toàn project:

```text
pom.xml
application.properties
```

Các cấu hình phải được kiểm tra cùng nhau:

```text
Database
JWT
Cloudinary
WebSocket
```

Build:

```text
./mvnw clean test
```

Sau đó:

```text
./mvnw package
```

và kiểm tra ứng dụng có thể chạy.

TV3 chịu trách nhiệm đóng gói và triển khai ứng dụng theo phạm vi được giao trong tài liệu.

---

# 15. Tiêu chí nghiệm thu TV3

TV3 được nghiệm thu khi:

```text
Register ✅
Login ✅
Refresh Token ✅
GET /me ✅
JWT ✅
Role ✅
@PreAuthorize ✅
WebSocket connection ✅
STOMP/SockJS ✅
Conversation ✅
ChatMessage ✅
Chat history ✅
Realtime message ✅
Admin User ✅
Admin Branch ✅
Moderate Post ✅
Moderate Adoption ✅
TV1 integration ✅
TV2 integration ✅
Security test ✅
Integration test ✅
Build ✅
Deploy/run ✅
```

Quan trọng nhất: **TV3 phải chạy được demo toàn bộ hệ thống chứ không chỉ demo riêng Authentication/Chat.**

---

# 16. Bàn giao và phối hợp

TV3 nhận từ TV1:

```text
Shop API
Order API
Booking API
Branch
Role requirements
```

TV3 nhận từ TV2:

```text
DogProfile
Community API
AdoptionPost
AdoptionApplication
Cloudinary
Adoption/Chat integration requirements
```

Sau khi tích hợp, TV3 phải phản hồi lại cho TV1/TV2 nếu có:

```text
API conflict
Entity conflict
Security conflict
Database conflict
DTO conflict
Integration bug
```

Không tự ý sửa nghiệp vụ của module người khác mà không báo lại.

---

# 17. Báo cáo bắt buộc

Cập nhật tại:

```text
docs/Members/TV3/report.md
```

Bảng theo dõi:

| Ngày | Hạng mục | File/Module | API/Feature | Test | Trạng thái | Người liên quan |
| ---- | -------- | ----------- | ----------- | ---- | ---------- | --------------- |
|      |          |             |             |      | ⬜/🟨/✅     |                 |

Đối với mỗi lần tích hợp, phải ghi:

```text
Đã nhận gì từ TV1?
Đã nhận gì từ TV2?
Đã tích hợp gì?
Có conflict không?
Test gì đã chạy?
Bug nào còn tồn tại?
Đã báo cho ai?
Đã fix chưa?
```

---

# 18. Mốc thực hiện

```text
Tuần 1–2
→ User/Role + dựng khung + JWT cơ bản

Tuần 3–5
→ phối hợp bảo mật API của TV1/TV2

Tuần 5–6
→ WebSocket + Chat + Cloudinary integration + hoàn thiện phân quyền

Tuần 7
→ Integration toàn hệ thống + test liên module + sửa lỗi

Tuần 8
→ hoàn thiện UI + deploy + báo cáo + demo
```

Đây là tiến độ tương ứng với kế hoạch trong tài liệu PawConnect.
