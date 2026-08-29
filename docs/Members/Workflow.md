# PawConnect – Team Workflow

> **Mục đích:** Bức tranh tổng quát về quá trình phát triển PawConnect, giúp TV1, TV2, TV3 biết **mình đang ở đâu – làm gì – phối hợp với ai – bàn giao khi nào**.

---

## 1. Bức tranh tổng thể

```text
                         PAWCONNECT
                             │
                 ┌───────────┼───────────┐
                 │           │           │
                TV1         TV2         TV3
                 │           │           │
          Shop & Service  Community   Platform
                 │        & Adoption   & Security
                 │           │           │
                 └───────────┼───────────┘
                             │
                        INTEGRATION
                             │
                      TEST & FIX BUG
                             │
                       FINAL UI/DEMO
                             │
                           DEPLOY
```

### Quy trình chung của mỗi chức năng

```text
Database → Entity → Repository → Service → Controller/API
                                      ↓
                                   Test
                                      ↓
                               Thymeleaf UI
                                      ↓
                                 Integration
```

---

## 2. Tiến độ & vị trí của từng thành viên

| Giai đoạn                | TV1 – Shop & Service             | TV2 – Community & Adoption         | TV3 – Platform                      |
| ------------------------ | -------------------------------- | ---------------------------------- | ----------------------------------- |
| **Tuần 1–2** Foundation  | Chuẩn bị Entity Shop/Service     | Chuẩn bị Entity Community/Adoption | Project, User/Role, JWT cơ bản      |
| **Tuần 3–5** Development | Product → Cart → Order → Booking | DogProfile → Post → Adoption       | Auth → JWT → Security               |
| **Tuần 5–6** Platform    | Phối hợp Security/API            | Cloudinary + bàn giao Adoption     | WebSocket → Chat + Admin + Security |
| **Tuần 7** Integration   | Test & sửa Shop/Service          | Test & sửa Community/Adoption      | Ghép toàn hệ thống + Security Test  |
| **Tuần 8** Final         | Hoàn thiện Shop/Service UI       | Hoàn thiện Community/Adoption UI   | Auth/Chat/Admin + Deploy            |
| **Cuối kỳ**              | Báo cáo + Demo                   | Báo cáo + Demo                     | Báo cáo + Demo                      |

> Chi tiết công việc của từng thành viên xem tại `docs/Members/TV1.md`, `TV2.md`, `TV3.md`. Tiến độ này bám theo kế hoạch trong tài liệu dự án.

---

## 3. Các điểm phối hợp quan trọng

```text
                 ┌──────────────┐
                 │     TV1      │
                 │ Shop/Service │
                 └──────┬───────┘
                        │
                  User / DogProfile
                        │
                        ▼
                 ┌──────────────┐
                 │     TV3      │
                 │ JWT/Security │
                 │ Chat / Admin │
                 └──────▲───────┘
                        │
                  User / DogProfile
                        │
                 ┌──────┴───────┐
                 │     TV2      │
                 │Community/    │
                 │Adoption      │
                 └──────────────┘
```

**Các điểm cần thống nhất sớm:**

* `User` và `Role` dùng chung toàn hệ thống.
* `DogProfile` của TV2 được TV1 sử dụng cho `ServiceBooking`.
* `AdoptionPost` / `AdoptionApplication` của TV2 là đầu vào cho Chat của TV3.
* JWT/Role của TV3 được áp dụng cho API của TV1 và TV2.
* Database và API contract phải thống nhất trước khi Integration.

---

## 4. Quy trình bàn giao

```text
TV1 ── Shop / Order / Booking ──┐
                                ├──→ TV3 Integration
TV2 ── Community / Adoption ────┘
                                │
                                ▼
                     Integration Test
                                │
                         ┌──────┴──────┐
                         ▼             ▼
                      TV1 Fix       TV2 Fix
                         │             │
                         └──────┬──────┘
                                ▼
                         TV3 Test lại
                                │
                                ▼
                           FINAL DEMO
```

**Nguyên tắc:** người phụ trách module là người sửa lỗi module đó; TV3 chịu trách nhiệm điều phối và kiểm tra tích hợp.

---

## 5. Definition of Done

Một chức năng chỉ được xem là hoàn thành khi:

```text
Code
 ↓
API hoạt động
 ↓
Database đúng
 ↓
Test Pass
 ↓
UI hoạt động (nếu có)
 ↓
Integration không lỗi
 ↓
Cập nhật docs/Members/TVx.md
 ↓
Sẵn sàng bàn giao
```

Không chỉ đánh dấu hoàn thành khi **“đã code xong”**.

---

## 6. Checklist trước Demo

```text
□ Authentication / JWT
□ Phân quyền CUSTOMER / BRANCH_MANAGER / ADMIN
□ Shop / Cart / Order / Booking
□ DogProfile / Community / Adoption
□ Cloudinary
□ WebSocket / Chat
□ Admin
□ Integration Test
□ UI hoàn thiện
□ Không còn lỗi blocker
□ Ba file TV1/TV2/TV3 đã cập nhật
□ Demo flow đã chạy thử
□ Project build/run/deploy thành công
```

### Quy tắc cuối

> **Làm đúng phạm vi → Test → Bàn giao → Tích hợp → Test lại → Demo.**
>
> `Workflow.md` chỉ dùng để nhìn **toàn cảnh**; hướng dẫn triển khai chi tiết nằm trong file nhiệm vụ của từng thành viên.
