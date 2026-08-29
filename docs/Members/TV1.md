# PawConnect – TV1: Thương mại & Dịch vụ (Shop & Pet Care Services)

> **Người phụ trách:** TV1
> **Phân hệ:** Thương mại bán hàng & Dịch vụ chăm sóc

---

## 1. Phạm vi phụ trách

TV1 xây dựng toàn bộ luồng **bán chó giống & đồ dùng thú cưng** theo chuỗi cửa hàng và **đặt lịch dịch vụ chăm sóc** (Spa, Khám bệnh, Tiêm phòng).

TV1 quản lý **7 entity**:

```text
Branch, Category, Product, Order, OrderItem, ServiceType, ServiceBooking
```

**Quyền hạn:**
* Cửa hàng (**ADMIN / BRANCH_MANAGER**) là bên duy nhất được đăng bán chó giống, thức ăn, phụ kiện, vật tư chăm sóc và quản lý đơn hàng / lịch hẹn theo chi nhánh.
* Khách hàng (**CUSTOMER**) chỉ xem danh mục, lọc theo kích cỡ/giống chó, đặt hàng và đặt lịch chăm sóc.

---

## 2. Chức năng phải làm

```text
1. Quản lý chi nhánh (Branch) — chỉ ADMIN thao tác, hiển thị công khai danh sách.
2. Quản lý danh mục (Category) — Chó giống thuần chủng, Thức ăn, Phụ kiện, Vật tư y tế.
3. Quản lý sản phẩm / chó giống (Product) theo Branch:
   - Đăng sản phẩm, chó giống (isBreedingDog) có đồ dùng / chó riêng.
   - Lọc theo branchId, isBreedingDog, suitableSize.
   - Quản lý tồn kho (stock). Chống race condition khi nhiều khách mua chó giống độc bản (stock = 1).
4. Giỏ hàng (Cart) — thêm/xem/xóa sản phẩm.
5. Đặt hàng (Order + OrderItem) — tạo đơn theo Branch, cập nhật trạng thái.
6. Dịch vụ (ServiceType) — Spa, Khám bệnh, Tiêm phòng.
7. Đặt lịch (ServiceBooking) — đặt khung giờ theo Branch, chống trùng lịch.
```

---

## 3. Entity & API tương ứng

### Entity

```text
Branch          (id, name, address, phone, latitude, longitude)
Category        (id, name, description)
Product         (id, name, description, price, stock, imageUrl,
                 suitableSize, isBreedingDog, categoryId, branchId)
Order           (id, userId, branchId, status, totalAmount, createdAt)
OrderItem       (id, orderId, productId, quantity, price)
ServiceType     (id, name, duration, price)
ServiceBooking  (id, userId, branchId, serviceTypeId, bookingTime, status)
```

### Quan hệ quan trọng

```text
Branch  1 ── N Product
Branch  1 ── N ServiceBooking
Order   1 ── N OrderItem
User    1 ── N Order           (User do TV3 cung cấp)
```

### API (không thêm/bớt endpoint)

| Method | Endpoint                      | Quyền                  | Chức năng                                   |
| ------ | ----------------------------- | ---------------------- | ------------------------------------------- |
| GET    | `/api/branches`               | Public                 | Danh sách chi nhánh                          |
| GET    | `/api/categories`             | Public                 | Danh mục sản phẩm / chó giống                |
| GET    | `/api/products`               | Public                 | DS chó giống & sản phẩm (lọc branchId, isBreedingDog, size) |
| GET    | `/api/products/{id}`          | Public                 | Chi tiết chó giống / sản phẩm                |
| POST   | `/api/products`               | BRANCH_MANAGER, ADMIN  | Đăng mới sản phẩm / chó giống                |
| PUT    | `/api/products/{id}`          | BRANCH_MANAGER, ADMIN  | Cập nhật giá bán, số lượng, thông tin        |
| DELETE | `/api/products/{id}`          | ADMIN                  | Xóa sản phẩm / chó giống                     |
| POST   | `/api/cart/items`             | CUSTOMER               | Thêm sản phẩm / chó giống vào giỏ             |
| GET    | `/api/cart`                   | CUSTOMER               | Xem giỏ hàng cá nhân                         |
| DELETE | `/api/cart/items/{id}`        | CUSTOMER               | Xóa sản phẩm khỏi giỏ                        |
| POST   | `/api/orders`                 | CUSTOMER               | Đặt mua chó giống / sản phẩm                  |
| GET    | `/api/orders/my`              | CUSTOMER               | Lịch sử mua hàng cá nhân                     |
| PUT    | `/api/orders/{id}/status`     | BRANCH_MANAGER, ADMIN  | Cập nhật trạng thái xử lý đơn                 |
| GET    | `/api/service-types`          | Public                 | Danh sách gói dịch vụ (Spa, Khám, Tiêm)      |
| POST   | `/api/bookings`               | CUSTOMER               | Đặt lịch chăm sóc chó tại chi nhánh           |
| GET    | `/api/bookings/my`            | CUSTOMER               | Danh sách lịch hẹn cá nhân                    |
| PUT    | `/api/bookings/{id}/status`   | BRANCH_MANAGER         | Duyệt / hủy lịch hẹn tại chi nhánh           |

---

## 4. Vị trí file / package cần tạo

Cây thư mục hiện tại được giữ nguyên. TV1 phát triển trong các package sau:

```text
src/main/java/com/pawconnect/
├── entity/          Branch, Category, Product, Order, OrderItem, ServiceType, ServiceBooking
├── repository/
├── controller/shop/  Branch, Category, Product, Cart, Order controllers
├── controller/       điểm đặt lịch (booking) / chi nhánh dùng chung
├── service/shop/     logic giỏ hàng, đặt hàng, sản phẩm, tồn kho, đặt lịch
├── dto/product/       request/response cho sản phẩm/chó giống
├── dto/order/         request/response cho order/orderItem
└── dto/booking/       request/response cho service/booking
```

Giao diện (templates):

```text
src/main/resources/templates/shop/        Trang chủ Shop, Gallery, Chi tiết sp
src/main/resources/templates/service/     Trang đặt lịch dịch vụ
src/main/resources/templates/admin/       (đóng góp Dashboard chi nhánh — triển khai chung với TV3)
```

Static:

```text
src/main/resources/static/css/
src/main/resources/static/js/
src/main/resources/static/images/
```

---

## 5. Yêu cầu kỹ thuật quan trọng

* **Shared Database**: toàn hệ thống dùng chung một CSDL quan hệ; dữ liệu Shop/Dịch vụ phân biệt theo khóa ngoại `branch_id`.
* **Index**:
  * `idx_product_branch_dog` trên `Product(branch_id, is_breeding_dog, suitable_size)` — tối ưu lọc chó giống/đồ dùng theo chi nhánh.
  * `idx_booking_branch_time` trên `ServiceBooking(branch_id, booking_time, status)` — hỗ trợ kiểm tra chống trùng lịch tại chi nhánh.
* **Chống trùng lịch ServiceBooking**: dùng Optimistic Locking (`@Version`) hoặc Pessimistic Locking trong JPA để khóa bản ghi lịch hẹn khi xử lý giao dịch đặt lịch.
* **Chống race condition khi mua chó giống độc bản (stock = 1)**: dùng câu truy vấn Atomic Update trong `@Transactional`:
  ```sql
  UPDATE Product p SET p.stock = p.stock - 1
  WHERE p.id = :id AND p.stock >= 1
  ```
  Nếu số dòng cập nhật = 0 thì báo hết hàng. Không trừ stock bằng đọc–ghi thông thường.
* **Cloudinary**: hình ảnh sản phẩm / chó giống lưu bằng Cloudinary API. Lưu `imageUrl` vào `Product`.
* **JWT (do TV3 cấp)**: API có quyền phải được bảo vệ; chưa đăng nhập → bị từ chối.

---

## 6. Test cần có

* **Unit test** từng service: sản phẩm, giỏ hàng, đặt hàng, tồn kho, đặt lịch.
* **Chống trùng lịch**: test hai request đặt cùng `branchId + bookingTime + status` → chỉ 1 thành công.
* **Chống race condition stock**: mô phỏng nhiều khách mua cùng chó giống `stock = 1` → chỉ 1 đơn tạo được, các đơn còn lại báo hết hàng.
* **Authorization**: CUSTOMER không đăng product; BRANCH_MANAGER không xóa product; chỉ ADMIN xóa.
* **Integration test xuyên module** (phối TV3): luồng JWT → Product → Cart → Order; và Service → Booking, bảo đảm `User`/`Role` (TV3) và `Branch` hoạt động đúng.

---

## 7. Dependency với thành viên khác

* **Nhận từ TV3**: `User`, `Role`, xác thực JWT, cơ chế phân quyền `@PreAuthorize` cho API Shop/Dịch vụ.
* **Bàn giao cho TV3**: các API `Shop/Order/Booking`, entity `Order`, `ServiceBooking`, `Branch`; thống nhất dùng chung `User.id` làm `userId` trong `Order`, `ServiceBooking`.
* **Phối hợp TV2**: `DogProfile` (TV2) có thể được tham chiếu trong nghiệp vụ lịch hẹn; cần thống nhất trước khi tích hợp (DogProfile thuộc TV2).

---

## 8. Tiêu chí nghiệm thu (Demo)

```text
Đăng nhập (Admin/Quản lý chi nhánh)
 ↓
Tạo/đăng sản phẩm & chó giống theo chi nhánh (có ảnh Cloudinary)
 ↓
Khách hàng xem Gallery, lọc theo size / isBreedingDog
 ↓
Thêm giỏ hàng
 ↓
Đặt mua chó giống (stock giảm đúng, chống mua trùng)
 ↓
Admin cập nhật trạng thái đơn
 ↓
Khách đặt lịch Spa/Khám/Tiêm (không trùng khung giờ)
 ↓
Manager duyệt / hủy lịch
```

---

## 9. Bàn giao

* TV1 bàn giao cho **TV3**: toàn bộ API `Shop`, `Category`, `Product`, `Cart`, `Order`, `Service`, `Booking`, `Branch` để TV3 bảo vệ bằng JWT và tích hợp toàn hệ thống.
* TV1 nhận feedback từ TV3 khi có conflict API/entity/security/database.
* Luôn giữ `branch_id` để phân biệt dữ liệu trong Shared Database.

---

## 10. Báo cáo & Git

* Báo cáo tiến độ cập nhật ngay trong file này (bảng dưới), theo yêu cầu chung của nhóm.
* Quy trình: **test → commit → push → cập nhật file này** sau mỗi chức năng.
* Làm việc trên **branch riêng** của TV1. Không merge code chưa test vào branch chung.

| Ngày | Chức năng | File/Module | API | Test | Trạng thái | Bàn giao |
| ---- | --------- | ----------- | --- | ---- | ---------- | -------- |
|      |           |             |     |      | ⬜/🟨/✅     |          |

---

## 11. Tiến độ 8 tuần

```text
Tuần 1–2  → hỗ trợ thiết kế DB + dựng khung project (chung)
Tuần 3–5  → Sản phẩm/Chó giống + Cart + Order + Booking
Tuần 5–6  → Cloudinary + phối hợp phân quyền (TV3) + bàn giao API
Tuần 7    → Integration Test toàn hệ thống + sửa lỗi
Tuần 8    → Hoàn thiện UI Shop/Gallery/Booking + báo cáo + demo
```
