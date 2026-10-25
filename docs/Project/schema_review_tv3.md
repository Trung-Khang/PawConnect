# Đối chiếu mô hình dữ liệu theo source — TV3, 24/10/2026

Tài liệu này mô tả **entity/JPA trong source hiện tại**, không khẳng định schema Railway đã được migrate. Đặc tả cũ có `Product.isBreedingDog` không còn đúng: `Product` là hàng hóa/phụ kiện; `PuppyListing` là chó thương mại; `DogProfile` là chó nhận nuôi. Ba loại này không được nhập chung.

## Quan hệ chính

```text
Branch ──< Product ──< CartItem / OrderItem
       ├─< PuppyListing ──< CartItem / OrderItem
       ├─< ServiceBooking
       ├─< DogProfile ──< AdoptionPost
       ├─< Order
       ├─< Conversation
       └─< User (BRANCH_MANAGER)

Cart ──< CartItem
Order ──< OrderItem
Conversation ──< ChatMessage
```

- `CartItem` và `OrderItem` có hai FK nullable tới `Product` hoặc `PuppyListing`. Service hiện kiểm tra item hợp lệ, nhưng **chưa có check constraint DB đảm bảo đúng một FK**; phải đưa vào migration có review nếu nhóm chốt.
- `Order.userId` và `ServiceBooking.userId` hiện là scalar, không phải FK tới `User`. Đây là nợ integrity, cần xét tương thích dữ liệu cũ trước khi đổi.
- `Order → OrderItem` và `Cart → CartItem` có `cascade=ALL`, `orphanRemoval=true`. `Branch` không cascade xóa dữ liệu nghiệp vụ. API admin chỉ xóa branch khi không còn User/Product/PuppyListing/Booking/Order/DogProfile/Conversation liên quan; DB FK vẫn là lớp bảo vệ cuối.
- `OrderItem.price` đã là snapshot đơn giá, nhưng tên item chưa snapshot; API lịch sử hiện đọc tên từ catalog, nên sửa/xóa catalog có thể làm bill sai hoặc bị FK chặn. Đây là đầu việc checkout/bill còn mở.
- Source khai báo `idx_product_branch_size`, `idx_booking_branch_time`, `idx_dog_profile_branch`, `idx_chat_conversation_time`, `idx_conversation_customer`, `idx_conversation_branch_status`; `Conversation.customer_id` có unique constraint cho contract **một customer một inbox**. Cần đối chiếu index/FK thực tế trên MySQL bằng metadata DB sau backup; test H2 không chứng minh Railway có đủ.

## Migration và việc cần chốt

- Source có migration V4.5 đổi tên hiển thị manager và V4.6 queue adoption. Trạng thái chạy trên Railway **chưa được xác minh**; không tự chạy lại, không reseed shared database.
- Checkout/bill cần chốt phương thức thanh toán và chính sách hủy/hoàn tiền trước khi thiết kế schema snapshot, trạng thái, audit và idempotency. Khi đã chốt, tạo SQL migration có bước backup/rollback, thử trên bản sao staging rồi mới đề xuất chạy shared DB.
- Không thay `ddl-auto=validate` của production thành `update` để che lỗi schema. Quy trình chi tiết ở [release_runbook.md](release_runbook.md).
