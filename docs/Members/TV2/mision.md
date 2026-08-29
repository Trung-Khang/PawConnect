# PawConnect – TV2: Hướng dẫn xây dựng Module Cộng đồng & Nhận nuôi

> **Người phụ trách:** TV2
> **Module:** Community & Adoption
> **Trọng tâm:** Hồ sơ chó, bảng tin cộng đồng, bình luận, like, đăng tin nhận nuôi và xử lý đơn xin nhận nuôi.
> **Tích hợp đặc biệt:** Cloudinary và Chat của TV3.

---

## 1. TV2 phải chịu trách nhiệm những gì?

TV2 xây dựng **trọn vẹn luồng Cộng đồng và Nhận nuôi**, từ `DogProfile` → bài đăng → tương tác → AdoptionPost → AdoptionApplication → bàn giao dữ liệu cho TV3 để tạo Conversation/Chat.

| Nhóm          | Phải xây dựng                                           |
| ------------- | ------------------------------------------------------- |
| Hồ sơ chó     | `DogProfile`                                            |
| Cộng đồng     | `CommunityPost`                                         |
| Bình luận     | `Comment`                                               |
| Like          | `Like`                                                  |
| Nhận nuôi     | `AdoptionPost`                                          |
| Đơn nhận nuôi | `AdoptionApplication`                                   |
| Hình ảnh      | Cloudinary                                              |
| API           | Toàn bộ nhóm API `5.3 – Cộng đồng`                      |
| UI            | Hồ sơ chó, bảng tin, bài đăng, nhận nuôi, đơn nhận nuôi |
| Integration   | Cung cấp AdoptionPost/User để TV3 xây Chat              |
| Test          | Test toàn bộ nghiệp vụ Community & Adoption             |

Đây là toàn bộ phạm vi đã được giao cho thành viên B trong tài liệu gốc.

---

# 2. Vị trí code

```text
src/main/java/com/pawconnect/
│
├── entity/
│   ├── DogProfile.java
│   ├── CommunityPost.java
│   ├── Comment.java
│   ├── Like.java
│   ├── AdoptionPost.java
│   └── AdoptionApplication.java
│
├── repository/
│   ├── DogProfileRepository.java
│   ├── CommunityPostRepository.java
│   ├── CommentRepository.java
│   ├── LikeRepository.java
│   ├── AdoptionPostRepository.java
│   └── AdoptionApplicationRepository.java
│
├── service/
│   └── community/
│
├── controller/
│   └── community/
│
├── dto/
│   └── adoption/
│
└── mapper/
```

Giao diện:

```text
src/main/resources/templates/community/
```

CSS/JS:

```text
src/main/resources/static/css/
src/main/resources/static/js/
```

Test:

```text
src/test/java/com/pawconnect/
```

---

# 3. Quy trình xây dựng tổng thể

TV2 triển khai theo chuỗi:

```text
DogProfile
    ↓
CommunityPost
    ↓
Comment + Like
    ↓
AdoptionPost
    ↓
AdoptionApplication
    ↓
Cloudinary
    ↓
API
    ↓
UI
    ↓
Test
    ↓
Bàn giao AdoptionPost + User cho TV3
```

Không nên làm Chat trong module này. Chat thuộc TV3; TV2 chỉ phải chuẩn bị đúng dữ liệu để TV3 tích hợp.

---

# 4. Bước 1 – DogProfile

Entity:

```text
DogProfile
```

Thông tin theo ERD:

```text
id
userId
name
breed
size
age
weight
gender
vaccinationStatus
imageUrl
description
```

Quan hệ:

```text
User 1 ──── N DogProfile
DogProfile 1 ──── N ServiceBooking
DogProfile 1 ──── N CommunityPost
DogProfile 1 ──── N AdoptionPost
```

TV2 cần xây:

```text
DogProfile
DogProfileRepository
DogProfileService
DogProfileController
DTO nếu cần
```

API:

```text
POST /api/dogs
GET  /api/dogs/my
PUT  /api/dogs/{id}
```

Luồng:

```text
User đăng nhập
   ↓
Tạo hồ sơ chó
   ↓
Upload ảnh nếu có
   ↓
Lưu DogProfile
   ↓
Xem danh sách chó của mình
   ↓
Sửa hồ sơ
```

Phải kiểm tra quyền sở hữu: User chỉ được quản lý DogProfile của mình.

---

# 5. Bước 2 – CommunityPost + Comment + Like

Sau khi DogProfile hoạt động, xây bảng tin cộng đồng.

Entity:

```text
CommunityPost
Comment
Like
```

Quan hệ:

```text
User 1 ──── N CommunityPost
User 1 ──── N Comment
User 1 ──── N Like

CommunityPost 1 ──── N Comment
CommunityPost 1 ──── N Like

DogProfile 1 ──── N CommunityPost
```

Luồng:

```text
User
 ↓
Tạo CommunityPost
 ↓
Có thể gắn DogProfile
 ↓
Upload ảnh
 ↓
Đăng bài
 ↓
Người khác xem bài
 ↓
Comment / Like
```

API:

```text
POST /api/posts
GET  /api/posts?userId=       public
POST /api/posts/{id}/comments
POST /api/posts/{id}/like
```

Các API này thuộc nhóm 5.3 của tài liệu.

Cần xử lý:

* Nội dung bài đăng.
* Hình ảnh.
* User tạo bài.
* DogProfile nếu bài gắn với chó.
* Comment.
* Like.
* Không để User thao tác trái quyền với dữ liệu của người khác.

---

# 6. Bước 3 – AdoptionPost

Đây là phần quan trọng nhất của TV2 vì nó kết nối trực tiếp sang Chat.

Entity:

```text
AdoptionPost
```

Thông tin:

```text
id
userId
dogProfileId
title
description
healthNote
status
createdAt
```

Quan hệ:

```text
User 1 ──── N AdoptionPost
DogProfile 1 ──── N AdoptionPost
AdoptionPost 1 ──── N AdoptionApplication
AdoptionPost 1 ──── N Conversation
```

Luồng phải hoàn chỉnh:

```text
Chủ chó
  ↓
Chọn DogProfile
  ↓
Tạo AdoptionPost
  ↓
Nhập title
  ↓
Nhập description
  ↓
Nhập healthNote
  ↓
Upload ảnh
  ↓
Đăng tin
  ↓
Tin xuất hiện ở danh sách AVAILABLE
```

API:

```text
POST /api/adoptions
GET  /api/adoptions?status=AVAILABLE   public
```

Phải hỗ trợ trạng thái để tin có thể được lọc theo `AVAILABLE`.

---

# 7. Bước 4 – AdoptionApplication

Người dùng khác có thể xin nhận nuôi:

```text
AdoptionPost
      ↓
Apply
      ↓
AdoptionApplication
      ↓
Chủ tin xem đơn
      ↓
Approve / Reject
```

Entity:

```text
AdoptionApplication
```

Thông tin:

```text
id
adoptionPostId
applicantId
message
status
```

API:

```text
POST /api/adoptions/{id}/apply

PUT /api/adoptions/applications/{id}/approve

PUT /api/adoptions/applications/{id}/reject
```

Nghiệp vụ phải kiểm tra:

* Người dùng có thể gửi đơn.
* Đơn gắn đúng AdoptionPost.
* Applicant được xác định đúng.
* Chủ AdoptionPost mới được approve/reject.
* Không cho người ngoài xử lý đơn.
* Status được cập nhật chính xác.

---

# 8. Bước 5 – Cloudinary

TV2 chịu trách nhiệm tích hợp Cloudinary cho cả:

```text
DogProfile
CommunityPost
AdoptionPost
```

Luồng:

```text
Frontend
 ↓
Upload image
 ↓
Backend
 ↓
Cloudinary
 ↓
Nhận image URL
 ↓
Lưu imageUrl vào Entity
 ↓
Frontend hiển thị URL
```

Không lưu trực tiếp file ảnh vào database.

Các Entity cần hỗ trợ URL:

```text
DogProfile.imageUrl
CommunityPost.imageUrl
AdoptionPost.imageUrl
```

Tài liệu gốc xác định rõ TV2 chịu trách nhiệm upload và quản lý ảnh cho ba nhóm dữ liệu này.

---

# 9. Bước 6 – Giao diện

TV2 xây giao diện trong:

```text
src/main/resources/templates/community/
```

Phải có đủ các nhóm giao diện:

```text
Hồ sơ chó
Bảng tin cộng đồng
Chi tiết bài đăng
Tạo bài đăng
Danh sách tin nhận nuôi
Chi tiết tin nhận nuôi
Tạo tin nhận nuôi
Quản lý đơn xin nhận nuôi
```

Có thể tổ chức:

```text
templates/community/
├── dog-profile.html
├── posts.html
├── post-detail.html
├── post-form.html
├── adoption-list.html
├── adoption-detail.html
├── adoption-form.html
└── adoption-applications.html
```

Tên file có thể thay đổi, nhưng **chức năng không được thiếu**. Tài liệu gốc yêu cầu trang hồ sơ chó, bảng tin, chi tiết bài, danh sách/chi tiết nhận nuôi và quản lý đơn.

---

# 10. Bước 7 – Test

Test:

```text
src/test/java/com/pawconnect/
```

Luồng test DogProfile:

```text
Create
→ Get my dogs
→ Update
→ Unauthorized update
```

Community:

```text
Create post
→ Get posts
→ Detail
→ Comment
→ Like
```

Adoption:

```text
Create adoption post
→ Get AVAILABLE
→ Apply
→ View application
→ Approve
→ Reject
```

Cloudinary:

```text
Upload
→ Receive URL
→ Save URL
→ Display image
```

Phải kiểm tra cả trường hợp lỗi:

* User không đăng nhập.
* User sửa DogProfile của người khác.
* User xử lý AdoptionApplication không thuộc tin của mình.
* Dữ liệu bắt buộc bị thiếu.
* Upload ảnh thất bại.

---

# 11. Điểm bàn giao đặc biệt cho TV3

Sau khi AdoptionPost và AdoptionApplication hoạt động, TV2 phải bàn giao cho TV3:

```text
AdoptionPost ID
Owner User ID
Applicant User ID
AdoptionApplication ID
```

TV3 dùng các dữ liệu này để xây:

```text
Conversation
ChatMessage
```

Luồng tích hợp:

```text
TV2:
AdoptionPost
    +
AdoptionApplication
    ↓
TV3:
Conversation
    ↓
ChatMessage
    ↓
WebSocket
```

Theo ERD, `AdoptionPost` có quan hệ với `Conversation`, còn `Conversation` chứa nhiều `ChatMessage`.

TV2 **không tự xây Conversation/ChatMessage**, vì hai entity này thuộc phạm vi TV3.

---

# 12. Tiêu chí nghiệm thu TV2

TV2 hoàn thành khi có thể demo liền mạch:

```text
Đăng nhập
 ↓
Tạo DogProfile
 ↓
Upload ảnh
 ↓
Tạo CommunityPost
 ↓
Comment
 ↓
Like
 ↓
Tạo AdoptionPost
 ↓
Người khác xem tin
 ↓
Gửi AdoptionApplication
 ↓
Chủ tin xem đơn
 ↓
Approve / Reject
```

Ngoài ra:

* Cloudinary hoạt động.
* API đúng endpoint.
* Public API truy cập được không cần authentication theo tài liệu.
* API cần login được bảo vệ.
* User không sửa dữ liệu của User khác.
* Test chính pass.

---

# 13. Bàn giao

TV2 bàn giao cho **TV3**:

```text
DogProfile API
Community API
Adoption API
AdoptionPost Entity
AdoptionApplication Entity
User relationship
Cloudinary configuration
Các API/DTO mà Chat cần
```

TV2 phối hợp với **TV1** khi `ServiceBooking` cần tham chiếu `DogProfile`.

TV2 không được tự thay đổi User/Security của TV3.

---

# 14. Báo cáo bắt buộc

Cập nhật tại:

```text
docs/Members/TV2/report.md
```

Bảng theo dõi:

| Ngày | Luồng đang làm | File/Module | API | Test | Trạng thái | Bàn giao |
| ---- | -------------- | ----------- | --- | ---- | ---------- | -------- |
|      |                |             |     |      | ⬜/🟨/✅     |          |

Mỗi lần hoàn thành phải ghi rõ:

* Đã tạo/sửa file nào.
* Entity nào hoàn thành.
* API nào chạy được.
* Cloudinary đã test chưa.
* Test nào pass.
* Đã bàn giao dữ liệu gì cho TV3.
* Vấn đề còn tồn tại.

---

# 15. Mốc thực hiện

```text
Tuần 1–2
→ hỗ trợ CSDL / khung project

Tuần 3–5
→ Community + Adoption

Tuần 5–6
→ Cloudinary + phối hợp WebSocket

Tuần 7
→ Integration Test + sửa lỗi

Tuần 8
→ UI + báo cáo + demo
```

Tiến độ này bám theo kế hoạch chung trong `PawConnect.docx`.
