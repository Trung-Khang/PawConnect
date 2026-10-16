# Kế hoạch tích hợp khẩn và hoàn thiện liên module

> **Cập nhật:** 16/10/2026
> **Mục đích:** Chốt các blocker trước demo, hoàn thiện Giai đoạn 7 và phân công rõ TV1, TV2, TV3.
> **Phạm vi:** Không tự chạy lại seed trên database chung hoặc đổi schema/API contract khi chưa có quyết định của nhóm.

## 1. Hiện trạng đã xác nhận

### 1.1. Phần đã có trong source

- Shop, Booking, User/Role, Branch, Category, ServiceType, Product và PuppyListing đã có entity, service, API và test.
- Adoption đã có DogProfile, AdoptionPost, AdoptionApplication, import seed, phân quyền theo chi nhánh, UI danh sách/hồ sơ/quản lý và luồng duyệt đơn.
- MySQL profile, `ddl-auto=update`, cơ chế seed theo property và Cloudinary backend đã tồn tại.
- Cloudinary lưu URL/public ID trong database; không lưu file ảnh trong database và không sửa Seed sau import.
- Auth backend có register, login, refresh token và `/api/auth/me`.
- Chat backend có Conversation, ChatMessage, REST history, WebSocket/STOMP; luồng duyệt đơn nhận nuôi có thể mở Conversation.
- Header/footer dùng chung đã được đưa vào một số trang Shop, Service và Adoption.

### 1.2. Kết quả kiểm tra kỹ thuật gần nhất

| Hạng mục | Kết quả | Ghi chú |
| --- | --- | --- |
| `mvn clean test` | PASS | 56 test, 0 failure, 0 error, 1 test MySQL bị skip theo điều kiện môi trường. |
| Build source Java | PASS | Biên dịch 158 class chính và 23 class test bằng Java 17. |
| Seed V3 `--verify v3` | Quyết định đã ghi nhận | CSV 20 Product, gồm 2 `DOG`, được TV1/nhóm giữ làm dữ liệu Shop. Validator/manifest cũ vẫn báo `count failure`; đây là nợ đồng bộ tài liệu/validator, không chạy lại seed để xử lý. |
| Seed V3 self-test | PASS | Builder vẫn từ chối stock PuppyListing không hợp lệ. |
| MySQL thật | Nghiệm thu một phần | Kết nối MySQL 8 thành công. Sáu account demo và `trungkhang` đã có trong database `pawconnect`; app user LAN `pawconnect_app` đã xác minh kết nối được từ host. Chưa nghiệm thu full app/MySQL integration test. |
| Cloudinary thật | Cần nghiệm thu lại | Backend/test unit có; cần xác nhận upload, replace, delete trên MySQL chung trước demo. |

### 1.3. Blocker không được bỏ qua

1. **Seed V3 validation cần đồng bộ sau demo.** `products.csv` có 20 record, gồm 2 `DOG` được TV1/nhóm chấp nhận cho Shop. Không khôi phục CSV và không phát hành Seed V4; chỉ ghi nhận validator/manifest/tài liệu cũ đang chưa phản ánh quyết định này.
2. **MySQL profile chưa khởi động hoàn chỉnh.** Khi chạy profile `mysql`, Hibernate tạo `chat_messages.read`, nhưng `read` là từ khóa MySQL. Cần sửa mapping/migration Chat rồi chạy lại profile MySQL trước khi nhóm có thể login qua UI dùng database chung.
3. **Chia sẻ MySQL qua LAN chưa hoàn tất.** `pawconnect_app` đã tồn tại cho `localhost` và dải `192.168.1.*`; Windows firewall rule port `3306` cần được tạo từ PowerShell Administrator trên máy chủ `192.168.1.45`. Không chia sẻ credential qua Git/tài liệu công khai.
4. **Chat chưa có giao diện nghiệp vụ hoàn chỉnh.** Auth UI/OTP/reset password đã có source và test H2; cần nghiệm thu SMTP và migration trên MySQL với environment local trước demo.
5. **UI chưa thống nhất hoàn toàn.** Một số trang còn tiếng Anh/nội dung mẫu, Admin dùng layout riêng, và các nút không có nghiệp vụ hoặc route thật phải được xử lý.

## 2. Hạng mục đã hoàn thành - TV2

> ## **DONE - TV2 ĐÃ HOÀN THÀNH AUTH, QUẢN LÝ VÀ SHARED UI**
>
> **Cập nhật:** 16/10/2026

TV2 đã thực hiện tích hợp trong workspace PawConnect, không xóa migration hoặc dữ liệu vận hành hiện có. Các thay đổi quan trọng đã hoàn tất:

1. **Xác thực PawConnect:** hoàn thiện đăng ký, xác minh email OTP, đăng nhập bằng username/email, `/api/auth/me`, logout phía frontend, quên mật khẩu, xác minh OTP reset và đặt lại mật khẩu. Password dùng BCrypt; OTP/reset token chỉ lưu SHA-256 hash; có TTL, cooldown, one-time use, giới hạn số lần sai, transaction/lock cần thiết. SMTP đọc từ environment, không hard-code credential hoặc trả/log OTP, password, token.
2. **Dữ liệu/migration Auth:** bổ sung entity/repository/service/DTO cho `AuthOtp` và `PasswordResetSession`; migration additive tại `data-pipeline/migrations/V4_0_0__pawconnect_auth_otp_mysql.sql`. Seed account demo đã được chuẩn bị/cập nhật trên MySQL local theo yêu cầu trước đó; không đưa thông tin nhạy cảm vào Git/tài liệu.
3. **Quản lý tài khoản:** thêm trang và API `/admin/users` cho ADMIN: xem danh sách, sửa họ tên/số điện thoại, khóa/mở khóa và xóa tài khoản. Backend chặn thao tác tự khóa/tự xóa; BRANCH_MANAGER không có link/UI hoặc quyền quản lý tài khoản.
4. **Quản lý cửa hàng:** trang `/admin/products` dùng shared header/menu quản lý; hỗ trợ xóa một sản phẩm, chọn nhiều dòng và xóa hàng loạt. Service vẫn kiểm tra quyền/chi nhánh ở backend.
5. **Quản lý đơn hàng:** thêm route/view `/admin/orders`, API `GET /api/orders/manage` và UI xử lý trạng thái. ADMIN xem toàn bộ; BRANCH_MANAGER chỉ xem/cập nhật đơn thuộc chi nhánh của mình.
6. **Shared header/menu:** đưa Shop, Service, Adoption, Admin Product, Admin Order, Admin User và Auth vào `fragments/header.html` cùng `header.css`/`header.js`. Menu ba gạch và sidebar chỉ hiện sau khi `/api/auth/me` xác nhận ADMIN/BRANCH_MANAGER. Sidebar có các mục Cửa hàng, Nhận nuôi, Đơn hàng; mục Tài khoản chỉ cho ADMIN. Customer/anonymous không thấy menu quản lý.
7. **Giao diện:** chuẩn hóa mốc trái logo và độ rộng header trên Cửa hàng, Dịch vụ, Nhận nuôi, Đơn hàng; chuẩn hóa cụm Cửa hàng/Dịch vụ/Nhận nuôi và font/căn giữa nút Đăng nhập/Đăng xuất. Sidebar quản lý có tông cam và ảnh trang trí dạng bát giác. Cửa hàng quản lý đã bỏ sidebar cũ để dùng menu dùng chung.
8. **User Profile:** Cụm icon/tên tài khoản mở `/profile`; người dùng tự cập nhật họ tên và số điện thoại qua `PUT /api/auth/profile`. Backend chuẩn hóa `+84` thành `0`, chỉ nhận số Việt Nam 10 chữ số đầu `03/05/07/08/09` và chặn số điện thoại trùng. Upload avatar dùng `/api/media/avatar` (JPG/PNG/GIF, tối đa 5 MB); metadata `avatar_url`/`avatar_public_id` được lưu MySQL theo luồng Cloudinary sẵn có. Header hiển thị avatar; CUSTOMER hiển thị tên cuối, ADMIN/BRANCH_MANAGER giữ nhãn role.

### 2.1. File chính TV2 đã chạm

- Backend/auth: `AuthController`, `AuthService`, `JwtService`, `SecurityConfig`, `OtpService`, `OtpMailService`, `PasswordPolicyValidator`, `ProfileUpdateRequest`, DTO/entity/repository auth, migration V4.
- Backend quản lý: `AdminUserController`, `AdminUserService`, `OrderController`, `OrderService`, `OrderRepository`, `ProductController`, `ProductService`.
- Frontend: `fragments/header.html`, `header.css`, `header.js`, `auth/*`, `profile/index.html`, `profile.css`, `profile.js`, `admin/users.html`, `admin/orders.html`, `admin/products.html`, CSS/JS admin tương ứng, Shop/Service/Adoption templates.
- Test: Auth flow/validation, authorization Product, security Admin User, controller view Adoption/Admin Order.

### 2.2. Test TV2 đã chạy

| Lệnh/nhóm test | Kết quả | Phạm vi |
| --- | --- | --- |
| `mvn clean test` | PASS trước lần bàn giao gần nhất | Auth OTP/reset, validation, authorization Product/Admin User, Adoption và các test hiện có. |
| `mvn -Dtest=AdoptionViewControllerIntegrationTest test` | **PASS - 3/3** | Render Adoption, trang quản lý nhận nuôi và `/admin/orders`. |
| `mvn -Dtest=AuthFlowIntegrationTest,AdoptionViewControllerIntegrationTest test` | **PASS - 9/9** | Render `/profile`; cập nhật hồ sơ, chuẩn hóa `+84` và từ chối số điện thoại sai. |
| `git diff --check` | PASS | Không có lỗi whitespace từ thay đổi TV2. |

### 2.3. Trạng thái sau bàn giao

Source H2/test đã hoạt động; giao diện header mới cần restart backend đang chạy và hard refresh trình duyệt để nạp template/CSS. MySQL đã xác nhận có sáu account demo, `trungkhang` và app user LAN; tuy nhiên full flow MySQL vẫn **CHƯA NGHIỆM THU** do blocker schema Chat và firewall LAN chưa tạo được từ terminal không có quyền Administrator. Migration V4 và SMTP Gmail thật cũng chưa nghiệm thu; không được coi là hoàn tất release chỉ vì H2 PASS.

## 3. Việc còn lại và bàn giao bắt buộc

### 3.1. TV1 - UI regression và nghiệp vụ thương mại

- [ ] Test desktop và mobile cho `/shop`, `/service`, `/adoptions`, `/adoptions/my-applications`, `/admin/products`, `/admin/orders` bằng anonymous, CUSTOMER, BRANCH_MANAGER và ADMIN.
- [ ] Xác nhận logo/header nằm cùng mốc trái, menu chính không chồng lên search/cart/booking/account; font và baseline Đăng nhập/Đăng xuất đồng đều.
- [ ] Xác nhận hamburger/sidebar chỉ hiện cho ADMIN/BRANCH_MANAGER; CUSTOMER/anonymous không thấy và không truy cập được chức năng staff qua UI.
- [ ] Test Cửa hàng: chọn một/nhiều sản phẩm, xóa, hủy xóa, tải lại danh sách; Manager không thể xóa/sửa dữ liệu ngoài chi nhánh.
- [ ] Test Đơn hàng: ADMIN thấy toàn bộ, Manager chỉ thấy đơn branch mình; cập nhật trạng thái hiển thị đúng sau reload.
- [ ] Báo lại mọi lỗi responsive/route/header bằng URL, role, kích thước màn hình và ảnh chụp; không tự nới `SecurityConfig` để sửa UI.

### 3.2. TV3 - Auth, MySQL/SMTP và kiểm tra bảo mật

- [ ] Chạy migration V4 **một lần** trên MySQL local/shared theo quy trình nhóm; kiểm tra schema additive và dữ liệu user cũ còn nguyên.
- [ ] Cấu hình biến môi trường MySQL/JWT/SMTP trong process chạy backend; restart Tomcat/backend sau cấu hình. Không commit hoặc in SMTP password/JWT secret ra terminal, docs hay Git.
- [ ] Sửa mapping/migration Chat để không dùng tên cột `read` trên MySQL, sau đó chạy lại profile `mysql` và xác nhận backend khởi động hoàn chỉnh.
- [ ] Trên máy chủ MySQL `192.168.1.45`, chạy PowerShell Administrator để đặt Wi-Fi là Private và tạo inbound firewall rule TCP `3306` cho subnet `192.168.1.0/24`; thành viên khác xác nhận kết nối LAN bằng app user đã được cấp riêng.
- [ ] Manual E2E: register -> nhận OTP email PawConnect -> verify -> login username -> refresh -> logout -> login email -> forgot password -> verify reset OTP -> đổi password -> login lại.
- [ ] Regression tất cả lỗi bảo mật: OTP hết hạn/sai quá số lần/cooldown/OTP cũ bị vô hiệu; reset token hết hạn/dùng lại; không dùng lại mật khẩu cũ; response quên mật khẩu không lộ account tồn tại.
- [ ] Regression quyền API: CUSTOMER/anonymous bị chặn ở `/api/orders/manage`, `/api/admin/users`, Product write; BRANCH_MANAGER bị chặn `/api/admin/users` và không xem/sửa đơn ngoài branch.
- [ ] Xác nhận seed account demo có thể đăng nhập theo credential được nhóm phân phối riêng, active/email verified đúng; không ghi credential vào issue/report.

### 3.3. Nhiệm vụ mới chưa hoàn thành của nhóm

- [ ] **7C Chat:** chốt contract Chat trước duyệt, UI chat, WebSocket/reconnect và test participant/branch scope.
- [ ] **7D Cloudinary/MySQL:** nghiệm thu upload/replace/delete ảnh thực tế, MySQL integration test với `RUN_MYSQL_INTEGRATION_TESTS=true`, kiểm tra orphan/stock/seed debt.
- [ ] **Release regression:** `mvn clean test`, frontend/manual E2E đa role, `git diff --check`, quét secret và cập nhật báo cáo thành viên trước demo.

## 4. Nguyên tắc thực hiện

1. Hoàn thành Giai đoạn 7A trước khi thêm tính năng mới hoặc chạy seed lại database chung. (ĐÃ XONG)
2. Không chạy lại seed chung để sửa dữ liệu demo. Việc đồng bộ validator/manifest V3 với quyết định giữ 2 Product `DOG` là một thay đổi data-contract riêng sau demo.
3. Không giải quyết lỗi UI bằng cách nới `SecurityConfig`, bỏ authorization hoặc hard-code token.
4. Database là dữ liệu vận hành sau import. Seed chỉ là bootstrap; ảnh và thay đổi nghiệp vụ không ghi ngược vào Seed V3.
5. Mỗi API mới phải có validation, kiểm tra role/branch scope ở backend và test tương ứng.
6. Mỗi chặng chỉ merge sau khi có `mvn clean test`, `git diff --check`, trạng thái bàn giao và checklist nghiệm thu.


## 5. Giai đoạn 7B - Trải nghiệm dùng chung và xác thực thật

**Mục tiêu:** Người dùng không còn dùng PowerShell/Console; giao diện thống nhất, không có nút chết hoặc dữ liệu mẫu không thuộc dự án.

### 5.1. TV1 - Shared layout và kiểm kê frontend

**Tiến độ 16/10/2026: IN PROGRESS.** Shop/Adoption đã dùng chung header actions: search focus đúng ô tìm kiếm theo trang, cart mở panel dùng API thật và icon đã đồng bộ CSS. Shop filter đã sửa contract `product`/`puppy`, thêm tìm kiếm client-side; giỏ hiển thị lý do đăng nhập/role/stock thay vì báo lỗi chung. Các trang còn lại và login UI vẫn thuộc các bước bên dưới.

1. Lập bảng kiểm kê cho Home, Shop, Service, Adoption, Admin: menu, nút, route, role hiển thị, backend phụ thuộc, người xử lý.
2. Giữ một hệ thống header/footer chung; Admin có thể dùng layout quản trị riêng nhưng không hiển thị CTA của khách như cart/booking nếu không có nhu cầu nghiệp vụ.
3. Chốt toàn bộ ngôn ngữ tiếng Việt: menu, button, status, validation, empty state và footer.
4. Chỉ để các thông tin liên hệ, social link, About Us, Blog, Contact khi dự án có nội dung/route thật. Nếu không có, loại đồng bộ ở header và footer.
5. Sửa font tiếng Việt, responsive, favicon và màu sắc theo hệ thống thiết kế đã dùng.
6. Với mỗi nút: nối route/handler thật, bàn giao API cần thiết cho TV3 hoặc bỏ nút. Không giữ `href="#"`, route 404 hay hành động giả.

**Nghiệm thu TV1:** Bảng kiểm kê hoàn tất; không còn nút chết; Home/Shop/Service/Adoption không lẫn tiếng Anh-Việt; desktop/mobile không vỡ bố cục.

### 5.2. TV3 - Auth UI và phiên đăng nhập

**Tiến độ 16/10/2026: CODE COMPLETE, CHỜ NGHIỆM THU MYSQL/SMTP.**

Đã triển khai và kiểm thử H2: trang `/auth/login`, `/auth/register`, `/auth/verify-email`, `/auth/forgot-password`; API tương thích `/api/auth/*` và API chuẩn `/api/v1/auth/*`; username/email login; access/refresh JWT key thống nhất; đăng ký với normalize email/phone Việt Nam; BCrypt; OTP 6 số SHA-256, 5 phút, one-time, cooldown 60 giây, tối đa 5 lần sai và so sánh constant-time; reset token ngẫu nhiên chỉ lưu hash, 10 phút, one-time, có lock database; mail PawConnect đọc hoàn toàn từ environment. Migration additive nằm tại `data-pipeline/migrations/V4_0_0__pawconnect_auth_otp_mysql.sql`; hướng dẫn vận hành không chứa credential tại `docs/Project/auth_otp_setup.md`.

Nghiệm thu tự động: `mvn clean test` PASS 56 test, gồm registration validation/phone `+84`, duplicate fields, email verification, login username/email, refresh, reset password one-time, password policy, OTP expiry/wrong-attempt/cooldown/invalidation. Chưa chạy migration lên MySQL chung và không gửi SMTP thật trong test.

1. [x] Tạo các trang/form đăng ký, đăng nhập và đăng xuất bằng API hiện có.
2. [x] Cài client auth dùng key access/refresh token chung; `/api/auth/me` xác định menu role. Refresh failure/redirect protected-page hiện có phải được TV1/TV3 regression trên UI chung.
3. [x] Hiển thị lỗi hợp lệ, không in token, OTP hoặc password ra UI/Console. Logout frontend phải xóa đủ token.
4. [x] Dùng role từ backend để điều khiển menu; backend vẫn là nơi quyết định quyền cuối cùng.
5. [x] Làm OTP đăng ký, quên mật khẩu và reset mật khẩu theo API/contract được chốt:
   - OTP có TTL, single-use, giới hạn nhập và resend.
   - Không log OTP/password plaintext.
   - Quên mật khẩu không tiết lộ email có tồn tại hay không.
6. [x] Phối hợp TV1 gắn Auth UI vào shared header, Admin và các trang Adoption/Shop/Service.
7. [ ] Chạy preflight unique phone, migration V4 một lần trên MySQL, export SMTP/JWT variables vào process chạy app, rồi manual E2E bằng mailbox thật. Không ghi SMTP password vào terminal history, docs hoặc Git.

**Nghiệm thu TV3:** H2/test PASS. Chỉ đánh dấu DONE sau khi Register -> email OTP -> login -> refresh -> logout -> login lại PASS trên UI và MySQL migration/SMTP thật; Customer/Manager/Admin thấy đúng menu.

### 5.3. Checklist đóng Giai đoạn 7B

- [x] Không cần PowerShell/DevTools để đăng nhập người dùng bình thường trong source/UI mới.
- [x] Token key dùng thống nhất toàn hệ thống; cần manual verify logout/shared header sau merge UI.
- [ ] Mọi menu/nút còn lại có route/handler/nghiệp vụ hoặc đã bị loại.
- [ ] Không còn placeholder contact, URL giả hoặc secret trên UI/source.
- [ ] Kiểm tra UI bằng Customer, Manager và Admin trên desktop/mobile.

## 6. Giai đoạn 7C - Chat và luồng nhận nuôi hoàn chỉnh

**Mục tiêu:** Nối giao diện Chat với backend hiện có mà không làm sai contract Adoption.

### 6.1. Quyết định nghiệp vụ phải chốt trước

Luồng hiện có mở Conversation khi `AdoptionApplication` được `APPROVED`. Nếu muốn nút “Chat với nhân viên” hoạt động ngay từ hồ sơ chó, TV2 và TV3 phải chốt trước khi code:

1. Chat trước duyệt là chatbot FAQ, một yêu cầu liên hệ, hay Conversation nhân viên thật.
2. Chi nhánh/nhân viên nào nhận hội thoại; quy tắc fallback khi chưa có Manager.
3. Participant được xem/gửi/subscription: Customer, Manager cùng branch, Admin.
4. Khóa chống Conversation trùng theo Customer và AdoptionPost/DogProfile.
5. Trạng thái hội thoại, thông báo khi bài đã CLOSED và xử lý khi đơn bị từ chối.

### 6.2. TV3 - Chat UI và realtime

1. Tạo danh sách hội thoại, khung chat, lịch sử và gửi tin nhắn qua REST/WebSocket hiện có.
2. Thêm reconnect, loading/error state, refresh token và xử lý access denied.
3. Subscribe đúng conversation; không để client tự chọn ID để đọc hội thoại của người khác.
4. Làm chatbot FAQ chỉ khi có nội dung/luồng rõ ràng; chatbot không được giả làm nhân viên.
5. Viết test participant, role, branch scope, REST history và WebSocket authorization.

### 6.3. TV2 - Contract Adoption và regression

1. Cung cấp mapping `adoptionPostId`, `dogProfileId`, applicant, creator/branch cho Chat.
2. Giữ rule: APPROVED đóng AdoptionPost và không còn đơn PENDING trên bài đó.
3. Đặt CTA Chat theo quyết định ở 5.1; trạng thái chưa đủ điều kiện phải giải thích rõ cho khách.
4. Regression luồng: xem hồ sơ -> gửi đơn -> Manager duyệt/từ chối -> post status -> Conversation -> Chat.
5. Rà status hiển thị: `AVAILABLE` là **Đang cần được nhận nuôi**; `CLOSED` là **Đã đóng nhận nuôi**, không tự khẳng định chó đã được nhận nuôi nếu chỉ đóng bài thủ công.

### 6.4. TV1 - Hỗ trợ giao diện và branch context

1. Đưa CTA Chat vào shared layout/hồ sơ theo contract đã chốt, không liên kết nhầm sang Service Booking.
2. Bảo đảm Manager chỉ thấy work queue/chat thuộc branch của mình.
3. Không thêm ServiceType “tư vấn nhận nuôi” chỉ để xử lý nút Chat; đây là luồng Chat độc lập, không phải booking có giá.

### 6.5. Checklist đóng Giai đoạn 7C

- [ ] Customer không đọc/gửi vào hội thoại người khác.
- [ ] Manager không xem hội thoại khác chi nhánh.
- [ ] APPROVED đóng bài và xử lý các đơn còn lại đúng contract.
- [ ] Chat history, realtime, reconnect và access expiry đã được kiểm tra.
- [ ] CTA Chat không dẫn tới Service Booking.

## 7. Giai đoạn 7D - Nghiệm thu MySQL, Cloudinary và release demo

### 7.1. TV1 - MySQL và commerce

1. Chạy app bằng profile MySQL với credential local không commit.
2. Chỉ bật seed theo property sau khi Giai đoạn 7A chốt Seed V3.
3. Kiểm tra Branch/Category/ServiceType/Product/PuppyListing idempotent; chạy lại không nhân bản dữ liệu.
4. Kiểm tra stock PuppyListing giảm đúng và về `SOLD_OUT` khi bằng 0.

### 7.2. TV2 - Adoption và Cloudinary thực tế

1. Chạy import Adoption sau Role/User/Branch và kiểm tra số record/mapping trên MySQL.
2. Test upload, replace, delete ảnh DogProfile và AdoptionPost bằng tài khoản Manager đúng branch.
3. Xác nhận database lưu `image_url`/`image_public_id`; Cloudinary lưu file; Seed V3 không đổi.
4. Kiểm tra bài/đơn orphan bằng SQL và kiểm tra rule APPROVED/CLOSED.

### 7.3. TV3 - Bảo mật cấu hình và vận hành

1. Cấu hình Cloudinary/MySQL bằng biến môi trường hoặc file local đã gitignore.
2. Quét Git trước demo để bảo đảm không có API secret, password, token, OTP, ảnh private hoặc file `.env` thật.
3. Xác nhận upload chỉ chấp nhận loại/kích thước file hợp lệ; lỗi Cloudinary không làm hỏng record dang dở.

### 7.4. Checklist release demo

- [ ] `mvn clean test` PASS.
- [ ] MySQL integration test chạy với `RUN_MYSQL_INTEGRATION_TESTS=true` và PASS.
- [ ] Seed verify PASS theo release đã chốt.
- [ ] Login/OTP/refresh/logout PASS trên UI.
- [ ] Customer -> Adoption application -> Manager decision -> Chat PASS.
- [ ] Product/PuppyListing/Booking đúng role và branch scope.
- [ ] Cloudinary upload/replace/delete PASS và không có secret trong Git.
- [ ] `git diff --check` PASS; mỗi thành viên cập nhật báo cáo bàn giao.

## 8. Bảng bàn giao và báo cáo bắt buộc

Mỗi thành viên cập nhật báo cáo sau mỗi giai đoạn theo mẫu sau:

| Mục | Nội dung phải ghi |
| --- | --- |
| Giai đoạn | 7A, 7B, 7C hoặc 7D; trạng thái TODO/IN PROGRESS/BLOCKED/DONE. |
| Phạm vi | Nghiệp vụ, role, branch và API bị ảnh hưởng. |
| File sửa | Danh sách source, test, template, CSS/JS, tài liệu. |
| API/DB | Route, request/response, schema hoặc mapping thay đổi. |
| Test | Lệnh chạy, PASS/FAIL, số test và môi trường H2/MySQL. |
| Bàn giao | Điều TV1/TV2/TV3 cần xác nhận hoặc làm tiếp. |
| Rủi ro | Seed, migration, secret, phân quyền, dữ liệu test hoặc regression còn tồn tại. |

## 9. Thứ tự ưu tiên thực tế

1. **7A:** chốt Seed V3, sửa authorization và xác nhận baseline test.
2. **7B:** shared UI, kiểm kê nút, Auth UI, token và OTP/reset password.
3. **7C:** chốt contract rồi tích hợp chat UI với Adoption.
4. **7D:** MySQL/Cloudinary E2E, regression, kiểm tra bảo mật và release demo.

Không bắt đầu seed lại database chung hoặc mở rộng tính năng ngoài phạm vi trước khi hoàn thành 7A.
