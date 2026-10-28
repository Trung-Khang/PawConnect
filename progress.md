# Tiến độ dự án - Nhóm TV1 (Thương mại bán hàng & Dịch vụ chăm sóc)

| Ngày | Chức năng | File/Module | API | Test | Trạng thái | Bàn giao |
| ---- | --------- | ----------- | --- | ---- | ---------- | -------- |
| 05/09/2026 | Thiết lập | Khởi tạo cấu trúc dự án (Entity, Repo, Service, Controller, DTO, Templates) |     |      | ✅ |          |
| 05/09/2026 | Hoàn thiện code | Implement toàn bộ Service, Controller và UI (Shop, Booking, Cloudinary mock) | Full | Unit | ✅ |          |
| 28/10/2026 | Tự động NO_SHOW (STT 8) | Viết cron @Scheduled quét lịch hẹn quá 2h sang NO_SHOW, lưu lý do & gửi thông báo chuông | POST /api/bookings/auto-no-show | Unit & E2E | ✅ Hoàn thành | Đã verify |
| 28/10/2026 | Xem lịch trống (STT 7) | Giao diện Time Slots Picker (07:00 - 20:00), hiển thị trực quan Còn trống / Đã kín / Đã qua | GET /api/bookings/busy-slots | E2E & UI | ✅ Hoàn thành | Đã verify |
| 28/10/2026 | Ma trận kiểm thử (STT 9) | Chạy Regression Matrix 19/19 test cases cho 5 vai trò (Customer, Manager 3 chi nhánh, Admin) | Auth, Bookings, Orders, Notifs | Matrix 100% | ✅ Hoàn thành | 19/19 PASS |
