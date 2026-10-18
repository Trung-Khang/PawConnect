# PawConnect - Tổng hợp trạng thái, kế hoạch và bàn giao

**Cập nhật:** 18/10/2026  
**Phạm vi:** tài liệu nguồn sự thật cho tình trạng dự án, nghiệp vụ chat, các phần đã làm, phần còn thiếu và checklist bàn giao.  
**Thay thế:** `emergency_task.md` và `Prompt_KeHoach_HoanThien.md`.

## 1. Tóm tắt trạng thái

PawConnect đã có nền tảng Spring Boot cho cửa hàng, thú cưng, dịch vụ, nhận nuôi, tài khoản, giỏ hàng, đơn hàng quản trị và chat realtime. Giao diện khách hàng cùng các màn quản trị cơ bản đã hoạt động. Phần chat đã được điều chỉnh theo nghiệp vụ mới: **mỗi khách hàng chỉ có một hộp thư/cuộc trò chuyện duy nhất**, thay vì tách hai tab hoặc tạo nhiều cuộc trò chuyện theo từng sản phẩm.

Tuy nhiên, dự án **chưa hoàn tất luồng thương mại điện tử cho khách hàng**: nút “Mua ngay” hiện chỉ thêm sản phẩm vào giỏ và mở giỏ hàng; chưa có checkout, thanh toán thật, biên lai, lịch sử mua hàng hoàn chỉnh hoặc quy tắc trạng thái đơn hàng phía khách. Triển khai thành website công khai và nghiệm thu mobile cũng chưa hoàn thành.

Kết quả kiểm tra mã nguồn gần nhất ngày 18/10/2026:

- `mvn test`: **68 test chạy, 0 lỗi, 0 error, 1 skipped**.
- Test render giao diện nhận nuôi đã được cập nhật để chấp nhận URL JavaScript có version cache (`/js/header.js?v=...`), không làm thay đổi nghiệp vụ.
- Các kiểm thử thủ công vẫn cần được TV1 thực hiện trên trình duyệt và dữ liệu Railway trước khi chốt demo/release.

## 2. Kiến trúc và công nghệ đang dùng

| Lớp | Thành phần hiện có |
| --- | --- |
| Backend | Java 17, Spring Boot 3.4.4, Spring MVC, Spring Data JPA, Spring Security |
| Dữ liệu | MySQL trên Railway cho môi trường dùng chung; H2 in-memory cho đa số test |
| Realtime | WebSocket/STOMP kết hợp SockJS; REST dùng cho tải trạng thái/lịch sử dự phòng |
| Giao diện | Thymeleaf, JavaScript thuần, CSS theo từng màn hình, Font Awesome |
| Ảnh | Cloudinary cho ảnh tải lên khi biến môi trường được cấu hình; dữ liệu seed có ảnh cục bộ/URL sẵn có |
| Xác thực | JWT/session theo contract hiện có; OTP xác minh email và đặt lại mật khẩu |
| Triển khai | Có profile production và cấu hình bằng biến môi trường; chưa có website public được nghiệm thu |

Tài liệu contract chi tiết vẫn được giữ riêng tại `auth_token_contract.md`, `auth_otp_setup.md`, `data_contract.md`, `data_handoff.md`, `production_configuration.md` và `Workflow.md`. Không đưa secret, mật khẩu hoặc URL private vào tài liệu này.

## 3. Những phần đã hoàn thành

### 3.1. Nền tảng dữ liệu, tài khoản và quản trị

- Đã chuẩn hóa entity/repository/service/controller cho người dùng, vai trò, chi nhánh, danh mục, sản phẩm, puppy listing, giỏ hàng, đơn hàng, dịch vụ, nhận nuôi và chat.
- Đã có đăng ký, đăng nhập, xác minh OTP, quên/đặt lại mật khẩu, khóa/mở khóa tài khoản và chỉnh sửa hồ sơ/ảnh đại diện.
- Header dùng chung đã hỗ trợ nhận diện khách hàng, nhân viên và quản trị viên; tên hiển thị của ba tài khoản chi nhánh là **Nhân viên TP.HCM**, **Nhân viên Hà Nội**, **Nhân viên Đà Nẵng**. Tài khoản quản trị vẫn hiển thị **Quản trị PawConnect**. Email và mật khẩu đăng nhập không bị đổi chỉ vì đổi tên hiển thị.
- Đã có trang quản lý tài khoản cho admin, quản lý sản phẩm/puppy listing, quản lý bài và đơn nhận nuôi, quản lý đơn hàng phía nhân viên/quản trị.
- Railway MySQL đã có các migration chat V4.1-V4.4 dùng để đổi cột đọc tin, bổ sung takeover/context và gộp hội thoại theo khách. Migration V4.5 trong source đổi tên hiển thị các manager; chỉ đánh dấu hoàn tất trên Railway sau khi đã chạy migration đó tại database dùng chung.

### 3.2. Cửa hàng và nhận nuôi

- Danh sách cửa hàng hỗ trợ lọc theo từ khóa, chi nhánh và danh mục bằng nút **Tìm kiếm**; không còn tự gọi lại danh sách theo từng ký tự nhập. Các request tìm kiếm cũ có thể bị hủy để tránh kết quả đến muộn ghi đè kết quả mới.
- Card sản phẩm/puppy đã căn bằng vùng tiêu đề và đưa nhóm giỏ hàng/mua ngay về đáy card để tên ngắn không làm nút bị cao hơn card khác.
- Modal chi tiết sản phẩm và puppy listing vẫn là trải nghiệm chính, không phải một product page tách riêng.
- Context tư vấn mới của sản phẩm/puppy tạo deep-link dạng `/shop?itemType=product&itemId=<id>#shop-section` hoặc `/shop?itemType=puppy&itemId=<id>#shop-section`. Khi nhấn card context, trang cửa hàng đọc query, gọi API chi tiết và tự mở đúng modal. Nếu item bị xóa/ngừng bán phải thông báo rõ, không mở modal rỗng.
- Link adoption tiếp tục dùng `/adoptions/<postId>` và đi trực tiếp đến hồ sơ nhận nuôi.
- Context chat cũ chỉ lưu `/shop#shop-section`, không có ID item nên không thể tự suy ra đúng sản phẩm một cách an toàn. Chỉ context tạo mới mới có deep-link chính xác.
- Đã có bài đăng adoption, hồ sơ chó, đơn đăng ký nhận nuôi, trạng thái đơn cơ bản và luồng khách trao đổi với nhân viên từ hồ sơ adoption.

### 3.3. Chat: nghiệp vụ và giao diện hiện tại

#### Quy tắc nghiệp vụ đã chốt

1. Một `CUSTOMER` chỉ có **một conversation/inbox duy nhất**. Ràng buộc dữ liệu là `uk_conversation_customer`; không phân theo tab Tư vấn/Nhận nuôi và không tạo conversation mới theo từng item.
2. Sản phẩm, puppy listing và adoption chỉ tạo **context message** trong inbox này. Context gồm tiêu đề, mô tả ngắn, ảnh khi có thể, deep-link và câu tự động nêu rõ khách đang quan tâm sản phẩm hoặc nhận nuôi bé cún.
3. Khách nhấn **Tư vấn** trong modal hoặc **Chat với nhân viên** trên hồ sơ nhận nuôi sẽ mở widget, gửi/tái sử dụng context rồi hiển thị cuộc trò chuyện đó. Không được tạo bản sao conversation khi khách thao tác lần hai.
4. Admin và nhân viên quản lý đang hoạt động có thể vào danh sách inbox, mở và trả lời. `assignedStaffId` là dấu hiệu nhân viên đầu tiên đang tiếp nhận để các nhân viên khác nhận biết; đây không phải khóa độc quyền ngăn người có quyền hỗ trợ.
5. Tin nhắn chưa đọc của khách trong inbox nhân viên phải nổi bật và được sắp theo thời gian tin mới nhất. Một conversation chỉ xuất hiện một dòng; khách gửi tiếp khi chưa xem chỉ cập nhật/in đậm/sắp xếp lại, không sinh dòng thứ hai.
6. Quyền đọc/gửi kiểm tra phía server. Không tin tưởng điều kiện ẩn/hiện trên giao diện.

#### Giao diện đang áp dụng

- Widget khách chỉ hiển thị một cuộc trò chuyện, không có thanh danh sách hội thoại/two tabs ở bên trái.
- Widget nhân viên/quản trị mở đầu bằng danh sách inbox cần xử lý, sau đó có nút **Quay lại** để quay về danh sách.
- Khung chat gồm header cam, biểu tượng cún, nền ảnh, avatar người gửi, bubble đối xứng khách/nhân viên, màu tên và mốc thời gian cam, ô nhập, nút emoji và nút gửi. Enter gửi tin (Shift+Enter dùng để xuống dòng nếu giao diện đang hỗ trợ textarea).
- Tên, avatar và màu bubble phải dựa trên chính sender của message; không được dùng avatar chữ cái mặc định khi người dùng đã có ảnh.
- Nhãn “Nhân viên” bên cạnh tên đã bỏ. Các tên nhân viên theo chi nhánh nêu ở mục 3.1 được dùng trực tiếp trong chat.

#### Ổn định và realtime đã xử lý

- Widget dùng WebSocket/STOMP để nhận cập nhật realtime; REST nạp conversation/history khi mở hoặc khi cần đồng bộ lại.
- Luồng mở tư vấn tránh gọi `refresh()` toàn widget ngay sau khi nhận conversation. Conversation được upsert từ response và lịch sử chỉ được lấy một lần có kiểm soát.
- Có trạng thái `openingConversation` và `historyLoadingId` để chống request lịch sử trùng, tránh nhấp nháy “Đang tải lịch sử...” và giảm nguy cơ ghi đè nội dung khách đang soạn.
- URL cache version cho `chat-widget.js`, `chat-widget.css`, `header.js` và CSS shop giúp trình duyệt nhận bản giao diện mới sau deploy. Thay đổi Java/backend vẫn cần build/restart service; chỉ đổi static file thường cần hard refresh nếu server đang chạy bản cũ.

#### Rủi ro còn cần nghiệm thu

- Cần kiểm thử nhiều tab/trình duyệt và mất-kết-nối mạng để xác nhận reconnect không nhân đôi message/context, không mất draft và không gửi lặp.
- Khi đưa lên production, cần chạy WebSocket trên HTTPS/WSS, cấu hình origin/domain chuẩn và theo dõi retry; không xem polling liên tục là cơ chế realtime chính.
- Cần đo thời gian mở widget/tư vấn với Railway thật. Mục tiêu là không reload toàn chatbox và chỉ có một tải lịch sử cho thao tác mở một context.

## 4. Bàn giao TV3 - Hoàn thiện backend, mobile và release

**Owner:** TV3. Mục này thay thế toàn bộ danh sách “chưa hoàn tất” trước đây bằng đầu việc có tiêu chí nghiệm thu rõ ràng. TV3 có thể tách thành nhiều commit/PR, nhưng không được thay đổi contract chat một khách - một inbox hoặc làm giảm phân quyền hiện có để test dễ hơn.

### 4.1. Checkout, mua hàng và quản lý đơn

Hiện tại nút **Mua ngay** chỉ thêm item vào giỏ và mở giỏ. Entity/API order cùng logic trừ tồn kho đã có, nhưng luồng mua phía khách chưa hoàn chỉnh. TV3 thực hiện:

- [ ] Thiết kế màn checkout Bootstrap 5 cho customer: thông tin nhận hàng, chi nhánh, danh sách item, số lượng, giá, tổng tiền, phí (nếu áp dụng), phương thức thanh toán và nút xác nhận.
- [ ] Tạo order từ cart ở server-side transaction. Không nhận `userId`, giá hay tổng tiền từ client; lấy user từ JWT và tính lại giá/tổng từ dữ liệu server.
- [ ] Lưu snapshot tối thiểu cho order item: tên item, đơn giá tại thời điểm mua và số lượng, để lịch sử không đổi khi catalog bị sửa/xóa.
- [ ] Trừ hoặc giữ tồn kho đúng một lần, rollback toàn bộ khi có item hết hàng, xóa/cập nhật cart sau khi order được tạo thành công và chống tạo đơn trùng khi khách nhấn nhiều lần.
- [ ] Chuẩn hóa trạng thái và transition: `PENDING_PAYMENT`, `PAID`, `CONFIRMED`, `PREPARING`, `SHIPPING` hoặc `READY_FOR_PICKUP`, `COMPLETED`, `CANCELLED`, `REFUNDED`. Mỗi transition phải kiểm tra trạng thái trước đó, role, branch và lưu audit tối thiểu ai/thời điểm/lý do.
- [ ] Bổ sung trang **Đơn mua của tôi**, chi tiết đơn, hủy đơn khi còn hợp lệ và thông báo trạng thái. Customer chỉ đọc/sửa đơn của chính mình; branch manager chỉ xử lý đơn chi nhánh mình; admin xem toàn hệ thống.
- [ ] Chốt phương thức thanh toán với nhóm trước khi code: tối thiểu hỗ trợ COD hoặc mô phỏng thanh toán cho demo. Nếu tích hợp cổng thật như VNPay, MoMo hoặc Stripe, callback/webhook phải xác thực chữ ký, idempotent, không tin trạng thái trả về từ browser và không commit credential.

**Nghiệm thu:** customer checkout được từ cart, chỉ một order được tạo cho một lần xác nhận, stock/tổng tiền đúng, trạng thái không nhảy sai và các role không truy cập/sửa order vượt quyền.

### 4.2. Bill, xác nhận và in ấn

- [ ] Tạo trang xác nhận đơn sau checkout và trang chi tiết bill/biên lai cho customer và staff hợp lệ.
- [ ] Bill phải có mã đơn, thời gian, khách hàng, chi nhánh, danh sách item, số lượng, đơn giá snapshot, tổng tiền, phương thức và trạng thái thanh toán/đơn.
- [ ] Có nút in bằng CSS `@media print`; chỉ bổ sung PDF khi có thư viện/đầu ra ổn định và kiểm thử được. Không gọi là hóa đơn VAT nếu nhóm chưa xây dữ liệu hóa đơn hợp lệ.
- [ ] Bill chỉ xem được bởi chủ đơn, branch manager đúng chi nhánh hoặc admin. Không để ID tuần tự trong URL trở thành cách truy cập dữ liệu người khác.

**Nghiệm thu:** in preview đúng khổ A4, không in header/chat/navigation thừa, thông tin khớp order server-side và kiểm tra quyền trả về `403` khi truy cập trái phép.

### 4.3. Phân quyền, branch isolation và dữ liệu còn thiếu

- [ ] Sửa `BookingService` để branch manager chỉ cập nhật booking thuộc chi nhánh của mình; thêm test cho nhánh bị từ chối.
- [ ] Chốt và áp dụng rule xóa product theo đặc tả: chỉ `ADMIN` được xóa, hoặc cập nhật tài liệu/ERD nếu nhóm chính thức chọn cho branch manager xóa item của chính chi nhánh. Không chỉ đổi UI, phải kiểm tra server-side.
- [ ] Bổ sung quản lý chi nhánh cấp admin nếu đây vẫn là phạm vi đề tài: danh sách, tạo, sửa, vô hiệu hóa/xóa có kiểm tra dữ liệu phụ thuộc.
- [ ] Rà soát mapping/cascade, foreign key, index và ERD theo source thực tế, đặc biệt mô hình tách `PuppyListing` khỏi `Product` thay cho `isBreedingDog`.
- [ ] Đối soát migration Railway, gồm V4.5, theo quy trình backup/xác nhận trước khi chạy; không reseed shared database đang có dữ liệu demo.

**Nghiệm thu:** mọi endpoint nhạy cảm có test `CUSTOMER`/manager sai chi nhánh/admin; không có API nào chỉ dựa vào việc ẩn nút ở frontend.

### 4.4. Site mobile bằng Bootstrap 5

Bootstrap 5 là công nghệ bắt buộc theo tên đề tài. TV3 phải tích hợp và **sử dụng thực tế**, không chỉ thêm một thẻ CDN.

- [ ] Đưa Bootstrap 5 CSS và Bootstrap Bundle JS vào layout/header dùng chung bằng phiên bản cố định; tránh nạp trùng trên từng trang.
- [ ] Dùng class/component Bootstrap trong các màn chính: `container`, `row`, `col-*`, `navbar`, `offcanvas`, `btn`, `form-control`, `modal`, `toast`, `spinner` hoặc tương đương phù hợp.
- [ ] Làm responsive cho header/menu, shop/filter/card/modal, cart/checkout/bill, service booking, adoption, profile/auth, chat widget và các màn quản trị trong phạm vi demo.
- [ ] Giữ màu sắc và nhận diện PawConnect hiện có; không thay toàn bộ giao diện bằng theme Bootstrap mặc định hoặc phá CSS chat đang ổn định.
- [ ] Kiểm tra ít nhất viewport 320px, 375px, 390px, 768px, 1024px và desktop. Không có text/nút/modal bị tràn, che nhau hoặc không thao tác được.

**Nghiệm thu:** có bằng chứng source dùng Bootstrap 5 thực tế và các luồng khách chính chạy được trên mobile, không chỉ co nhỏ giao diện desktop.

### 4.5. Production, Railway và vận hành

- [ ] Deploy backend lên Railway/hosting được nhóm chốt, dùng profile production, MySQL private, HTTPS và WebSocket qua WSS.
- [ ] Cấu hình biến môi trường cho database, JWT, SMTP, Cloudinary và allowed origin; không commit `.env`, password, token hoặc URL private.
- [ ] Kiểm tra Cloudinary upload/xóa ảnh và SMTP/OTP thật trên staging/production; ghi nhận lỗi rõ nếu provider không khả dụng.
- [ ] Viết runbook ngắn: deploy, migration, smoke test, backup/rollback, log cần kiểm tra và cách quay về bản release trước.
- [ ] Thiết lập smoke test sau deploy cho auth, shop, booking, adoption, cart/order, chat WebSocket và upload ảnh.

**Nghiệm thu:** môi trường public/staging mở được qua HTTPS, API/auth/WebSocket hoạt động đúng origin, không lộ secret và có cách rollback đã thử nghiệm.

### 4.6. Checklist kiểm thử bắt buộc của TV3

- [ ] `mvn clean test` không có failure/error; bổ sung test cho checkout, order state transition, stock race, idempotency, booking cross-branch và authorization.
- [ ] Test API bằng ít nhất customer, manager TP.HCM, manager Hà Nội/Đà Nẵng và admin; kiểm tra `401`/`403` cho mọi truy cập sai.
- [ ] Test đồng thời: hai customer checkout item stock 1; nhấn xác nhận liên tiếp; payment callback bị gửi lại; hai staff đổi trạng thái đơn cùng lúc.
- [ ] Test bill: chủ đơn/staff đúng branch/admin xem được; user khác và manager sai branch bị chặn.
- [ ] Test responsive bằng DevTools/device thật; kiểm tra console, network, loading/error/empty states và hard refresh để loại trừ cache cũ.
- [ ] Test chat regression: một customer một inbox, context/deep-link đúng, realtime/reconnect không nhân đôi hoặc mất message. Không refactor chat ngoài việc cần thiết cho Bootstrap/mobile.
- [ ] Báo cáo bàn giao gồm commit/PR, migration đã chạy, biến môi trường cần có (chỉ tên biến), kết quả test, ảnh desktop/mobile, known issues và hướng rollback.

**Điều kiện hoàn thành mục 4:** toàn bộ checklist liên quan được đánh dấu, có commit/evidence, TV1 regression UI pass và không có lỗi P0/P1 về thanh toán, stock, quyền, chat hoặc deploy.

## 5. Bàn giao kiểm thử cho TV1

TV1 chịu trách nhiệm regression giao diện và kiểm thử thủ công trên server local, sau đó lặp lại với Railway khi service sẵn sàng. Không sửa/hạ mức xác thực backend để làm test “qua”. Mỗi lỗi cần ghi URL, role, dữ liệu test, bước tái hiện, kết quả mong đợi/thực tế, ảnh/video và console/network nếu có.

### 5.1. Ma trận tài khoản

Kiểm thử tối thiểu bằng: khách chưa đăng nhập, `CUSTOMER`, Nhân viên TP.HCM, Nhân viên Hà Nội, Nhân viên Đà Nẵng và Quản trị PawConnect. Xác nhận riêng quyền hiển thị menu và quyền API; menu bị ẩn không đồng nghĩa endpoint đã an toàn.

### 5.2. Checklist chat bắt buộc

- Khách lần đầu mở chat chỉ có một inbox; không có tab Tư vấn/Nhận nuôi hoặc thanh conversation trái.
- Khách nhấn Tư vấn của product, puppy và Chat với nhân viên ở adoption: context đúng, không tạo conversation thứ hai, câu quan tâm đúng loại item.
- Nhấn context product/puppy mới mở đúng modal từ deep-link; context adoption mở đúng bài chi tiết. Với context cũ `/shop#shop-section`, ghi nhận đây là giới hạn dữ liệu cũ, không đánh lỗi deep-link mới.
- Gửi Enter, nút gửi, emoji; thử gửi nhanh liên tiếp, mở/đóng widget, refresh trang và mất/reconnect mạng. Không mất tin, không nhân đôi tin, không phải spam nút gửi, không quay lại loading lặp vô hạn.
- Avatar, tên, màu bubble, mốc thời gian và text wrap đúng cho khách/nhân viên; không còn badge “Nhân viên”. Kiểm tra nút Quay lại không vỡ kiểu chữ.
- Đăng nhập nhân viên/admin: danh sách inbox chỉ một dòng mỗi khách, tin chưa đọc in đậm/sắp xếp mới nhất, mở inbox đánh dấu đã xem, khách gửi thêm thì conversation được đẩy đúng vị trí. Kiểm tra marker nhân viên đang tiếp nhận hiển thị cho các staff còn lại.
- Dùng ít nhất hai phiên trình duyệt để xác nhận cập nhật realtime và phân quyền đọc/gửi phía server.

### 5.3. Checklist shop, adoption và layout

- `/shop`: gõ từ khóa không tự reload/list lại trước khi bấm **Tìm kiếm** hoặc Enter; đổi branch/category rồi tìm, spam nút tìm kiếm, clear keyword và kiểm tra loading/error/empty state.
- Kiểm tra card tên ngắn/dài có nút giỏ hàng và Mua ngay cùng hàng. Mua ngay hiện chỉ thêm vào cart/mở cart, không ghi nhận là thanh toán thành công.
- Kiểm tra deep-link modal từ chat với product, puppy bị xóa/ngừng bán và quyền khách/staff/admin.
- `/adoptions`, `/adoptions/<id>`, `/adoptions/my-applications`, `/adoptions/manage`: hiển thị theo role, tạo/gửi/quản lý đơn, ảnh, trạng thái và chat context.
- `/profile`, trang auth, `/admin/products`, `/admin/orders`, `/admin/users`: responsive, không lộ hành động vượt quyền, không lỗi console/API.
- Thử viewport desktop và mobile thông dụng; ảnh, modal, chat, header, form và nút không được tràn/che nhau.

### 5.4. Tiêu chí pass và cách báo cáo

- Pass khi không có lỗi blocker/critical, không mất hoặc gửi trùng tin/đơn, không có bypass quyền, và không có crash/console error lặp lại.
- Lỗi chat/data/payment/quyền là ưu tiên P0/P1. Lỗi căn chỉnh hoặc copy là P2/P3 nhưng vẫn phải liệt kê.
- Báo cáo mỗi task theo mẫu: `Mục tiêu - role/data - bước test - kết quả - bằng chứng - lỗi/risks - file/endpoint liên quan - đề xuất người xử lý`.

## 6. Thứ tự ưu tiên tiếp theo

1. **TV3:** thực hiện mục 4 theo thứ tự checkout/order, phân quyền backend, bill, Bootstrap 5/mobile, rồi staging/release. Chốt phương thức thanh toán và chính sách hủy/hoàn tiền trước khi code.
2. **TV1:** hoàn tất checklist regression chat/shop/adoption ở mục 5 và retest các thay đổi TV3; ghi rõ lỗi để sửa trước demo.
3. **TV2 + TV1:** nghiệm thu performance chat/deep-link, bổ sung E2E/reconnect test theo phạm vi chat và xử lý catalog seed debt. Không thay đổi contract chat đã chốt.
4. **TV3/hạ tầng:** đối soát Railway migration, secrets, SMTP/Cloudinary, HTTPS/WSS, smoke test và deploy/rollback guide không chứa secret.
5. **Cả nhóm:** chỉ xác nhận staging/public hoặc release demo sau khi checklist TV3 và regression TV1 đều pass.

## 7. Quy tắc vận hành và bàn giao

- Không chạy migration seed hoặc migration phá dữ liệu trên Railway shared database nếu chưa backup/xác nhận owner.
- Không commit file hướng dẫn local, prompt cá nhân, token, mật khẩu, URL database private hoặc file `.env` có secret.
- Không coi test H2 là bằng chứng rằng Railway MySQL, WebSocket hay Cloudinary production đã hoạt động.
- Khi sửa chat, phải giữ contract “một khách - một inbox”, context message idempotent, dữ liệu sender/avatar chính xác và kiểm tra realtime trước khi merge.
- Mỗi thay đổi backend phải chạy test phù hợp; mỗi thay đổi UI chat/shop phải kiểm tra ít nhất desktop, mobile và hard refresh để loại trừ cache cũ.
- Mọi trạng thái “đã xong” trong tài liệu chỉ có hiệu lực khi có commit, test/evidence và người nhận bàn giao xác nhận.

## 8. Lịch sử tài liệu

Hai tài liệu cũ được gộp vào đây để tránh trùng lặp:

- `emergency_task.md` chứa kế hoạch khẩn, hạng mục TV2, phase 7B-7D và bàn giao cũ. Phần đã hoàn thành được tổng hợp ở mục 3; các mục chưa xong được tái tổ chức tại mục 4-6.
- `Prompt_KeHoach_HoanThien.md` chứa prompt điều phối và kế hoạch migration/chat cũ. Các quy tắc chat hai-tab, phân conversation theo loại hoặc các mốc test cũ không còn là nguồn sự thật và đã được thay bằng nghiệp vụ tại mục 3.3.

