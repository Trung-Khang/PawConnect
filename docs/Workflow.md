# PawConnect – Team Workflow

> **Mục đích:** Bức tranh tổng quát giúp TV1, TV2, TV3 biết **mình đang ở đâu – làm gì – phối hợp với ai – bàn giao khi nào**.
> Chi tiết triển khai xem tại `docs/Members/TV1.md`, `TV2.md`, `TV3.md`. Workflow này **không lặp lại** nội dung chi tiết của các file đó.

---

## 1. Sơ đồ 5 giai đoạn

```text
Foundation → Parallel Development → Platform/Realtime → Integration/Test → Final/Demo
   Tuần 1-2          Tuần 3-5            Tuần 5-6           Tuần 7           Tuần 8
```

* **Foundation (1–2):** thiết kế DB (14 entity), dựng khung Spring Boot, cấu hình Spring Security & JWT, Cloudinary.
* **Parallel Development (3–5):** TV1 phát triển Bán chó giống/Sản phẩm & Dịch vụ; TV2 phát triển Nhận nuôi.
* **Platform/Realtime (5–6):** TV3 tích hợp WebSocket Chat (Khách ↔ Admin), Cloudinary, hoàn thiện phân quyền Admin/Manager/Customer.
* **Integration/Test (7):** tích hợp toàn hệ thống, kiểm thử luồng mua hàng – đặt lịch – đăng ký nhận nuôi – chat, sửa lỗi.
* **Final/Demo (8):** hoàn thiện UI Thymeleaf/Bootstrap, báo cáo, chuẩn bị kịch bản demo.

---

## 2. Vị trí của từng thành viên

| Giai đoạn                | TV1 – Thương mại & Dịch vụ     | TV2 – Nhận nuôi              | TV3 – Nền tảng                     |
| ------------------------ | ------------------------------ | ---------------------------- | ---------------------------------- |
| **Foundation (1–2)**     | Hỗ trợ thiết kế DB/khung       | Hỗ trợ thiết kế DB/khung     | User/Role + khung + JWT cơ bản     |
| **Development (3–5)**    | Product → Cart → Order → Booking | DogProfile → Adoption     | Bảo mật API của TV1/TV2            |
| **Platform/Realtime (5–6)** | Cloudinary (chó/sản phẩm)    | Cloudinary + bàn giao Chat   | WebSocket Chat + Admin + Deploy    |
| **Integration/Test (7)** | Test & sửa Shop/Dịch vụ        | Test & sửa Nhận nuôi         | Ghép toàn hệ thống + Test liên mod |
| **Final/Demo (8)**       | Hoàn thiện UI shop/gallery     | Hoàn thiện UI adoption       | Auth/Chat/Admin + Deploy + Demo    |

> **Entity (14):** User, Role, Branch, Category, Product, Order, OrderItem, ServiceType, ServiceBooking, DogProfile, AdoptionPost, AdoptionApplication, Conversation, ChatMessage.

---

## 3. Sơ đồ dependency / bàn giao

```text
   TV1 ── Shop/Order/Booking/Branch ──┐
                                     ├──→ TV3 Integration → System Test → Final Demo
   TV2 ── Adoption (Chat/Customer↔Admin) ─┘
```

**Nguyên tắc:** TV1 + TV2 bàn giao API cho TV3 để bảo vệ JWT và tích hợp toàn hệ thống. Người phụ trách module sửa lỗi module đó; TV3 điều phối và kiểm tra tích hợp.

---

## 4. Checklist trước demo

```text
□ JWT Auth + phân quyền CUSTOMER / BRANCH_MANAGER / ADMIN
□ Shop / Cart / Order / Booking (TV1) — chống trùng lịch, chống race stock
□ DogProfile / Adoption (TV2) — chỉ Admin/Manager đăng tin, Customer nộp đơn
□ Cloudinary (ảnh chó / sản phẩm / adoption)
□ WebSocket STOMP + ChannelInterceptor JWT — Chat Khách ↔ Admin
□ Admin Dashboard (branches / users)
□ Integration Test xuyên module pass
□ UI Thymeleaf/Bootstrap hoàn thiện
□ Báo cáo TV1/TV2/TV3 cập nhật
□ Build / run / deploy thành công
```

---

## 5. Quy trình làm việc & Git

```text
Test → Commit → Push (branch riêng) → Cập nhật docs/Members/TVx.md
```

* Không merge code chưa test vào branch chung.
* Mỗi thành viên làm trên **branch riêng**; document/API contract thống nhất chung.
