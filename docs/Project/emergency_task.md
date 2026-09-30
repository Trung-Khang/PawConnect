# Kế hoạch bàn giao khẩn sau Giai đoạn 6

## 1. Mục tiêu

Hoàn thiện trải nghiệm thống nhất cho PawConnect trước khi kiểm thử toàn hệ thống:

- Đồng bộ giao diện giữa Home, Shop, Service, Adoption và trang quản trị.
- Loại bỏ nội dung mẫu, liên kết chết và nút không có chức năng.
- Hoàn thiện giao diện xác thực, OTP, phân quyền và Chat.
- Giữ nguyên contract dữ liệu Seed V3 và các API Adoption đã nghiệm thu.
- Phân định rõ trách nhiệm của TV1, TV2 và TV3 để tránh sửa chồng chéo.

Giai đoạn tiếp theo được xác định là **Giai đoạn 7: Hoàn thiện trải nghiệm, xác thực và Chat liên module**. Backend Chat cơ bản đã tồn tại; không xây lại `Conversation`, `ChatMessage` hoặc luồng mở hội thoại đã có.

## 2. Công việc của TV1 - giao diện và trải nghiệm dùng chung

### 2.1. Đồng bộ giao diện

1. Tạo header và footer dùng chung cho Home, Shop, Service, Adoption và trang quản trị.
2. Chỉ hiển thị thông tin dự án đã được nhóm xác nhận. Xóa địa chỉ, số điện thoại, email, mạng xã hội và nội dung mẫu không thuộc PawConnect.
3. Đồng bộ ngôn ngữ tiếng Việt trên menu, nút, trạng thái, thông báo và footer. Không để tiếng Anh và tiếng Việt lẫn lộn tùy trang.
4. Giữ thanh menu nhất quán khi chuyển giữa các module. Chỉ hiển thị mục có route, section hoặc chức năng thật.
5. Chuẩn hóa font tiếng Việt, màu sắc, khoảng cách, trạng thái hover/focus và bố cục responsive.
6. Bổ sung favicon thật và phối hợp TV3 cho phép tải tài nguyên public để loại bỏ lỗi `favicon.ico 401`.

### 2.2. Phân chia giao diện theo vai trò

- `CUSTOMER`: mua hàng, đặt lịch, xem hồ sơ nhận nuôi, gửi và theo dõi đơn.
- `BRANCH_MANAGER`: quản trị dữ liệu và nghiệp vụ thuộc chi nhánh được phân công.
- `ADMIN`: quản trị toàn hệ thống.

Không đặt các lời gọi hành động dành cho khách hàng như giỏ hàng hoặc đặt lịch nổi bật trong màn hình nghiệp vụ của Manager/Admin nếu chúng không phục vụ công việc quản trị.

### 2.3. Kiểm kê nút, menu và liên kết

TV1 phải rà toàn bộ frontend, bao gồm Home, Shop, Service, Adoption và trang quản trị, sau đó lập bảng:

| Trang | Nút/menu | Route hoặc hành vi mong đợi | Hiện trạng | Hướng xử lý | Người nhận |
|---|---|---|---|---|---|
| Ví dụ: Home | About Us | Mở section hoặc trang giới thiệu | Chưa có chức năng | Tạo chức năng hoặc loại bỏ | TV1/TV3 |
| Ví dụ: Home | Blog | Mở danh sách bài viết | Chưa có backend | Nhóm quyết định giữ hoặc bỏ | TV1/TV3 |

Quy tắc xử lý:

1. Nút chỉ cuộn đến section có sẵn hoặc chuyển tới route có sẵn: TV1 nối đúng liên kết.
2. Nút cần dữ liệu hoặc nghiệp vụ backend: TV1 mô tả request/response mong muốn và bàn giao TV3 đánh giá, triển khai.
3. Nút không phục vụ phạm vi đồ án: TV1 trao đổi TV3 và nhóm để loại bỏ, không tạo backend chỉ để giữ nút trang trí.
4. Không để `href="#"`, nút không có handler, route 404 hoặc hành động giả trong bản nghiệm thu.
5. Riêng `About Us`, `Blog` và `Contact`: chỉ giữ khi nhóm có nội dung, route và người chịu trách nhiệm. Nếu không cần, loại khỏi header/footer trên tất cả trang.

### 2.4. Hạng mục kỹ thuật khác

- Không thay đổi route hoặc API Adoption khi chuẩn hóa giao diện.
- Rà và xử lý `BookingServiceConcurrencyTest`, hiện là test ngoài Adoption còn làm full Maven suite thất bại.
- Báo TV2 trước khi thay ID, class hoặc cấu trúc DOM mà `adoption.js` đang sử dụng.

### 2.5. Tiêu chí nghiệm thu TV1

- Header/footer và menu nhất quán trên mọi module.
- Không còn thông tin liên hệ giả, liên kết chết hoặc nút không có chức năng.
- Giao diện theo role rõ ràng trên desktop và mobile.
- Không làm thay đổi contract API hoặc nghiệp vụ đã được TV2/TV3 bàn giao.
- Có bảng kiểm kê frontend và trạng thái xử lý cho từng nút/menu.

## 3. Công việc của TV3 - xác thực, backend dùng chung và Chat

### 3.1. Đăng nhập, đăng ký và phiên làm việc

1. Làm giao diện đăng ký, đăng nhập và đăng xuất.
2. Tự lưu access token và refresh token; người dùng không còn dùng PowerShell hoặc Console.
3. Tự refresh access token và đưa người dùng về trang đăng nhập khi refresh token hết hạn.
4. Điều hướng và hiển thị menu phù hợp với `CUSTOMER`, `BRANCH_MANAGER` và `ADMIN`.
5. Giữ kiểm tra quyền ở backend; ẩn nút frontend không thay thế cho authorization.

### 3.2. OTP và quên mật khẩu

1. Xác minh OTP khi đăng ký tài khoản.
2. Gửi OTP khi quên mật khẩu và cho phép đặt lại mật khẩu sau khi xác minh.
3. OTP phải có thời hạn, giới hạn số lần nhập và gửi lại, chỉ dùng một lần.
4. Không lưu hoặc ghi log OTP dạng rõ; không đưa credential vào Git.
5. Phản hồi quên mật khẩu không được tiết lộ một email có tồn tại trong hệ thống hay không.

### 3.3. Backend cho các chức năng frontend dùng chung

1. Nhận bảng kiểm kê nút/menu từ TV1.
2. Đánh giá các nút thực sự cần backend và thống nhất API contract trước khi viết code.
3. Triển khai backend cho chức năng đã được nhóm duyệt trên Home hoặc layout dùng chung.
4. Phối hợp TV1 loại bỏ `About Us`, `Blog`, `Contact` hoặc nút khác nếu chúng nằm ngoài phạm vi dự án.
5. Không tạo API rỗng, dữ liệu giả hoặc route chỉ để nút có vẻ hoạt động.
6. Bổ sung test phân quyền và validation cho từng endpoint mới.

### 3.4. Chatbot và Chat với nhân viên

Backend hiện đã có `Conversation`, `ChatMessage`, REST history, WebSocket/STOMP và luồng mở Conversation sau khi đơn Adoption được `APPROVED`. TV3 tiếp tục:

1. Làm giao diện danh sách hội thoại, lịch sử tin nhắn và nhắn tin realtime.
2. Xử lý trạng thái kết nối, reconnect và access token hết hạn.
3. Chỉ cho participant hợp lệ xem, gửi và subscribe hội thoại.
4. Manager chỉ xử lý hội thoại đúng phạm vi chi nhánh; Admin theo phạm vi được contract cho phép.
5. Có thể làm chatbot tự động cho FAQ và quy trình nhận nuôi trước khi gửi đơn.
6. Không tự mở Chat với nhân viên trước khi đơn được duyệt nếu chưa thống nhất contract mới với TV2.

Nếu nhóm muốn Chat với nhân viên ngay từ hồ sơ chó trước khi gửi hoặc duyệt đơn, TV2 và TV3 phải chốt thêm:

- Cách chọn nhân viên hoặc chi nhánh tiếp nhận.
- Quyền xem hội thoại của Customer, Manager và Admin.
- Khóa chống tạo trùng Conversation.
- Trạng thái hội thoại và liên kết với `adoptionPostId`/`dogProfileId`.

### 3.5. Tiêu chí nghiệm thu TV3

- Người dùng đăng ký, xác minh OTP, đăng nhập, refresh token và đăng xuất hoàn toàn trên giao diện.
- Luồng quên mật khẩu hoạt động nhưng không lộ OTP, mật khẩu hoặc trạng thái tồn tại của email.
- API mới từ bảng kiểm kê của TV1 có contract và test rõ ràng.
- Chat realtime chạy đúng participant, role và branch scope.
- Không làm yếu SecurityConfig để khắc phục lỗi giao diện.

## 4. Công việc phối hợp của TV2

1. Cung cấp `adoptionPostId`, `dogProfileId`, Customer được duyệt và nhân viên tạo bài cho luồng Chat.
2. Giữ ổn định API Adoption và `ConversationService.openForAdoption` hiện có.
3. Điều chỉnh nút Chat trong hồ sơ theo contract cuối:
   - Chưa đủ điều kiện: hiển thị giải thích hoặc mở chatbot.
   - Đã có Conversation: mở đúng hội thoại với nhân viên.
4. Kiểm thử Manager chỉ quản lý Adoption thuộc chi nhánh của mình.
5. Regression test trạng thái bài, đơn nhận nuôi, Cloudinary và Chat sau khi TV1/TV3 thay layout/auth.
6. Xóa tài khoản Manager HCM tạm khỏi MySQL khi không còn phục vụ nghiệm thu.

## 5. Thứ tự thực hiện

| Bước | Người thực hiện | Công việc | Điều kiện hoàn thành |
|---|---|---|---|
| 1 | Cả nhóm | Chốt ngôn ngữ, menu, thông tin liên hệ và phạm vi About Us/Blog/Contact | Không còn nội dung hoặc route chưa có chủ sở hữu |
| 2 | TV1 | Kiểm kê toàn bộ nút/menu/link frontend | Có bảng hiện trạng và hướng xử lý từng mục |
| 3 | TV1 | Tạo shared header/footer và layout theo role | Các trang dùng chung cấu trúc và ngôn ngữ |
| 4 | TV3 | Hoàn thiện UI Auth, refresh token, logout và OTP | Không còn đăng nhập bằng PowerShell/Console |
| 5 | TV1 + TV3 | Nối các nút cần backend hoặc loại bỏ mục thừa | Không còn nút chết, route 404 hoặc API giả |
| 6 | TV3 | Hoàn thiện UI Chat trên backend hiện có | REST history và WebSocket chạy đúng quyền |
| 7 | TV2 + TV3 | Nối Chat từ Adoption | Mở đúng hội thoại theo contract đã chốt |
| 8 | Cả nhóm | E2E trên MySQL chung | Register -> OTP -> Login -> Adoption -> Approve -> Chat PASS |
| 9 | Cả nhóm | Chạy full test và cập nhật báo cáo | Không còn test FAIL, secret hoặc blocker chưa ghi nhận |

## 6. Kiểm thử bắt buộc trước khi merge

- Kiểm tra tất cả menu, nút và liên kết trên desktop/mobile.
- Kiểm tra không còn nội dung liên hệ mẫu hoặc dữ liệu giả.
- Kiểm tra role và branch scope bằng cả UI lẫn API trực tiếp.
- Kiểm tra access token hết hạn được refresh và logout xóa đầy đủ phiên.
- Kiểm tra OTP hết hạn, sai nhiều lần, gửi lại và dùng lại.
- Kiểm tra Chat reconnect, history, participant và chống truy cập chéo hội thoại.
- Kiểm tra luồng Adoption từ xem hồ sơ đến duyệt đơn và mở Chat.
- Chạy `mvn test`, `git diff --check` và quét credential trước khi commit.

## 7. Quy tắc Git và bàn giao

1. TV1, TV2 và TV3 commit riêng theo phạm vi sở hữu.
2. Không commit secret, token, OTP, mật khẩu, file `.env` thật hoặc tài khoản test.
3. Mỗi báo cáo phải ghi file đã sửa, API/route thay đổi, test PASS/FAIL và phần cần thành viên khác xác nhận.
4. Không merge khi còn liên kết chết, test FAIL chưa được giải thích hoặc thay đổi API chưa bàn giao.
5. Seed V3 tiếp tục là release bootstrap bất biến; không sửa Seed để giải quyết vấn đề giao diện hoặc xác thực.
