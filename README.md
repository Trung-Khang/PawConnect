# PawConnect — Website Chuỗi Cửa Hàng Chó Cưng & Dịch Vụ Thú Cưng

> **Đồ án cuối kỳ môn Lập trình Web — Trường Đại học Sư phạm Kỹ thuật TP.HCM (HCMUTE)**  
> **Khoa Công nghệ Thông tin — Bộ môn Công nghệ Phần mềm**  
> **Nhóm thực hiện:** Nhóm 24

---

## Giới thiệu đề tài

**Tên đề tài:**  
> **Xây dựng website chuỗi cửa hàng chó cưng PawConnect bằng Spring Boot + Thymeleaf + Bootstrap + JPA + MySQL/PostgreSQL/SQL Server + Spring Security + JWT + WebSocket + Cloudinary.**

**PawConnect** là hệ sinh thái web đa chi nhánh (TP.HCM, Hà Nội, Đà Nẵng) hỗ trợ quản lý và kết nối toàn diện dành cho thú cưng:
- **Thương mại điện tử & Ưu đãi:** Mua bán chó giống, thức ăn, phụ kiện, vật tư chăm sóc theo từng chi nhánh; quản lý giỏ hàng, đặt hàng, hệ thống mã giảm giá (Coupons: `DON500K`, `LANDAU15`, `SPA50K`) và xử lý đơn hàng.
- **Dịch vụ chăm sóc thú cưng:** Đặt lịch dịch vụ (Spa/Grooming, Khám thú y, Tiêm phòng) với giao diện chọn khung giờ trực quan, tự động kiểm soát trùng lịch và xử lý hủy lịch/vắng mặt (No-Show).
- **Chương trình Nhận nuôi (Adoption):** Quản lý hồ sơ chó cứu hộ, đăng tin tìm mái ấm, tiếp nhận và quy trình phê duyệt đơn nhận nuôi minh bạch.
- **Tư vấn trực tuyến (Real-time Chat):** Hỗ trợ khách hàng nhắn tin trực tiếp với nhân viên chi nhánh theo thời gian thực (WebSocket STOMP).
- **Bảo mật & Quản trị:** Phân quyền đa cấp bậc (`ADMIN`, `BRANCH_MANAGER`, `CUSTOMER`), xác thực JWT, xác minh mã OTP email và bảng điều khiển quản trị tập trung.

---

## Công nghệ chính sử dụng

- **Backend:** Java 17+, Spring Boot 3.x, Spring Data JPA, Spring Security, Spring WebSocket (STOMP & SockJS), JWT (JSON Web Token), Resend HTTP API (Email OTP).
- **Frontend:** Thymeleaf template engine, Bootstrap 5, HTML5/CSS3, JavaScript (ES6+), FontAwesome / Lucide Icons.
- **Database:** MySQL 8.x (chuẩn hóa schema, tự động seed dữ liệu mẫu), Hibernate ORM.
- **Cloud & Media:** Cloudinary (lưu trữ và tối ưu hình ảnh sản phẩm, thú cưng, avatar).
- **Triển khai & CI/CD:** Docker, Railway Cloud Platform.

---

## Thành viên tham gia thực hiện (Nhóm 24)

| STT | Họ và tên | MSSV | Vai trò & Phân hệ phụ trách |
| :---: | :--- | :---: | :--- |
| **1** | **Hà Đinh Minh Lý** | **24133038** | **TV1 – Thương mại, Dịch vụ chăm sóc & Thông báo**<br>• Quản lý Sản phẩm, Danh mục, Chi nhánh & Giỏ hàng (`Cart`, `CartItem`).<br>• Đặt lịch dịch vụ (Spa, Khám, Tiêm phòng), giao diện Time Slots Picker & tự động xử lý No-Show.<br>• Hệ thống Thông báo chuông (Notifications) cho khách hàng và nhân viên chi nhánh theo sự kiện. |
| **2** | **Nguyễn Trung Khang** | **24133028** | **TV2 – Quản lý Nhận nuôi, Real-time Chat & Trưởng nhóm**<br>• Quản lý Hồ sơ chó cưng (`DogProfile`), Bài đăng nhận nuôi (`AdoptionPost`).<br>• Quy trình Khách nộp đơn nhận nuôi, Nhân viên phê duyệt/từ chối & tự động đồng bộ trạng thái bài viết.<br>• Chat trực tuyến thời gian thực Khách hàng ↔ Nhân viên (WebSocket STOMP) & thẻ ngữ cảnh tư vấn trực tiếp.<br>• Cùng phát triển luồng Đăng ký, Đăng nhập, Xác thực OTP Email & Quản trị tài khoản người dùng Admin. |
| **3** | **Vũ Hoàng Long** | **24133035** | **TV3 – Nền tảng Bảo mật, Mua bán thanh toán, Mã giảm giá, Mobile & Triển khai**<br>• Xác thực & Phân quyền hệ thống (Spring Security, JWT, OTP Email, Quản trị tài khoản Admin).<br>• Luồng mua bán & thanh toán: Đặt hàng (`Order`, `OrderItem`), quản lý & áp dụng Mã giảm giá (`DiscountCoupon`), xác nhận thanh toán & quy trình hủy đơn hàng có kiểm soát.<br>• Tối ưu hóa giao diện thiết bị di động (Site Mobile / Mobile Navigation, bảng quản trị co giãn) & Triển khai đám mây (Docker, Railway). |

---

## Cách chạy chương trình

Hệ thống đã được đóng gói container Docker và triển khai trực tuyến trên hạ tầng đám mây Railway:

 **Truy cập website trực tiếp tại:** [**https://pawconnect-ute.up.railway.app/**](https://pawconnect-ute.up.railway.app/)

*(Người dùng và hội đồng chấm chỉ cần nhấp vào đường link trên là có thể trải nghiệm toàn bộ tính năng mà không cần cài đặt môi trường trên máy cá nhân).*

---

## Danh sách tài khoản demo

Hệ thống đã tích hợp sẵn 6 tài khoản mẫu tương ứng với các vai trò trong chuỗi cửa hàng:

| Vai trò / Quyền | Chi nhánh / Ghi chú | Email đăng nhập | Mật khẩu chung |
| :--- | :--- | :--- | :--- |
| **ADMIN** | Quản trị toàn hệ thống | `admin@pawconnect.example` | `Password123!` |
| **BRANCH_MANAGER** | Quản lý Chi nhánh TP.HCM | `manager.hcm@pawconnect.example` | `Password123!` |
| **BRANCH_MANAGER** | Quản lý Chi nhánh Hà Nội | `manager.hn@pawconnect.example` | `Password123!` |
| **BRANCH_MANAGER** | Quản lý Chi nhánh Đà Nẵng | `manager.dn@pawconnect.example` | `Password123!` |
| **CUSTOMER** | Khách hàng mẫu (Khách An) | `an@pawconnect.example` | `Password123!` |
| **CUSTOMER** | Khách hàng mẫu (Khách Lan) | `lan@pawconnect.example` | `Password123!` |

> **Mật khẩu cho tất cả 6 tài khoản trên:** `Password123!`  
> Ngoài ra, người dùng có thể tự đăng ký tài khoản Khách hàng mới trực tiếp tại trang web bằng email thật để nhận mã OTP kích hoạt và trải nghiệm luồng mua hàng / nộp đơn nhận nuôi.
