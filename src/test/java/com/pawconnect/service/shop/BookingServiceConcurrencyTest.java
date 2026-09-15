package com.pawconnect.service.shop;

import com.pawconnect.entity.Branch;
import com.pawconnect.entity.ServiceBooking;
import com.pawconnect.entity.ServiceType;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.ServiceBookingRepository;
import com.pawconnect.repository.ServiceTypeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class BookingServiceConcurrencyTest {

    @Autowired
    private ServiceBookingRepository bookingRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;

    @Autowired
    private BookingService bookingService;

    private Long bookingId;

    @BeforeEach
    public void setup() {
        Branch branch = Branch.builder().name("Test Branch").address("Address").build();
        branch = branchRepository.save(branch);

        ServiceType serviceType = ServiceType.builder()
                .name("Spa")
                .duration(60)
                .price(new BigDecimal("150000"))
                .build();
        serviceType = serviceTypeRepository.save(serviceType);

        ServiceBooking booking = ServiceBooking.builder()
                .userId(1L)
                .branch(branch)
                .serviceType(serviceType)
                .bookingTime(LocalDateTime.now().plusDays(1))
                .status("PENDING")
                .build();
        booking = bookingRepository.save(booking);
        bookingId = booking.getId();
    }

    @AfterEach
    public void cleanup() {
        if (bookingId != null) {
            bookingRepository.deleteById(bookingId);
        }
    }

    @Test
    public void testConcurrentUpdateBooking_OptimisticLocking() throws InterruptedException {
        int numberOfThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        AtomicInteger optimisticLockExceptions = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            executorService.execute(() -> {
                try {
                    // Cả 2 Manager cùng cố gắng cập nhật lịch này cùng lúc (vd 1 duyệt, 1 huỷ)
                    String newStatus = threadId == 0 ? "APPROVED" : "REJECTED";
                    bookingService.updateBookingStatus(bookingId, newStatus);
                } catch (ObjectOptimisticLockingFailureException e) {
                    optimisticLockExceptions.incrementAndGet();
                } catch (Exception e) {
                    if (e.getCause() instanceof ObjectOptimisticLockingFailureException) {
                         optimisticLockExceptions.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // MONG ĐỢI 1 TRONG 2 MANAGER SẼ BỊ BÁO LỖI VÌ NGƯỜI KIA ĐÃ CẬP NHẬT TRƯỚC
        assertEquals(1, optimisticLockExceptions.get(), "Phải có đúng 1 giao dịch bị chặn bởi Optimistic Locking");
    }
}
