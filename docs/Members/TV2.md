# PawConnect – TV2: Nhận nuôi (Store Adoption Program)

> **Người phụ trách:** TV2
> **Phân hệ:** Quản lý chó nhận nuôi & đơn đăng ký

---

## 1. Phạm vi phụ trách

TV2 xây dựng **chương trình Nhận nuôi cửa hàng**: cửa hàng tiếp nhận, quản lý và đăng tải thông tin các chú chó chờ tìm chủ mới; khách hàng lướt xem và gửi đơn đăng ký nhận nuôi; Admin/Quản lý duyệt/từ chối đơn.

TV2 quản lý **3 entity**:

```text
DogProfile, AdoptionPost, AdoptionApplication
```

**Quyền hạn:**
* **ADMIN / BRANCH_MANAGER (cửa hàng)** là bên duy nhất được tạo hồ sơ chó (`DogProfile`) và đăng tin nhận nuôi (`AdoptionPost`).
* **CUSTOMER (khách hàng)** chỉ xem danh sách/chi tiết chó nhận nuôi và nộp đơn (`AdoptionApplication`) tới cửa hàng.
* Không có tính năng "mạng xã hội" (không đăng bài tự do, không bình luận, không like).

---

## 2. Chức năng phải làm

```text
1. Quản lý hồ sơ chó (DogProfile) cho Admin/Quản lý cửa hàng.
2. Đăng tin nhận nuôi (AdoptionPost) — chỉ Admin/Quản lý tạo & cập nhật.
3. Khách hàng xem danh sách tin AVAILABLE và chi tiết chó nhận nuôi.
4. Khách hàng nộp đơn (AdoptionApplication) tới cửa hàng.
5. Khách hàng xem trạng thái đơn của mình.
6. Admin/Quản lý xem toàn bộ đơn (manage) và approve / reject; khi approve → đóng tin.
7. Tích hợp Cloudinary lưu trữ ảnh chó cưng.
8. Bàn giao AdoptionPost/AdoptionApplication cho TV3 để mở Chat (Customer ↔ Admin).
```

---

## 3. Entity & API tương ứng

### Entity 

```text
DogProfile          (id, name, breed, size, age, weight, gender,
                     vaccinationStatus, imageUrl, description)
                    → Hồ sơ chó thuộc quản lý của cửa hàng (không có userId).

AdoptionPost        (id, createdByAdminId, dogProfileId, title,
                     description, healthNote, status, createdAt)
                    → Tin do Admin/Quản lý tạo.

AdoptionApplication (id, adoptionPostId, applicantId, message, status)
                    → Đơn do Khách hàng gửi.
```

### Quan hệ quan trọng 

```text
DogProfile         1 ── N AdoptionPost          (Cascade REMOVE)
AdoptionPost       1 ── N AdoptionApplication   (Cascade REMOVE)
AdoptionPost       1 ── N Conversation          (Conversation do TV3 xây; Cascade REMOVE)
Conversation       1 ── N ChatMessage           (do TV3)
```

### API (không thêm/bớt endpoint)

| Method | Endpoint                          | Quyền               | Chức năng                                      |
| ------ | --------------------------------- | ------------------- | ---------------------------------------------- |
| GET    | `/api/adoptions`                  | Public              | Danh sách chó chờ nhận nuôi (AVAILABLE)        |
| GET    | `/api/adoptions/{id}`             | Public              | Chi tiết chú chó chờ nhận nuôi                  |
| POST   | `/api/adoptions`                  | BRANCH_MANAGER, ADMIN | Admin/Quản lý đăng tin chó chờ nhận nuôi       |
| PUT    | `/api/adoptions/{id}`             | BRANCH_MANAGER, ADMIN | Admin cập nhật thông tin / trạng thái tin       |
| POST   | `/api/adoptions/{id}/apply`       | CUSTOMER              | Khách hàng nộp đơn xin nhận nuôi                |
| GET    | `/api/adoptions/applications/my`  | CUSTOMER              | Xem trạng thái đơn của mình                      |
| GET    | `/api/adoptions/applications/manage` | BRANCH_MANAGER, ADMIN | Xem toàn bộ đơn xin nhận nuôi của khách        |
| PUT    | `/api/adoptions/applications/{id}/approve` | BRANCH_MANAGER, ADMIN | Duyệt đơn cho khách và đóng tin                |
| PUT    | `/api/adoptions/applications/{id}/reject`  | BRANCH_MANAGER, ADMIN | Từ chối đơn xin nhận nuôi                       |

---

## 4. Vị trí file / package cần tạo

Cây thư mục hiện tại được giữ nguyên. Phần Nhận nuôi nằm trong các package sau của Spring Boot:

```text
src/main/java/com/pawconnect/
├── entity/          DogProfile, AdoptionPost, AdoptionApplication
├── repository/      các repository tương ứng
├── controller/community/    API nhận nuôi (adoptions, apply, manage…)
├── service/community/       nghiệp vụ hồ sơ chó, tin nhận nuôi, đơn
├── dto/adoption/    request/response cho adoption
└── mapper/
```

Giao diện (templates):

```text
src/main/resources/templates/community/
├── ... trang lướt xem chó nhận nuôi (Khách)
├── ... form nộp đơn nhận nuôi (Khách)
└── ... quản lý & đăng tin nhận nuôi (Admin)
```

Static:

```text
src/main/resources/static/css/
src/main/resources/static/js/
src/main/resources/static/images/
```

> **Ghi chú:** Package mang tên `community` là tên thư mục hiện có của project; nội dung gồm **Nhận nuôi** (DogProfile/Adoption). Không tạo package thừa khác.

---

## 5. Yêu cầu kỹ thuật quan trọng 

* **Shared Database**: dùng chung CSDL; dữ liệu nhận nuôi gắn theo bối cảnh cửa hàng tập trung.
* **Index**:
  * `idx_adoption_status` trên `AdoptionPost(status)` — tăng tốc hiển thị tin chó chờ nhận nuôi (AVAILABLE).
* **Cloudinary**: lưu ảnh chó cưng qua Cloudinary API; lưu `imageUrl` vào `DogProfile` / `AdoptionPost`. Không lưu file ảnh thô vào DB.
* **Quyền nghiệp vụ**:
  * Chỉ **ADMIN / BRANCH_MANAGER** tạo/đăng/cập nhật `AdoptionPost`.
  * Chỉ **CUSTOMER** nộp đơn; applicant được xác định đúng từ JWT.
  * Chỉ **ADMIN / BRANCH_MANAGER** approve/reject đơn; khi approve → đóng tin (`status` đổi, hết AVAILABLE).
* **Bàn giao dữ liệu cho Chat (TV3)**: cung cấp `AdoptionPost ID`, `adminId` (cửa hàng), `applicantId` (CUSTOMER), `AdoptionApplication ID` để TV3 tạo `Conversation`.

---

## 6. Test cần có

* **Unit test**: tạo/sửa/xóa `DogProfile`, `AdoptionPost`; nộp đơn; approve/reject.
* **Quyền**: CUSTOMER không đăng/sửa tin nhận nuôi; người không đủ quyền không approve/reject; chỉ ADMIN/BRANCH_MANAGER approve/reject.
* **Luồng apply**: khách đăng nhập (CUSTOMER) mới nộp được đơn; applicant đúng người đăng ký.
* **Khi approve**: đơn được duyệt và `AdoptionPost` chuyển trạng thái (đóng tin).
* **Integration test xuyên module** (phối TV3): đăng tin (TV2) → nộp đơn (TV2) → mở Conversation Chat (TV3) → gửi tin nhắn realtime.

---

## 7. Dependency với thành viên khác

* **Nhận từ TV3**: `User`, `Role`, xác thực JWT, phân quyền `@PreAuthorize` cho API nhận nuôi.
* **Bàn giao cho TV3**: `DogProfile`, `AdoptionPost`, `AdoptionApplication`, cấu hình Cloudinary, và thông tin cần thiết để mở Conversation.
* **Phối hợp TV1**: nếu `DogProfile` được dùng trong lịch hẹn (ServiceBooking) cần thống nhất trước khi tích hợp (không gắn `userId` cho DogProfile — thuộc quản lý cửa hàng).

---

## 8. Tiêu chí nghiệm thu (Demo)

```text
Đăng nhập (Admin/Quản lý chi nhánh)
 ↓
Tạo hồ sơ chó (DogProfile) + upload ảnh Cloudinary
 ↓
Đăng tin nhận nuôi (AdoptionPost) → tin xuất hiện AVAILABLE
 ↓
Khách hàng (CUSTOMER) lướt danh sách, xem chi tiết
 ↓
Khách nộp đơn (AdoptionApplication)
 ↓
Admin/Quản lý xem đơn (manage)
 ↓
Approve (đóng tin) hoặc Reject
```

---

## 9. Bàn giao

* TV2 bàn giao cho **TV3**: API `adoptions`, `DogProfile`, `AdoptionPost`, `AdoptionApplication`, cấu hình Cloudinary và thông tin mở `Conversation` cho Chat.
* TV2 nhận feedback từ TV3 về conflict API/entity/security/database.
* Luôn giữ `AdoptionPost`/`AdoptionApplication` đúng phạm vi: chỉ cửa hàng đăng tin, chỉ khách nộp đơn.

---

## 10. Báo cáo & Git

* Báo cáo tiến độ cập nhật ngay trong file này (bảng dưới), theo yêu cầu chung của nhóm.
* Quy trình: **test → commit → push → cập nhật file này** sau mỗi chức năng.
* Làm việc trên **branch riêng** của TV2. Không merge code chưa test vào branch chung.

| Ngày | Chức năng | File/Module | API | Test | Trạng thái | Bàn giao |
| ---- | --------- | ----------- | --- | ---- | ---------- | -------- |
|      |           |             |     |      | ⬜/🟨/✅     |          |

---

## 11. Tiến độ 8 tuần 

```text
Tuần 1–2  → hỗ trợ thiết kế DB + dựng khung project (chung)
Tuần 3–5  → DogProfile + AdoptionPost + AdoptionApplication
Tuần 5–6  → Cloudinary + phối hợp mở Conversation chat (TV3) + bàn giao
Tuần 7    → Integration Test toàn hệ thống + sửa lỗi
Tuần 8    → Hoàn thiện UI adoption + báo cáo + demo
```
