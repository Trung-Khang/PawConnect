package com.pawconnect.service.shop;

import com.pawconnect.dto.booking.BookingRequest;
import com.pawconnect.dto.booking.BookingResponse;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.ServiceBooking;
import com.pawconnect.entity.ServiceType;
import com.pawconnect.entity.User;
import com.pawconnect.exception.BusinessException;
import com.pawconnect.exception.ResourceNotFoundException;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.ServiceBookingRepository;
import com.pawconnect.repository.ServiceTypeRepository;
import com.pawconnect.repository.UserRepository;
import com.pawconnect.security.SecurityUtils;
import com.pawconnect.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final ServiceBookingRepository bookingRepository;
    private final BranchRepository branchRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    private static final List<String> VALID_STATUSES = List.of(
            "PENDING", "CONFIRMED", "ARRIVED", "COMPLETED", "NO_SHOW", "CANCELLED"
    );

    @Override
    @Transactional
    public BookingResponse createBooking(BookingRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();

        if (request.getBookingTime() == null || request.getBookingTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Thời gian đặt lịch không hợp lệ hoặc ở quá khứ. Vui lòng chọn thời gian trong tương lai.");
        }

        LocalTime timeOnly = request.getBookingTime().toLocalTime();
        if (timeOnly.isBefore(LocalTime.of(7, 0)) || timeOnly.isAfter(LocalTime.of(20, 0))) {
            throw new BusinessException("Khung giờ đặt lịch phải từ 07:00 đến 20:00 (Cửa hàng mở cửa từ 07:00 - 21:00 từ Thứ 2 đến Chủ Nhật).");
        }

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh"));
        ServiceType serviceType = serviceTypeRepository.findById(request.getServiceTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ"));

        ServiceBooking booking = ServiceBooking.builder()
                .userId(userId)
                .branch(branch)
                .serviceType(serviceType)
                .bookingTime(request.getBookingTime())
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        bookingRepository.save(booking);

        try {
            String timeStr = booking.getBookingTime().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
            String sName = booking.getServiceType() != null ? booking.getServiceType().getName() : "Dịch vụ";
            String bName = booking.getBranch() != null ? booking.getBranch().getName() : "Chi nhánh";

            // Thông báo cho khách hàng
            sendCustomerNotification(booking, "Đặt lịch dịch vụ thành công",
                    "Lịch hẹn " + sName + " tại " + bName + " lúc " + timeStr + " đã được ghi nhận. Vui lòng chờ nhân viên chi nhánh xác nhận.",
                    "SERVICE_BOOKING_CREATED", "/service");

            // Thông báo cho quản lý chi nhánh
            String customerName = "Khách hàng";
            if (booking.getUserId() != null && userRepository != null) {
                customerName = userRepository.findById(booking.getUserId()).map(User::getFullName).orElse("Khách hàng");
            }
            notifyBranchManagers(booking.getBranch().getId(),
                    "Lịch hẹn dịch vụ mới #" + booking.getId(),
                    customerName + " vừa đặt lịch " + sName + " vào lúc " + timeStr + " tại chi nhánh của bạn. Hãy kiểm tra và xác nhận.",
                    "SERVICE_BOOKING_CREATED", "/admin/services", booking.getId());
        } catch (Exception ignored) {
        }

        return mapToResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings() {
        Long userId = SecurityUtils.getCurrentUserId();
        return bookingRepository.findByUserIdOrderByBookingTimeDesc(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getManagedBookings() {
        if (SecurityUtils.hasRole("ADMIN")) {
            return bookingRepository.findAllByOrderByBookingTimeDesc().stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        }
        if (SecurityUtils.hasRole("BRANCH_MANAGER")) {
            Long branchId = SecurityUtils.getCurrentUserBranchId();
            if (branchId == null) {
                throw new AccessDeniedException("Nhân viên chưa được chỉ định chi nhánh.");
            }
            return bookingRepository.findByBranchIdOrderByBookingTimeDesc(branchId).stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        }
        throw new AccessDeniedException("Bạn không có quyền quản lý lịch hẹn dịch vụ.");
    }

    @Override
    @Transactional
    public BookingResponse updateBookingStatus(Long bookingId, String status) {
        return updateBookingStatus(bookingId, status, null);
    }

    @Override
    @Transactional
    public BookingResponse updateBookingStatus(Long bookingId, String status, String reason) {
        if (!VALID_STATUSES.contains(status)) {
            throw new BusinessException("Trạng thái lịch hẹn không hợp lệ: " + status);
        }
        ServiceBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn."));

        if (SecurityUtils.hasRole("BRANCH_MANAGER")
                && !Objects.equals(SecurityUtils.getCurrentUserBranchId(), booking.getBranch().getId())) {
            throw new AccessDeniedException("Nhân viên chỉ có thể cập nhật lịch hẹn của chi nhánh mình.");
        }

        booking.setStatus(status);
        if (reason != null && !reason.isBlank()) {
            booking.setReason(reason.trim());
        }
        bookingRepository.save(booking);

        try {
            String timeStr = booking.getBookingTime().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
            String sName = booking.getServiceType() != null ? booking.getServiceType().getName() : "Dịch vụ";
            String bName = booking.getBranch() != null ? booking.getBranch().getName() : "Chi nhánh";

            if ("CANCELLED".equals(status)) {
                String cancelReason = reason != null && !reason.isBlank() ? reason.trim() : "Chi nhánh từ chối hoặc bận lịch";
                sendCustomerNotification(booking, "Lịch hẹn dịch vụ #" + booking.getId() + " đã bị hủy/từ chối",
                        "Chi nhánh " + bName + " đã hủy/từ chối lịch hẹn " + sName + " của bạn lúc " + timeStr + ". Lý do: " + cancelReason,
                        "SERVICE_BOOKING_CANCELLED", "/service");
            } else if ("CONFIRMED".equals(status)) {
                sendCustomerNotification(booking, "Lịch hẹn dịch vụ #" + booking.getId() + " đã được xác nhận 🎉",
                        "Chi nhánh " + bName + " đã xác nhận lịch hẹn " + sName + " của bạn lúc " + timeStr + ". Hẹn gặp bạn và bé cưng nhé!",
                        "SERVICE_BOOKING_CONFIRMED", "/service");
            }
        } catch (Exception ignored) {
        }

        return mapToResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse cancelMyBooking(Long bookingId, String reason) {
        Long userId = SecurityUtils.getCurrentUserId();
        ServiceBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn."));

        if (!Objects.equals(booking.getUserId(), userId)) {
            throw new AccessDeniedException("Bạn không có quyền hủy lịch hẹn này.");
        }

        if ("COMPLETED".equals(booking.getStatus()) || "CANCELLED".equals(booking.getStatus()) || "ARRIVED".equals(booking.getStatus())) {
            throw new BusinessException("Không thể hủy lịch hẹn đã ở trạng thái: " + booking.getStatus());
        }

        String finalReason = reason != null && !reason.isBlank() ? reason.trim() : "Khách hàng tự hủy hẹn qua giao diện";
        booking.setStatus("CANCELLED");
        booking.setReason(finalReason);
        bookingRepository.save(booking);

        try {
            String timeStr = booking.getBookingTime().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
            String sName = booking.getServiceType() != null ? booking.getServiceType().getName() : "Dịch vụ";
            String bName = booking.getBranch() != null ? booking.getBranch().getName() : "Chi nhánh";

            // Gửi thông báo đến chuông cho khách hàng
            sendCustomerNotification(booking, "Lịch hẹn dịch vụ #" + booking.getId() + " đã hủy thành công",
                    "Bạn đã hủy lịch hẹn " + sName + " tại " + bName + " lúc " + timeStr + ". Lý do: " + finalReason,
                    "SERVICE_BOOKING_CANCELLED", "/service");

            // Gửi thông báo đến chuông cho Quản lý chi nhánh
            String customerName = "Khách hàng";
            if (booking.getUserId() != null && userRepository != null) {
                customerName = userRepository.findById(booking.getUserId()).map(User::getFullName).orElse("Khách hàng");
            }
            notifyBranchManagers(booking.getBranch().getId(),
                    "Khách hủy lịch hẹn dịch vụ #" + booking.getId(),
                    customerName + " đã hủy lịch hẹn " + sName + " lúc " + timeStr + " tại chi nhánh. Lý do: " + finalReason,
                    "SERVICE_BOOKING_CANCELLED", "/admin/services", booking.getId());
        } catch (Exception ignored) {
        }

        return mapToResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getBusySlots(Long branchId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);
        return bookingRepository.findByBranchIdAndBookingTimeBetweenOrderByBookingTimeAsc(branchId, start, end).stream()
                .filter(b -> !"CANCELLED".equals(b.getStatus()) && !"NO_SHOW".equals(b.getStatus()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public int autoUpdateNoShowBookings() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(2);
        List<ServiceBooking> overdueBookings = bookingRepository.findByStatusInAndBookingTimeBefore(
                List.of("PENDING", "CONFIRMED"), threshold);

        for (ServiceBooking booking : overdueBookings) {
            booking.setStatus("NO_SHOW");
            booking.setReason("Hệ thống tự động: Khách không đến quá 2 tiếng so với giờ hẹn");
            bookingRepository.save(booking);

            // Gửi thông báo đến chuông của khách hàng
            try {
                if (booking.getUserId() != null && userRepository != null && notificationService != null) {
                    userRepository.findById(booking.getUserId()).ifPresent(user -> {
                        String timeStr = booking.getBookingTime().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
                        String branchName = booking.getBranch() != null ? booking.getBranch().getName() : "chi nhánh";
                        notificationService.createNotification(
                                user,
                                "Lịch hẹn dịch vụ đã chuyển sang Khách không đến (NO_SHOW)",
                                "Lịch hẹn dịch vụ lúc " + timeStr + " tại " + branchName
                                        + " đã quá 2 tiếng so với giờ hẹn mà chi nhánh chưa ghi nhận bạn đến. Hệ thống đã tự động chuyển trạng thái sang Khách không đến.",
                                "SERVICE_BOOKING",
                                "/service#tab-history",
                                booking.getId()
                        );
                    });
                }
            } catch (Exception ignored) {
            }
        }
        return overdueBookings.size();
    }

    private BookingResponse mapToResponse(ServiceBooking booking) {
        BookingResponse res = new BookingResponse();
        res.setId(booking.getId());
        res.setUserId(booking.getUserId());
        res.setBranchId(booking.getBranch() != null ? booking.getBranch().getId() : null);
        res.setBranchName(booking.getBranch() != null ? booking.getBranch().getName() : null);
        res.setServiceTypeId(booking.getServiceType() != null ? booking.getServiceType().getId() : null);
        res.setServiceTypeName(booking.getServiceType() != null ? booking.getServiceType().getName() : null);
        res.setServicePrice(booking.getServiceType() != null ? booking.getServiceType().getPrice() : null);
        res.setDurationMinutes(booking.getServiceType() != null ? booking.getServiceType().getDuration() : null);
        res.setBookingTime(booking.getBookingTime());
        res.setStatus(booking.getStatus());
        res.setReason(booking.getReason());
        res.setCreatedAt(booking.getCreatedAt());

        if (booking.getUserId() != null && userRepository != null) {
            userRepository.findById(booking.getUserId()).ifPresent(user -> {
                res.setCustomerName(user.getFullName());
                res.setCustomerEmail(user.getEmail());
                res.setCustomerPhone(user.getPhone());
            });
        }

        return res;
    }

    private void sendCustomerNotification(ServiceBooking booking, String title, String content, String type, String link) {
        try {
            if (booking.getUserId() != null && userRepository != null && notificationService != null) {
                userRepository.findById(booking.getUserId()).ifPresent(user -> {
                    notificationService.createNotification(user, title, content, type, link, booking.getId());
                });
            }
        } catch (Exception ignored) {
        }
    }

    private void notifyBranchManagers(Long branchId, String title, String content, String type, String link, Long referenceId) {
        try {
            if (notificationService != null && branchId != null) {
                notificationService.notifyBranchManagers(branchId, title, content, type, link, referenceId);
            }
        } catch (Exception ignored) {
        }
    }
}