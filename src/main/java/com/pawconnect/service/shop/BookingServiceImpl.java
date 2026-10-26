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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

        booking.setStatus("CANCELLED");
        booking.setReason(reason != null && !reason.isBlank() ? reason.trim() : "Khách hàng tự hủy hẹn");
        bookingRepository.save(booking);
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
}