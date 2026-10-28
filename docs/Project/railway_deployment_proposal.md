# Phương án đưa PawConnect lên Internet (đề xuất, chưa triển khai)

Ngày 25/10/2026. Phạm vi tài liệu: đề xuất để nhóm trưởng và owner hạ tầng duyệt; không xác nhận đã có backend service, public domain hay bản phát hành trên Railway.

## Kiến trúc đề xuất

Giữ MySQL đang có trong project Railway, thêm **một backend service** cho Spring Boot/Thymeleaf cùng project và environment. Giao diện, API và WebSocket chạy chung service nên chỉ cần một URL HTTPS công khai. Backend kết nối MySQL bằng private networking và reference variables, không dùng TCP proxy công khai cho kết nối nội bộ. [Railway Spring Boot](https://docs.railway.com/guides/spring-boot), [MySQL](https://docs.railway.com/databases/mysql), [Variables](https://docs.railway.com/variables).

## Điều kiện để triển khai

1. Nhóm trưởng chọn commit SHA/nhánh phát hành sau review và kết quả test; không bật tự deploy từ `TV3` hoặc tự merge sang `main`. Không coi bản chạy `localhost` là bản đã deploy.
2. Owner DB kiểm tra các migration thực tế, nhất là V4.7 của checkout và V4.8 của yêu cầu hủy đơn, tạo và xác minh backup trước khi áp phần còn thiếu. Code mới có entity `order_cancellation_requests`: nếu bảng này chưa có, MySQL profile với `JPA_DDL_AUTO=validate` có thể không khởi động. Không dùng `update`, `createDatabaseIfNotExist`, reseed hoặc thử tạo đơn trên dữ liệu chung khi chưa có kế hoạch.
3. Tạo backend service từ GitHub repository và build Spring Boot theo [hướng dẫn chính thức](https://docs.railway.com/guides/spring-boot). Repository hiện không có Maven wrapper/Dockerfile, nên cần xác nhận build thực tế trên Railway; nếu auto-detect không ổn định, bổ sung Dockerfile riêng sau khi nhóm duyệt.
4. Đặt `SPRING_PROFILES_ACTIVE=production,mysql`, `JPA_DDL_AUTO=validate`, thông số MySQL private, `JWT_SECRET`, SMTP, Cloudinary và `WEBSOCKET_ALLOWED_ORIGIN_PATTERN` trên **backend service** theo [production_configuration.md](production_configuration.md). Secret đã xuất hiện trong chat/tài liệu cục bộ phải được xoay trước khi phát hành; không đưa giá trị vào Git hoặc ảnh/log. Không dùng MySQL public proxy làm host production của backend.
5. Sau khi backend deploy và log khởi động sạch, vào backend service → Settings → Networking → Public Networking → Generate Domain để nhận URL `https://...up.railway.app` (hoặc gắn domain riêng). Đặt WebSocket allowed origin đúng URL đó. [Railway Domains](https://docs.railway.com/networking/domains/working-with-domains).
6. Smoke test qua HTTPS/WSS trên bản đã deploy: đăng nhập từng role, shop, checkout/biên lai trên dữ liệu thử được phép, hủy/hoàn tiền, manager đúng/sai chi nhánh, adoption, chat, SMTP OTP, Cloudinary và mobile 320/375/390/768 px. Theo dõi log/5xx và chạy rollback theo [release_runbook.md](release_runbook.md) nếu có lỗi.

## Chưa được xác nhận

- Chưa có quyền xem trạng thái backend service, biến môi trường, backup hoặc domain trong Railway; do đó chưa thể cung cấp URL Internet thật.
- MySQL local test ở 24/10 chỉ xác nhận có thể kết nối và schema chính hiện diện; không thay cho biên bản migration/backup hoặc smoke test production.
- Chi phí và quota cần nhóm trưởng kiểm tra trực tiếp trên project/plan Railway trước khi mở public service; không giả định miễn phí.
