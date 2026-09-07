# Báo cáo Tiến độ & Chi tiết Công việc - TV1 (Thương mại & Dịch vụ)

**Ngày báo cáo:** 07/09/2026
**Thành viên thực hiện:** TV1
**Trạng thái tổng quan:** Hoàn thành 100% các yêu cầu chức năng theo tài liệu phân công.

---

## 1. Tổng quan các Module đã triển khai

Tôi đã hoàn thiện toàn bộ luồng nghiệp vụ E-commerce (Mua bán cún cưng, vật tư) và Service (Đặt lịch dịch vụ thú y, spa) dành riêng cho từng chi nhánh. Toàn bộ mã nguồn đã được tối ưu và thiết kế đồng bộ với hệ thống chung.

### Hệ thống Thực thể (Entities) & Database
Thiết lập thành công 7 bảng dữ liệu cốt lõi bằng JPA/Hibernate:
1. `Branch`: Quản lý chi nhánh cửa hàng.
2. `Category`: Danh mục (Thức ăn, Phụ kiện, Cún giống).
3. `Product`: Sản phẩm và Cún giống (Đã mở rộng thêm các trường: `breed`, `age`, `healthStatus`, `careInstructions` để hiển thị chi tiết thú cưng).
4. `Cart` & `CartItem`: Quản lý giỏ hàng.
5. `Order` & `OrderItem`: Quản lý đơn hàng.
6. `ServiceType`: Danh sách gói dịch vụ.
7. `ServiceBooking`: Quản lý đặt lịch hẹn.

## 2. Xử lý Kỹ thuật Chuyên sâu (Core Technical Implementations)

Để đảm bảo hệ thống không bị lỗi khi có nhiều người truy cập, tôi đã áp dụng các kỹ thuật sau:

- **Tối ưu hóa Database (Indexing):**
  - Tạo `idx_product_branch_dog` trên `(branch_id, is_breeding_dog, suitable_size)` giúp truy vấn lọc cún giống theo chi nhánh cực kỳ nhanh.
  - Tạo `idx_booking_branch_time` giúp việc tra cứu lịch rảnh nhanh chóng.

- **Xử lý Đụng độ dữ liệu (Concurrency Control):**
  - **Chống Race Condition khi mua cún giống (Stock = 1):** Triển khai truy vấn nguyên tử (Atomic Update) trong `ProductRepository`: `UPDATE Product p SET p.stock = p.stock - 1 WHERE p.id = :id AND p.stock >= 1`. Điều này đảm bảo dù có 10 người cùng nhấn "Mua ngay" một bé cún thì chỉ có 1 người mua thành công.
  - **Chống Trùng lịch Dịch vụ (Double Booking):** Áp dụng **Optimistic Locking** thông qua annotation `@Version` trong entity `ServiceBooking`. Bất kỳ yêu cầu đặt trùng khung giờ nào cũng sẽ bị DB từ chối an toàn.

## 3. Hoàn thiện Giao diện (Premium UI/UX)

Thay vì chỉ làm Backend, tôi đã xây dựng Frontend cho module này với thiết kế hiện đại, cao cấp:

- **Tích hợp Cloudinary:** Hoàn thiện cơ chế nạp ảnh thú cưng trực tiếp từ Cloudinary thông qua biến `imageUrl`, hệ thống tự động fallback sang ảnh demo nếu chưa có ảnh.
- **Glassmorphism Modal:** Xây dựng cửa sổ bật lên (Popup Modal) với hiệu ứng kính mờ trong suốt (Glassmorphism). Khách hàng click vào cún cưng sẽ hiện ra chi tiết về: Giống, Tuổi, Sức khỏe, Chăm sóc mà không cần load lại trang.
- **Micro-Animations:** Áp dụng hiệu ứng `fadeInUp`, hiệu ứng nổi (hover scale/bounce) cho các nút "Mua ngay" (`btn-pill`) và "Giỏ hàng" giúp website có trải nghiệm mượt mà, sống động.
- **Multi-step Form:** Trang Đặt lịch dịch vụ (`booking.html`) được cấu trúc thành các bước trực quan, dễ thao tác.

## 4. Tích hợp và Fix Bug

- Đã sửa triệt để lỗi biên dịch DTO mapping (`Unresolved compilation problem`) liên quan đến cấu trúc class tĩnh của `OrderResponse` và `OrderRequest`.
- Đã khắc phục lỗi vòng lặp JSON vô tận (StackOverflowError) khi trả dữ liệu bằng cách loại bỏ `@Data` thay bằng `@Getter/@Setter` và dùng `@JsonIgnore`.
- Khắc phục lỗi Spring Boot không tìm thấy View do thiếu thư viện Thymeleaf trong `pom.xml`.

## 5. Tình trạng Bàn giao (Handoff Status)

- **Mức độ hoàn thành:** **100% (Sẵn sàng tích hợp)**.
- **Đầu ra:** Toàn bộ RESTful APIs của Shop và Booking đã hoàn thiện. Entity `Order` và `ServiceBooking` đã được thiết kế mở để chừa sẵn cột `userId`.
- **Chờ TV3:** Bàn giao toàn bộ module cho TV3. TV3 chỉ cần ráp bộ lọc bảo mật JWT (`@PreAuthorize`) vào các Controller của TV1 và truyền `userId` từ token vào là hệ thống hoàn chỉnh.

---
*Báo cáo kết thúc.*
