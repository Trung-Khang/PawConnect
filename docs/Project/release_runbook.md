# PawConnect — runbook staging/release (TV3)

> Đây là quy trình vận hành, **không** xác nhận rằng bản hiện tại đã được deploy. Chỉ người có quyền quản trị hạ tầng mới chạy migration/deploy trên database dùng chung sau khi nhóm chốt bản release và có backup.

## 1. Điều kiện trước deploy

1. Ghi SHA commit cần phát hành, chạy `mvn clean test` và lưu báo cáo kiểm thử; TV1 xác nhận regression shop/adoption/chat. Không deploy code chưa qua nghiệm thu.
2. Kiểm tra các biến trong [production_configuration.md](production_configuration.md) trên **backend service**. Chỉ ghi tên biến vào báo cáo, không in hoặc commit giá trị. Dùng `SPRING_PROFILES_ACTIVE=production,mysql` và `JPA_DDL_AUTO=validate`.
3. Đối chiếu schema hiện tại với các file `data-pipeline/migrations/`, đặc biệt V4.5 (tên hiển thị manager), V4.6 (queue adoption), V4.7 (checkout/biên lai), V4.8 (yêu cầu hủy đơn) và V4.9 (mã giảm giá/snapshot giá đơn). Lập danh sách migration chưa chạy; không suy ra trạng thái Railway từ source.
4. Tạo và xác minh bản backup MySQL trước mọi migration; ghi thời điểm, người thực hiện và nơi lưu an toàn. Không chạy seed/import lại trên database demo đã có dữ liệu.

## 2. Migration và deploy

1. Người quản trị review SQL, số bản ghi ảnh hưởng và khả năng rollback trên bản sao staging. Chạy từng migration chưa áp dụng theo thứ tự phiên bản, ghi log/kết quả; không chạy lại tùy tiện nếu migration không idempotent.
2. Deploy đúng SHA đã test lên backend Railway. Giữ profile production/MySQL, dùng kết nối MySQL private và một public origin HTTPS chính xác cho WebSocket; không dùng wildcard origin.
3. Kiểm tra log khởi động: kết nối DB, Hibernate `validate`, cấu hình JWT/SMTP/Cloudinary, lỗi 4xx/5xx. Nếu validate lỗi, dừng phát hành; không đổi `ddl-auto` thành `update` để vượt lỗi.
4. Mở site qua HTTPS và xác nhận kết nối chat qua WSS. Không ghi token, mật khẩu, database URL hoặc email OTP vào ảnh/log bàn giao.

## 3. Smoke test bằng tài khoản thử nghiệm

| Luồng | Kiểm tra tối thiểu |
| --- | --- |
| Auth | Đăng ký/OTP thật, đăng nhập, refresh, quên mật khẩu; API cần JWT trả 401 khi chưa đăng nhập. |
| Shop/cart/order | Tìm kiếm, thêm/xóa cart; chỉ thử checkout khi nghiệp vụ thanh toán và migration tương ứng đã được nghiệm thu. Kiểm tra stock, tổng tiền, quyền đọc đơn, hủy ngay trước xác nhận thanh toán, gửi/duyệt/từ chối yêu cầu hủy khi đang xử lý, và chặn bàn giao khi yêu cầu còn chờ. Không thử hủy đơn thực của khách trên DB chung. |
| Mã giảm giá | Admin xem/thêm/tạm ngưng mã; customer/manager không quản trị được. Checkout chỉ chọn một mã, báo giá và lượt do server tính, đơn/biên lai giữ số trước giảm và sau giảm. Kiểm tra ngưỡng >500k, mua lần đầu, spa đã hoàn tất, hết lượt, đổi/bỏ mã và hoàn lượt chỉ sau hủy được chấp nhận. Chi tiết tại [discount_coupons.md](discount_coupons.md); không dùng đơn của người khác để thử. |
| Booking/adoption | Đặt lịch, nộp đơn nhận nuôi; manager sai chi nhánh không sửa được booking/đơn. |
| Chat | Một customer một inbox, context/deep-link đúng; WSS/reconnect không nhân đôi hoặc mất tin. |
| Media | Upload và xóa ảnh thử qua Cloudinary, kiểm tra lỗi provider hiển thị rõ. |
| Mobile | 320, 375, 390, 768, 1024 px và desktop: menu, form, modal, chat, cart, bảng quản trị; kiểm tra console/network/hard refresh. |

Ghi kết quả theo từng role: CUSTOMER, manager TP.HCM, manager Hà Nội/Đà Nẵng, ADMIN; lưu ảnh desktop/mobile, SHA và thời điểm. Không đánh dấu pass cho bước chưa chạy trên staging.

## 4. Theo dõi và rollback

- Theo dõi startup/runtime logs, lỗi 401/403 bất thường, HTTP 5xx, kết nối WSS, gửi SMTP, Cloudinary và thời gian phản hồi các API chính sau deploy.
- Nếu có lỗi P0/P1: ngừng nghiệm thu, lưu log và SHA, đưa backend về deployment đã biết ổn định bằng chức năng redeploy bản trước trên Railway. Xác nhận smoke test lại sau khi quay về.
- Nếu migration thay đổi dữ liệu/schema không tương thích với bản trước, không chỉ rollback code. Dùng bản backup đã xác minh và kế hoạch phục hồi được nhóm/hạ tầng duyệt; thử phục hồi trên bản sao trước khi đụng database chung.
- Ghi báo cáo sau cùng: SHA/PR, migration thực tế đã chạy, tên env bắt buộc, test và ảnh, known issues, người xác nhận, thời điểm backup/deploy/rollback (nếu có).
