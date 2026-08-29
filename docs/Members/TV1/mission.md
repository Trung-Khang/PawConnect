# PawConnect – TV1: Hướng dẫn xây dựng Module Thương mại & Dịch vụ

> **Người phụ trách:** TV1
> **Module:** Shop & Service
> **Trọng tâm:** Bán sản phẩm, giỏ hàng, đặt hàng, tồn kho và đặt lịch dịch vụ theo chi nhánh.
> **Nguồn phân công:** Module Thương mại trong `PawConnect.docx`.

---

## 1. TV1 phải chịu trách nhiệm những gì?

TV1 chịu trách nhiệm xây dựng **trọn vẹn luồng thương mại và dịch vụ từ Database → Repository → Service → Controller/API → Thymeleaf UI → Test → bàn giao**, không chỉ tạo Entity.

Phạm vi chính gồm:

| Nhóm              | Phải xây dựng                                                              |
| ----------------- | -------------------------------------------------------------------------- |
| Chi nhánh         | `Branch` – thông tin chi nhánh để Shop/Order/Booking xác định nơi phục vụ  |
| Danh mục          | `Category` – danh mục sản phẩm                                             |
| Sản phẩm          | `Product` – sản phẩm, giá, tồn kho, ảnh, kích cỡ phù hợp, category, branch |
| Giỏ hàng          | Thêm/xem/xóa sản phẩm trong giỏ                                            |
| Đặt hàng          | `Order`, `OrderItem`, tạo đơn, tổng tiền, trạng thái                       |
| Tồn kho           | Kiểm tra và cập nhật `stock` khi đặt hàng                                  |
| Thanh toán        | Thanh toán giả lập                                                         |
| Dịch vụ           | `ServiceType` – Spa, Khám bệnh, Tiêm phòng                                 |
| Đặt lịch          | `ServiceBooking` – chi nhánh, dịch vụ, chó, thời gian, trạng thái          |
| Quản lý chi nhánh | Dashboard để xem/xử lý đơn hàng và cập nhật lịch hẹn                       |
| Giao diện         | Shop, sản phẩm, giỏ hàng, checkout, booking, dashboard                     |
| API               | Toàn bộ API nhóm `5.2 – Thương mại`                                        |
| Test              | Test nghiệp vụ Shop và Service                                             |

Các entity và chức năng trên đúng với phạm vi được phân công cho thành viên A trong tài liệu gốc.

---

# 2. Vị trí code của TV1

Tất cả code phải nằm đúng phạm vi module:

```text
src/main/java/com/pawconnect/
│
├── entity/
│   ├── Branch.java
│   ├── Category.java
│   ├── Product.java
│   ├── Order.java
│   ├── OrderItem.java
│   ├── ServiceType.java
│   └── ServiceBooking.java
│
├── repository/
│   ├── BranchRepository.java
│   ├── CategoryRepository.java
│   ├── ProductRepository.java
│   ├── OrderRepository.java
│   ├── OrderItemRepository.java
│   ├── ServiceTypeRepository.java
│   └── ServiceBookingRepository.java
│
├── service/
│   └── shop/
│       ├── BranchService.java
│       ├── CategoryService.java
│       ├── ProductService.java
│       ├── CartService.java
│       ├── OrderService.java
│       └── BookingService.java
│
├── controller/
│   └── shop/
│       ├── BranchController.java
│       ├── ProductController.java
│       ├── CartController.java
│       ├── OrderController.java
│       └── BookingController.java
│
└── dto/
    ├── product/
    ├── order/
    └── booking/
```

Nếu cần Mapper:

```text
src/main/java/com/pawconnect/mapper/
```

Exception dùng chung:

```text
src/main/java/com/pawconnect/exception/
```

Không tự tạo package mới ngoài cấu trúc dự án nếu chưa thống nhất với TV3.

---

# 3. Quy trình xây dựng: làm theo thứ tự này

TV1 không nên làm UI trước rồi mới quay lại Database. Luồng triển khai nên là:

```text
ERD / Entity
    ↓
Repository
    ↓
DTO
    ↓
Service + nghiệp vụ
    ↓
Controller / API
    ↓
Test API
    ↓
Thymeleaf UI
    ↓
Test luồng hoàn chỉnh
    ↓
Bàn giao TV3
```

TV1 có thể phát triển Product, Cart/Order và Booking song song sau khi phần Entity/Repository cơ bản ổn định.

---

# 4. Bước 1 – Xây dữ liệu Shop & Service

Trước tiên tạo các Entity:

```text
Branch
Category
Product
Order
OrderItem
ServiceType
ServiceBooking
```

Quan hệ phải bám theo ERD:

```text
Branch 1 ──── N Product
Branch 1 ──── N Order
Branch 1 ──── N ServiceBooking

Category 1 ──── N Product

Order 1 ──── N OrderItem
OrderItem N ──── 1 Product

ServiceType 1 ──── N ServiceBooking
```

`Product` phải có các dữ liệu chính:

```text
id
name
description
price
stock
imageUrl
suitableSize
categoryId
branchId
```

`Order`:

```text
id
userId
branchId
status
totalAmount
createdAt
```

`OrderItem`:

```text
id
orderId
productId
quantity
price
```

`ServiceType`:

```text
id
name
duration
price
```

`ServiceBooking`:

```text
id
userId
branchId
serviceTypeId
dogProfileId
bookingTime
status
```

Các field và quan hệ này phải thống nhất với ERD trong tài liệu.

---

# 5. Bước 2 – Product & Category

Sau khi Entity/Repository hoạt động, xây Product trước vì Cart và Order phụ thuộc Product.

Luồng hoàn chỉnh:

```text
Category
   ↓
Product
   ↓
Branch
   ↓
Product listing
   ↓
Product detail
   ↓
Filter
```

TV1 phải làm:

* Danh sách Category.
* Danh sách Product.
* Chi tiết Product.
* Tạo Product.
* Sửa Product.
* Xóa Product.
* Liên kết Product với Category.
* Liên kết Product với Branch.
* Lưu giá.
* Lưu tồn kho.
* Lưu `suitableSize`.
* Lọc theo `branchId`.
* Lọc theo `size`.

API phải có:

```text
GET    /api/branches                 public
GET    /api/categories               public
GET    /api/products?branchId=&size= public
GET    /api/products/{id}            public

POST   /api/products                 BRANCH_MANAGER / ADMIN
PUT    /api/products/{id}            BRANCH_MANAGER / ADMIN
DELETE /api/products/{id}            ADMIN
```

Các quyền trên phải giữ đúng theo tài liệu gốc.

---

# 6. Bước 3 – Giỏ hàng → Order → tồn kho → thanh toán giả lập

Sau khi Product chạy ổn, xây Cart và Order thành **một luồng liên tục**:

```text
Product detail
      ↓
Add to Cart
      ↓
View Cart
      ↓
Delete Cart Item nếu cần
      ↓
Checkout
      ↓
Kiểm tra tồn kho
      ↓
Tạo Order + OrderItem
      ↓
Tính totalAmount
      ↓
Thanh toán giả lập
      ↓
Cập nhật tồn kho
      ↓
Order status
```

API:

```text
POST   /api/cart/items
GET    /api/cart
DELETE /api/cart/items/{id}

POST   /api/orders
GET    /api/orders/my
PUT    /api/orders/{id}/status
```

Khi tạo Order phải đặc biệt kiểm tra:

* Sản phẩm còn tồn kho.
* Số lượng đặt > 0.
* Không đặt vượt `stock`.
* Giá được ghi vào `OrderItem`.
* Tổng tiền chính xác.
* Order thuộc User hiện tại.
* Order thuộc đúng Branch.
* Tồn kho được cập nhật hợp lý.
* Branch Manager/Admin mới được xử lý trạng thái Order theo quyền.

---

# 7. Bước 4 – Đặt lịch dịch vụ

Sau Order, triển khai Service:

```text
ServiceType
      ↓
Chọn Branch
      ↓
Chọn DogProfile
      ↓
Chọn thời gian
      ↓
Kiểm tra trùng lịch
      ↓
Tạo ServiceBooking
      ↓
Branch Manager xử lý
```

Ba dịch vụ tối thiểu:

```text
Spa
Khám bệnh
Tiêm phòng
```

API:

```text
GET  /api/service-types          public
POST /api/bookings
GET  /api/bookings/my
PUT  /api/bookings/{id}/status  BRANCH_MANAGER
```

Đặc biệt phải xử lý yêu cầu **tránh trùng lịch theo chi nhánh và khung giờ**, đây là nghiệp vụ được nêu rõ trong tài liệu.

`ServiceBooking` phải liên kết:

```text
User
Branch
ServiceType
DogProfile
```

TV1 không tự xây DogProfile. `DogProfile` thuộc TV2. Khi cần sử dụng, phải thống nhất với TV2/TV3 về ID và API.

---

# 8. Bước 5 – Giao diện

Sau khi API ổn định, xây Thymeleaf:

```text
src/main/resources/templates/
│
├── shop/
│   ├── index.html
│   ├── products.html
│   ├── product-detail.html
│   ├── cart.html
│   ├── checkout.html
│   └── orders.html
│
└── service/
    ├── service-list.html
    ├── booking.html
    └── my-bookings.html
```

Ngoài giao diện khách hàng, TV1 phải có phần **dashboard quản lý chi nhánh** để:

* Xem đơn hàng.
* Xử lý/cập nhật trạng thái Order.
* Xem lịch dịch vụ.
* Cập nhật trạng thái ServiceBooking.

Tài liệu gốc xác định rõ trang cần có gồm trang chủ Shop, danh sách/chi tiết sản phẩm, giỏ hàng, thanh toán, đặt lịch và dashboard quản lý chi nhánh.

CSS/JS:

```text
src/main/resources/static/css/
src/main/resources/static/js/
```

---

# 9. Bước 6 – Test

Test phải đặt tại:

```text
src/test/java/com/pawconnect/
```

Không chỉ test Controller. Phải test nghiệp vụ Service.

Luồng test tối thiểu:

```text
Product
→ lấy danh sách
→ filter branch
→ filter size
→ detail
→ create
→ update
→ delete
```

```text
Cart
→ add
→ view
→ delete
→ kiểm tra tồn kho
```

```text
Order
→ checkout
→ tạo Order
→ tạo OrderItem
→ tính tiền
→ cập nhật stock
→ xử lý trạng thái
→ trường hợp thiếu stock
```

```text
Booking
→ chọn service
→ chọn branch
→ chọn dog
→ chọn thời gian
→ booking
→ phát hiện trùng lịch
→ branch manager cập nhật status
```

Phải có cả test trường hợp **thành công và thất bại**.

---

# 10. Tiêu chí nghiệm thu TV1

TV1 chỉ được xem là hoàn thành khi người khác có thể chạy và thực hiện được toàn bộ luồng:

```text
Xem Shop
   ↓
Lọc sản phẩm theo Branch / Size
   ↓
Xem Product
   ↓
Thêm Cart
   ↓
Checkout
   ↓
Thanh toán giả lập
   ↓
Tạo Order
   ↓
Cập nhật tồn kho
   ↓
Branch Manager xử lý Order
```

và:

```text
Xem Service
   ↓
Chọn Branch
   ↓
Chọn Dog
   ↓
Chọn thời gian
   ↓
Booking
   ↓
Không bị trùng lịch
   ↓
Branch Manager cập nhật Booking
```

API phải đúng endpoint, quyền và public/authenticated như tài liệu.

---

# 11. Bàn giao

TV1 bàn giao cho **TV3**:

```text
Entity
Repository
Service
Controller
DTO
API
UI
Test
Database changes
```

Đặc biệt phải ghi rõ:

```text
API nào đã hoàn thành
API nào yêu cầu CUSTOMER
API nào yêu cầu BRANCH_MANAGER
API nào yêu cầu ADMIN
```

TV1 bàn giao cho **TV2/TV3** thông tin `DogProfile` cần dùng trong `ServiceBooking`.

TV3 sẽ dùng phần này để tích hợp Authentication/Security toàn hệ thống.

---

# 12. Báo cáo bắt buộc

Mọi cập nhật phải ghi trực tiếp trong:

```text
docs/Members/TV1/report.md
```

Cuối file duy trì bảng:

| Ngày | Công việc | File/Module | Trạng thái | Test | Vấn đề | Bàn giao |
| ---- | --------- | ----------- | ---------- | ---- | ------ | -------- |
|      |           |             | ⬜/🟨/✅     |      |        |          |

Quy ước:

```text
⬜ Chưa làm
🟨 Đang làm
✅ Hoàn thành
❌ Có lỗi/chờ xử lý
```

Khi hoàn thành một nhóm chức năng, TV1 phải ghi rõ **đã làm gì, tạo/sửa file nào, API nào chạy được, test nào pass và đã bàn giao cho ai**.

---

# 13. Mốc thực hiện

Bám tiến độ chung của dự án:

```text
Tuần 1–2
→ hỗ trợ thiết kế CSDL / dựng khung

Tuần 3–5
→ hoàn thiện Shop + Service

Tuần 5–6
→ phối hợp tích hợp JWT / Cloudinary / dữ liệu dùng chung

Tuần 7
→ integration test + sửa lỗi

Tuần 8
→ hoàn thiện UI + báo cáo + demo
```

Đây là tiến độ được quy định trong tài liệu gốc.
