# Mã giảm giá — checkout và quản trị

## Phạm vi

Admin quản lý tại `/admin/coupons` (menu **Mã giảm giá**). Khách xem mã ngay trong `/checkout`, không nhập hoặc tự tính số tiền giảm. Các API có kiểm tra vai trò tại server.

## Quy tắc đã chốt ngày 29/10/2026

| Mã mặc định | Điều kiện | Giảm | Lượt / khách |
| --- | --- | --- | --- |
| `DON500K` | Tiền hàng **lớn hơn** 500.000 VND; đúng 500.000 không đủ điều kiện | 10% | 1 |
| `LANDAU15` | Đơn mua hàng đầu tiên; không có đơn nào chưa hủy | 15% | 1 |
| `SPA50K` | Có lịch dịch vụ tên chứa `spa`, trạng thái `COMPLETED`, thuộc chính khách | 50.000 VND | 1 |

Mỗi khách có hạn mức riêng, không dùng chung một kho mã của cả hệ thống. Mã spa chỉ có lượt khả dụng sau khi đủ điều kiện. Mã theo giá trị đơn vẫn hiển thị lượt còn lại khi đơn hiện tại chưa đủ ngưỡng.

Lượt được giữ khi tạo đơn, không đợi giao thành công. Một đơn đang chờ xác nhận cũng giữ quyền mua lần đầu: khách không được mở nhiều đơn song song để lặp ưu đãi. Nếu đơn đó được hủy hợp lệ, quyền/lượt được xét lại. Đơn hoàn tất vẫn tiêu thụ mã. Lịch spa đang chờ, đã xác nhận, bị hủy hoặc của người khác không cấp mã spa; hoàn tất thêm nhiều lịch spa không tự cấp thêm lượt cho chiến dịch mặc định.

Chỉ một mã cho mỗi đơn. Server gợi ý mã đủ điều kiện có số tiền giảm lớn nhất; frontend tự chọn gợi ý lần đầu. Khách được đổi hoặc bỏ chọn. Khi bằng tiền giảm, ưu tiên mã có ID nhỏ hơn để gợi ý ổn định. Phần trăm làm tròn xuống đơn vị VND; mức giảm không vượt tiền hàng, tổng phải trả không âm. Không có phí vận chuyển mới trong thay đổi này.

Hủy trực tiếp hoặc yêu cầu hủy đã được chấp nhận hoàn lại lượt. Chỉ gửi yêu cầu hoặc bị từ chối không hoàn lượt. Lịch sử và biên lai vẫn giữ nguyên snapshot ưu đãi của đơn bị hủy, cùng số tiền thực tế của đơn để đối soát hoàn tiền mô phỏng.

## Quản trị

Admin thêm mã với mã định danh duy nhất, tên, đối tượng, mức giảm phần trăm/số tiền, ngưỡng tiền hàng và số lượt mỗi khách. Mã lần đầu chỉ cho một lượt. Có thể tạm ngưng/kích hoạt; không sửa chính sách hoặc xóa chiến dịch đã dùng để tránh làm thay đổi lịch sử.

Ba mã mặc định được thêm khi khởi động nếu chưa có mã cùng tên; khởi động lại không tạo bản sao, không cấp lại lượt, không kích hoạt lại mã đã tạm ngưng và không reseed user/catalog.

## API

| Endpoint | Vai trò | Nội dung |
| --- | --- | --- |
| `GET /api/admin/coupons` | ADMIN | Danh sách chiến dịch |
| `POST /api/admin/coupons` | ADMIN | Thêm chiến dịch |
| `PATCH /api/admin/coupons/{id}/active` | ADMIN | Tạm ngưng/kích hoạt |
| `POST /api/coupons/quote` | CUSTOMER | Báo giá giỏ của người đăng nhập; danh sách lượt/điều kiện và gợi ý |
| `POST /api/orders` | CUSTOMER | Nhận thêm `couponId` (không bắt buộc), `expectedTotalAmount` (đối soát báo giá) |

Quote nhận `branchId`, `itemIds` tùy chọn và một `couponId` hoặc null. Không nhận user ID, số tiền hàng hoặc tiền giảm do client quyết định. Khi tạo đơn, backend tính lại từ catalog và kiểm tra lượt/điều kiện một lần nữa. Nếu báo giá đã thay đổi thì yêu cầu khách xác nhận lại, không âm thầm đặt đơn với số tiền khác.

`orders` lưu `subtotal_amount`, `discount_amount`, `coupon_id`, `coupon_code`, `coupon_name`; `total_amount` là số sau giảm. Response dùng camelCase tương ứng, không trả couponId nội bộ của đơn nếu không cần. Đơn cũ không có snapshot được đọc như không giảm, giữ nguyên tổng lịch sử.

Checkout giữ khóa giỏ của khách và dùng `READ_COMMITTED` để lượt đã dùng được nhìn thấy sau khi chờ khóa MySQL. Hai tab dùng hai nhóm sản phẩm khác nhau không thể tiêu cùng lượt. Idempotency hiện có đảm bảo gửi lại cùng xác nhận trả đơn đầu tiên, không trừ thêm mã hoặc tồn kho.

## Triển khai và rollback

Trước push lên main có auto-deploy, kiểm tra database Railway và tạo bản sao lưu. Với production schema `validate`, phải áp dụng [V4_9_0__discount_coupons_mysql.sql](../../data-pipeline/migrations/V4_9_0__discount_coupons_mysql.sql) trước deploy. Không tự đổi sang `ddl-auto=update` để bỏ qua migration.

`scripts/Invoke-CouponDatabase.ps1` hỗ trợ `Inspect`, `Backup`, `Migrate`, `RestoreVerify`, `DropTest`; đọc cấu hình riêng, không chạy nội dung file cấu hình như script và không in credential. Giữ backup dưới `backups/` (đã Git-ignore), không đưa lên Git hoặc gửi kèm báo cáo và không để dưới `target/` vì `mvn clean` sẽ xóa. `Migrate` chỉ chạy khi có SQL backup và manifest đúng SHA-256; sau migration kiểm tra đủ cột và tổng/count đơn cũ không thay đổi. `RestoreVerify` tạo database có tiền tố riêng, phục hồi các bảng/dữ liệu rồi đối chiếu số dòng/tổng tiền và chạy migration hai lần. `DropTest` chỉ xóa tên database tạm đúng mẫu, có marker sở hữu và không phải database ứng dụng đang kết nối. Backup không xuất view/routine/trigger/event; không gọi đây là diễn tập phục hồi toàn bộ hạ tầng.

SQL bổ sung có thể chạy lặp lại. Không xóa bảng/cột để rollback. Nếu rollback code, giữ schema bổ sung; với các đơn đã dùng ưu đãi, không chạy phiên bản cũ làm sai lệch việc hiển thị biên lai. Có thể tạm ngưng các mã để dừng áp dụng mới trong lúc xử lý.

## Kiểm thử có thể chạy lại

`CouponCheckoutIntegrationTest`: tiền giảm, ngưỡng 500k, lượt cá nhân, mã lần đầu/spa, hết lượt, trạng thái hủy/hoàn tiền, snapshot, idempotency, chống sửa tiền và phân quyền. `CheckoutConcurrencyTest` có thêm ca hai tab tranh một lượt trên hai nhóm item khác nhau.

`scripts/check-coupons-ui.cjs` chạy Chrome headless thật trên server local dùng database demo bỏ đi; mặc định chỉ chấp nhận localhost cổng 18090. Kiểm tra chọn/đổi/bỏ mã, báo giá, hộp xác nhận, đặt đơn, biên lai và lượt còn lại; admin thêm/tạm ngưng mã; viewport 320, 375, 390, 768, 1024, 1440 px; không có exception JavaScript chưa bắt. Ảnh thực và kết quả ở `target/coupon-ui/`. Không chạy script này trên database Railway.

`scripts/verify-coupons-live.cjs` chờ bản checkout mới, đăng nhập các vai trò thử nghiệm, kiểm tra chiến dịch/quyền/đọc đơn và báo giá giỏ hiện có. Không thêm chiến dịch, sản phẩm, đơn hoặc hủy đơn production. Script không thay thế việc xem log Railway nếu deployment chưa ra bản mới.

## Bàn giao Railway ngày 29/10/2026

Code tính năng `9995303` và `1395f9e` đã ở `main`; migration V4.9 đã chạy trên database đích sau backup. Hai lượt suite H2: 120 ca/lượt, 0 failure/error, 1 skip cũ; thêm 11 ca coupon trên bản sao MySQL thật: 0 failure/error/skip. Khôi phục 22 bảng/383 dòng và đối chiếu tổng tiền qua; database test tạm đã được xóa, backup vẫn giữ riêng.

GitHub Deployment `7404508530` cho `1395f9e` đã được Railway nhận tự động nhưng vẫn `in_progress` khi chốt bàn giao; trang public `/checkout` vẫn chưa có `checkout-coupons`. Chưa xác nhận phát hành thành công và chưa tạo đơn giảm giá trên production. Không xác định nguyên nhân deployment kéo dài khi chưa có log build/deploy hoặc kết nối tài khoản Railway.

Người có quyền Railway tiếp tục:

1. Mở đúng backend service của `pawconnect-ute.up.railway.app`, tab Deployments, chọn bản mới nhất của `main` (commit tài liệu sau hai commit tính năng dùng cùng source ứng dụng). Xem log Build và Deploy, xác định đang xếp hàng, đang build, khởi động lại hay thất bại; không suy ra nguyên nhân chỉ từ `in_progress` trên GitHub.
2. Nếu job kẹt, xử lý theo trạng thái/log rồi redeploy bản `main` đã kiểm thử. Không cần thêm biến môi trường cho coupon; không đổi `ddl-auto` để né migration và không chạy lại user/catalog seed. Nếu cần phối hợp sửa, gửi lỗi đầu tiên trong log và SHA; che mọi credential.
3. Sau khi deployment thành công, hard refresh website. Admin mở `/admin/coupons`, kiểm tra `DON500K`, `LANDAU15`, `SPA50K` (mỗi loại 1 lượt/khách). Initializer tự thêm chiến dịch khi chưa có, không ghi đè mã đã tồn tại.
4. Dùng tài khoản/sản phẩm demo được phép: checkout có mã, chọn một mã, tổng giảm đúng, biên lai khớp và lần mua sau cập nhật lượt. Tài khoản đã có đơn chưa hủy không đủ điều kiện mua lần đầu; spa phải là lịch đã hoàn tất, không phải chỉ mới đặt lịch. Hủy hợp lệ phải trả lượt; yêu cầu hủy đang chờ hoặc bị từ chối không trả lượt.
5. Kiểm tra lại desktop và mobile 390 px, giữ kết quả smoke/sign-off của nhóm. Không dùng mã OTP/email hoặc cổng thanh toán thật làm điều kiện cho tính năng này; chúng không được thay đổi trong đợt coupon.
