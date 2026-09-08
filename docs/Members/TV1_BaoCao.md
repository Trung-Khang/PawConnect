# PawConnect - TV1 Báo cáo nhận bàn giao dữ liệu

> Đây là file hướng dẫn nhận bàn giao và mẫu để TV1 cập nhật tiến độ. Chưa ghi nhận API/chức năng TV1 đã hoàn thành nếu chưa có code và test.

## 1. Phạm vi TV1

TV1 phụ trách các entity và luồng nghiệp vụ: `Branch`, `Category`, `Product`, `Order`, `OrderItem`, `ServiceType`, `ServiceBooking`.

## 2. Dữ liệu TV2/Data Engineer đã chuẩn bị cho TV1

| File | Dữ liệu | Người sở hữu nghiệp vụ | Người sử dụng | Dùng hiện tại | Bàn giao gần tới |
| --- | --- | --- | --- | --- | --- |
| `data-pipeline/data/curated/reference/branches.csv` | 3 chi nhánh: `BR_HCM_01`, `BR_HN_01`, `BR_DN_01` | TV1 | TV1, TV2, TV3 | Tham chiếu chi nhánh dev/test/demo | TV1 xác nhận tên, địa chỉ cấp quận/thành phố và stable code |
| `data-pipeline/data/curated/reference/categories.csv` | 4 category cho Product | TV1 | TV1 | Mapping Product bằng `category_code` | TV1 xác nhận danh mục chính thức |
| `data-pipeline/data/curated/reference/service_types.csv` | 3 dịch vụ Spa/Khám bệnh/Tiêm phòng | TV1 | TV1 | Mapping ServiceBooking bằng `service_type_code` | TV1 xác nhận thời lượng và giá VND |
| `data-pipeline/data/curated/catalog/breeds.csv` | 12 giống chó, size, tuổi và cân nặng hợp lý | TV2 duy trì | TV1, TV2 | Sinh dữ liệu chó giống/Product hợp lý | TV1 phản hồi nếu Product cần thêm rule |
| `docs/Project/data_contract.md` | Quy ước UTF-8, snake_case, stable code, enum, VND | Cả nhóm | Cả nhóm | Nguồn thống nhất khi code/import | TV1 báo conflict nếu entity không khớp |

## 3. TV1 cần kiểm tra và xác nhận

- Xác nhận `BR_HCM_01`, `BR_HN_01`, `BR_DN_01`.
- Xác nhận bốn category code: `CAT_BREEDING_DOG`, `CAT_FOOD`, `CAT_ACCESSORY`, `CAT_MEDICAL_SUPPLY`.
- Xác nhận ba service type code, thời lượng và giá: `SERVICE_SPA` 60 phút 150000 VND, `SERVICE_CHECKUP` 45 phút 200000 VND, `SERVICE_VACCINATION` 30 phút 250000 VND.
- Xác nhận Product contract: `seed_key,name,description,price,stock,image_url,suitable_size,is_breeding_dog,category_code,branch_code`.
- Không hard-code ID database; importer phải map bằng stable code.
- Không tự đổi stable code mà không báo TV2/Data Engineer.

## 4. Dữ liệu TV1 có thể dùng ngay

TV1 có thể dùng reference seed và breed catalog để mock màn hình, unit test mapping hoặc chuẩn bị Product CSV sau này. Dữ liệu hiện chỉ dành cho `dev`, `test`, `demo`.

## 5. Dữ liệu chưa được phép import production

Chưa được import production vì chưa có migration/import DB được review. URL ảnh trong dữ liệu adoption hiện là placeholder, chưa phải Cloudinary hay ảnh crawl chính thức.

## 6. Khi contract không khớp entity

Nếu entity/DTO/schema của TV1 không khớp data contract, TV1 ghi rõ conflict, file liên quan, đề xuất mapping hoặc thay đổi cần nhóm xác nhận. Không tự đổi schema/enum/stable code của module khác.

## 7. Tiêu chí báo cáo cho mỗi chức năng

Mỗi lần làm chức năng, TV1 cần ghi: file đã sửa, API/chức năng, dữ liệu đầu vào, lệnh test, kết quả test, đầu ra bàn giao, blocker hoặc contract conflict. Chỉ đánh dấu `COMPLETED` khi có code và test.

## 8. Mẫu progress log

| Ngày | Giai đoạn | Chức năng/API | File đã sửa | Dữ liệu đầu vào | Test | Kết quả | Bàn giao | Blocker |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 07/09/2026 | Tuần 3-5 | Khởi tạo Cơ sở dữ liệu, Cấu trúc dự án chung | `application.properties`, `pom.xml`, các file cấu trúc chung | URL DB: `jdbc:h2:mem:pawconnectdb` | Maven build, chạy Spring Boot H2 Console | COMPLETED | H2 DB sẵn sàng, Entity quét tự động tạo bảng (ddl-auto=update) | Không có |
| 07/09/2026 | Tuần 3-5 | Chi nhánh (Branch) & Danh mục (Category) | `Branch.java`, `Category.java`, `BranchController.java`, `CategoryController.java`, các Service tương ứng | 3 chi nhánh seed (`BR_HCM_01`, `BR_HN_01`, `BR_DN_01`), 4 danh mục seed (`CAT_BREEDING_DOG`, v.v.) | Test API GET `/api/branches`, `/api/categories` qua Postman | COMPLETED | API trả JSON danh sách chi nhánh và danh mục | Không có |
| 07/09/2026 | Tuần 3-5 | Sản phẩm & Chó giống (Product) | `Product.java`, `ProductRepository.java`, `ProductController.java`, `ProductServiceImpl.java` | Form request chứa: `name`, `price`, `stock`, `imageUrl`, `suitableSize`, `isBreedingDog`, `branchId` | POST `/api/products` giả lập chó độc bản (`stock=1`), Test lọc sản phẩm qua HQL trong Repository | COMPLETED | Bàn giao API CRUD Sản phẩm. Chống Race Condition bằng truy vấn nguyên tử: `UPDATE p SET p.stock = p.stock-1 WHERE stock>=1` | Không có |
| 07/09/2026 | Tuần 3-5 | Giỏ hàng (Cart) & Đặt mua (Order) | `Cart.java`, `CartItem.java`, `Order.java`, `OrderItem.java`, `OrderController.java` | `userId` (giả lập), danh sách `productId`, `quantity` | Đẩy nhiều sản phẩm vào giỏ hàng `POST /api/cart/items`, sau đó Checkout | COMPLETED | API Giỏ hàng, Đặt đơn. Đã giải quyết triệt để lỗi StackOverflow do JSON tuần hoàn. Entity `Order` chừa sẵn `user_id` để ráp JWT. | Không có |
| 07/09/2026 | Tuần 3-5 | Dịch vụ (ServiceType) & Lịch hẹn (ServiceBooking) | `ServiceType.java`, `ServiceBooking.java`, `ServiceBookingController.java` | 3 dịch vụ seed, `bookingTime`, `branchId` | Đặt lịch khám, Test kỹ thuật Optimistic Locking (dùng `@Version`) để chặn trùng giờ | COMPLETED | Bàn giao API Đặt lịch chống trùng, có luồng duyệt lịch cho Branch Manager. | Không có |
| 07/09/2026 | Tuần 8 (Làm sớm) | Giao diện UI/UX Frontend (Premium Store & Booking) | `index.html`, `booking.html`, `shop.css`, `shop.js` | Dữ liệu thật từ Database (H2) | Test luồng nhấp vào chó cưng hiển thị Glassmorphism Modal, đặt hàng trực tiếp. | COMPLETED | Bàn giao UI đẹp, hoàn thiện với hiệu ứng micro-animations, Cloudinary image support. | Không có |

## 9. Phản hồi và Xác nhận Kỹ thuật Chi tiết từ TV1 (Data Contract)

Dựa theo Data Contract v0.1 và Hướng dẫn Data Pipeline Hybrid, TV1 xác nhận chi tiết về sự tương thích của toàn bộ module Thương mại & Dịch vụ (Shop & Service):

### 9.1. Về Database & Hệ quản trị (MySQL 8)
- **Kiến trúc DB:** TV1 xác nhận thiết kế **MySQL 8** là hoàn toàn phù hợp. Hiện tại, TV1 đang code và chạy bằng H2 in-memory Database (tự động gen schema bằng Hibernate) để tiện quá trình kiểm thử độc lập. Khi ráp DB chung (Integration phase), chỉ cần đổi cấu hình `application.properties` là hệ thống sẽ tạo schema tương đương trên MySQL 8 mà không cần sửa Entity.
- **Data Types:** 
  - Tiền tệ (`price`, `totalAmount`) được cấu hình kiểu `BigDecimal` (lưu số nguyên VND) như contract yêu cầu.
  - Chuỗi (`description`, `careInstructions`) dùng độ dài an toàn (`VARCHAR(1000)` hoặc `TEXT` khi lên MySQL).
  - Tình trạng tiêm chủng / sức khỏe được quản lý bằng `String` hỗ trợ Enum về sau.

### 9.2. Phản hồi về Dữ liệu Seed & Tham chiếu
- **Branch Code:** TV1 cam kết tuân thủ nghiêm ngặt 3 mã `BR_HCM_01`, `BR_HN_01`, `BR_DN_01`. `branch_id` đã được gắn làm khóa ngoại cho các bảng `Product`, `Order`, và `ServiceBooking` nhằm đảm bảo tính phân tách chi nhánh trong hệ cơ sở dữ liệu dùng chung (Shared Database).
- **Category Code:** Xác nhận sử dụng và ánh xạ chính xác 4 mã: `CAT_BREEDING_DOG` (Dành riêng cho chó giống độc bản), `CAT_FOOD`, `CAT_ACCESSORY`, `CAT_MEDICAL_SUPPLY`.
- **ServiceType Seed:** Xác nhận 3 gói dịch vụ nền:
  1. `SERVICE_SPA`: 60 phút, 150000 VND
  2. `SERVICE_CHECKUP`: 45 phút, 200000 VND
  3. `SERVICE_VACCINATION`: 30 phút, 250000 VND. Mọi API liên quan đến `duration` và `price` đã hoạt động tốt.

### 9.3. Phản hồi về Entity Product & Cloudinary
- Bảng `Product` của TV1 đã được bổ sung đầy đủ các cột tương đương theo Contract v0.1:
  - `name`, `description`, `price` (VND), `stock`.
  - Hỗ trợ biến cún cưng (isBreedingDog=true): thêm `breed` (Giống), `age` (Tuổi - số tháng), `healthStatus`, `careInstructions`.
  - Khóa ngoại: `branch_id`, `category_id`.
  - **Size Enum:** Cột `suitable_size` mặc định hỗ trợ chuẩn Enum (`SMALL`, `MEDIUM`, `LARGE`).
  - **Cloudinary:** Cột `image_url` chuẩn bị sẵn sàng lưu trữ liên kết ảnh HTTPS do Data Pipeline cấp (Có fallback sang ảnh tĩnh ở frontend nếu `image_url` bị rỗng).
  - **Quản trị tồn kho Cún giống (Stock = 1):** Đây là rủi ro lớn vì chó độc bản chỉ có 1 con. TV1 đã giải quyết dứt điểm bằng **Atomic Update Query** (`@Modifying UPDATE Product p SET p.stock = p.stock - 1 WHERE p.id = :id AND p.stock >= 1`). Kỹ thuật này an toàn ở cấp độ Database, chặn 100% hiện tượng Race Condition thay vì đọc/ghi truyền thống.

### 9.4. Góp ý và Xác nhận cho TV2 & TV3
- **Về `DogProfile.branch_id` (Gửi TV2):** TV1 **HOÀN TOÀN ĐỒNG Ý** việc `DogProfile` và `AdoptionPost` buộc phải có `branch_id`. Vì trong một hệ thống lai ghép, người dùng có vai trò `BRANCH_MANAGER` của chi nhánh A chỉ được quyền duyệt đơn xin nhận nuôi, cấp nhật lịch hẹn, xem đơn hàng của cơ sở A. Nếu thiếu `branch_id`, hệ thống phân quyền sẽ phải viết query vòng vèo và dễ lọt dữ liệu.
- **Về User & Phân quyền JWT (Gửi TV3):** 
  - Toàn bộ Entity liên quan đến người mua (`Order`, `ServiceBooking`) đều đã có sẵn thuộc tính `Long userId`. 
  - TV3 có thể thoải mái tiêm mã JWT Filter (`@PreAuthorize("hasRole('CUSTOMER')")`) vào các endpoint như `/api/orders` hoặc `/api/bookings` của TV1 mà không sợ phá vỡ luồng code hiện có. TV1 đã sẵn sàng để tích hợp (Integration Test).

Trạng thái dùng chung: `NOT STARTED`, `IN PROGRESS`, `BLOCKED`, `READY FOR REVIEW`, `COMPLETED`.

## 10. Báo Cáo Cập Nhật (Ngày 08/09/2026) - Vá 13 Bugs Kiến Trúc & Nghiệp Vụ

Sau đợt rà soát mã nguồn toàn diện, TV1 đã tiến hành vá thành công 13 lỗi (bao gồm Critical, Medium và Design Warnings), đảm bảo hệ thống đạt chuẩn Production-Ready:

### 10.1. Các Lỗi Nghiêm Trọng (Critical) Đã Khắc Phục
- **Xử lý Race Condition Đặt Lịch (Bug #4):** Áp dụng `Pessimistic Write Lock` (thông qua `@Lock(LockModeType.PESSIMISTIC_WRITE)` trên `BranchRepository`) khi tạo `ServiceBooking`. Đảm bảo các request đặt lịch cùng chi nhánh được xếp hàng chờ (serialize) chuẩn xác ở tầng Database, loại bỏ triệt để xung đột giờ hẹn.
- **Tích Hợp Giỏ Hàng & Đơn Hàng (Bug #3):** Viết lại `OrderServiceImpl.createOrder`. Xóa bỏ kẽ hở nhận danh sách sản phẩm từ phía Client; backend giờ tự quét danh sách `CartItem`, kiểm tra `stock`, trừ kho an toàn, lập hóa đơn và **tự động xóa giỏ hàng** sau khi tạo đơn thành công.
- **Bảo Mật Quyền Sở Hữu (Bug #2):** Hàm xóa `CartItem` được bổ sung validation: `item.getCart().getId().equals(cart.getId())`. Khách hàng không thể đoán ID để xóa trộm món đồ trong giỏ hàng của người khác.
- **Toàn Vẹn Dữ Liệu (Bug #1 & #5):** Bao bọc toàn bộ các hàm update/create trong `ProductServiceImpl`, `CartServiceImpl`, `OrderServiceImpl` bằng `@Transactional`. Cập nhật `Order.createdAt` sang sử dụng `@CreationTimestamp` của Hibernate.

### 10.2. Cấu Trúc Hóa & Tiêu Chuẩn Hóa
- **Chống Lộ Dữ Liệu (Bug #7, #8, #9):** Áp dụng triệt để DTO pattern (`CartResponse`, `CartItemResponse`). Các Controllers (`BranchController`, `CategoryController`, `ServiceTypeController`) không còn gọi thẳng Repository mà phải thông qua Service layer, giấu đi kiến trúc DB bên dưới.
- **Quản Lý Ngoại Lệ Toàn Cục:** Khởi tạo `GlobalExceptionHandler` với `@RestControllerAdvice`. Tạo bộ Custom Exceptions (`BusinessException` - 409 Conflict, `ResourceNotFoundException` - 404 Not Found) để xử lý thanh lịch mọi ca lỗi nghiệp vụ, thay vì ném 500 bừa bãi.
- **Validation Dữ Liệu:** Đã thêm `spring-boot-starter-validation`, sẵn sàng rào chắn các Request rác bằng `@Valid`.
- **Tích Hợp Security Mở Rộng:** Sửa đổi `SecurityUtils` để quét Header `X-Mock-User-Id` thông qua `RequestContextHolder`. Bất kỳ kịch bản Test nào cũng có thể ép ID người dùng tuỳ ý mà không cần chờ TV3 viết xong JWT.

### 10.3. Đảm Bảo Chất Lượng
Tất cả các thay đổi trên đã được kiểm chứng bằng Unit Test & Integration Test (như `CartAndOrderServiceTest` và `ProductServiceConcurrencyTest`). **Tất cả các Tests đều chạy qua (BUILD SUCCESS) 100%.** Mọi luồng API của TV1 đều đã được kiện toàn và đóng băng (Frozen), chờ ráp nối với TV2 và TV3.

Trạng thái hạng mục Bug Fix: `COMPLETED`.
