# PawConnect - TV3 Báo cáo nhận bàn giao dữ liệu

> Đây là file hướng dẫn nhận bàn giao và mẫu để TV3 cập nhật tiến độ. Chưa ghi nhận JWT, Security hoặc Chat đã hoàn thành nếu chưa có code và test.

## 1. Phạm vi TV3

TV3 phụ trách `User`, `Role`, JWT, Spring Security, Conversation, ChatMessage, Admin dashboard và integration toàn hệ thống.

## 2. Dữ liệu TV2/Data Engineer đã chuẩn bị cho TV3

| File | Dữ liệu | Người sở hữu nghiệp vụ | Người sử dụng | Dùng hiện tại | Bàn giao gần tới |
| --- | --- | --- | --- | --- | --- |
| `data-pipeline/data/curated/reference/roles.csv` | Role | TV3 | Cả nhóm tham chiếu phân quyền | Mapping role bằng `role_code` | TV3 xác nhận role name khớp Spring Security |
| `data-pipeline/data/curated/reference/branches.csv` | Branch | TV1 | TV2/TV3 dùng cho liên kết chi nhánh | `BRANCH_MANAGER` fixture gắn branch | TV3 xác nhận User-Branch mapping |
| `data-pipeline/data/curated/fixtures/users.csv` | User fixture | TV3 sở hữu cách import và credential | TV1/TV2 dùng khóa tham chiếu khi test | Admin, 3 manager, 2 customer demo | TV3 tạo hash password demo an toàn |
| `data-pipeline/data/curated/adoption/dog_profiles.csv` | DogProfile | TV2 | TV2/TV3 | Suy ra branch cho AdoptionPost | TV3 xác nhận `DogProfile.branch_id` hoặc mapping |
| `data-pipeline/data/curated/adoption/adoption_posts.csv` | AdoptionPost | TV2 | TV3 dùng để tích hợp Conversation/Chat | 2 AVAILABLE, 1 CLOSED | TV3 map Conversation tới post/customer/manager |
| `data-pipeline/data/curated/adoption/adoption_applications.csv` | AdoptionApplication | TV2 | TV3 dùng cho authorization và Chat | Đủ PENDING, APPROVED, REJECTED | TV3 xác nhận rule approve/chat |
| `data-pipeline/reports/manual_curated_validation.md` | Báo cáo validation | Cả nhóm | Cả nhóm kiểm tra chất lượng bàn giao | Manual validation PASS | Dùng làm baseline trước integration |

## 3. TV3 cần kiểm tra và xác nhận

- Role name khớp Spring Security: `CUSTOMER`, `BRANCH_MANAGER`, `ADMIN`.
- `BRANCH_MANAGER` có liên kết Branch rõ ràng.
- Credential demo được hash/import an toàn.
- User fixture chỉ dùng `dev`, `test`, `demo`.
- Conversation tham chiếu đúng AdoptionPost, customer và manager/admin.
- Không lưu `TV3_SEED_REQUIRED` như password thật.
- Không tự sửa Adoption enum hoặc stable code mà không báo TV2.

## 4. Hướng dẫn xử lý User fixture và credential

`users.csv` chỉ chứa `password_placeholder=TV3_SEED_REQUIRED`. TV3 cần tự quyết định cách import password demo an toàn, ví dụ sinh hash trong seed nội bộ hoặc tạo account qua service auth. Không đưa password thật vào CSV.

## 5. Hướng dẫn mapping User-Branch

User role `BRANCH_MANAGER` bắt buộc có `branch_code`. `CUSTOMER` và `ADMIN` để trống `branch_code`. Khi import DB, TV3 cần map `branch_code` sang Branch ID thật và dùng mapping này để kiểm tra quyền quản lý dữ liệu theo chi nhánh.

## 6. Dữ liệu Adoption dùng cho Conversation/Chat

TV3 có thể dùng `adoption_posts.csv` và `adoption_applications.csv` để chuẩn bị Conversation:

- Customer lấy từ `applicant_user_key`.
- Manager/admin lấy từ `created_by_user_key` của AdoptionPost hoặc rule TV3 xác nhận.
- AdoptionPost lấy từ `adoption_post_seed_key`.
- Post `CLOSED` với application `APPROVED` không còn application `PENDING`.

## 7. Tiêu chí báo cáo cho mỗi chức năng

Mỗi lần làm chức năng, TV3 cần ghi: file đã sửa, API/chức năng, dữ liệu nhận, security/test, kết quả, đầu ra bàn giao, blocker hoặc contract conflict. Chỉ đánh dấu `COMPLETED` khi có code và test.

## 8. Mẫu progress log

| Ngày | Giai đoạn | Chức năng/API | File đã sửa | Dữ liệu nhận | Security/Test | Kết quả | Bàn giao | Blocker |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  | NOT STARTED |  |  |

Trạng thái dùng chung: `NOT STARTED`, `IN PROGRESS`, `BLOCKED`, `READY FOR REVIEW`, `COMPLETED`.
