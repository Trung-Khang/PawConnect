package com.pawconnect.scheduler;

import com.pawconnect.service.shop.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingNoShowScheduler {

    private final BookingService bookingService;

    /**
     * Quét định kỳ mỗi 15 phút (900.000 ms), bắt đầu sau khi ứng dụng khởi động 30 giây.
     * Tự động chuyển các lịch hẹn (PENDING, CONFIRMED) quá giờ hẹn 2 tiếng sang trạng thái NO_SHOW.
     */
    @Scheduled(fixedDelay = 900000, initialDelay = 30000)
    public void scanAndMarkNoShow() {
        try {
            int count = bookingService.autoUpdateNoShowBookings();
            if (count > 0) {
                log.info("[CRON NO_SHOW] Đã tự động cập nhật {} lịch hẹn quá giờ sang NO_SHOW", count);
            }
        } catch (Exception e) {
            log.error("[CRON NO_SHOW] Lỗi khi quét tự động cập nhật NO_SHOW: {}", e.getMessage(), e);
        }
    }
}
