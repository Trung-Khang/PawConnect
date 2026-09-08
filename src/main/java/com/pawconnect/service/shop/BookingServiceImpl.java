package com.pawconnect.service.shop;

import com.pawconnect.dto.booking.BookingRequest;
import com.pawconnect.dto.booking.BookingResponse;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.ServiceBooking;
import com.pawconnect.entity.ServiceType;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.ServiceBookingRepository;
import com.pawconnect.repository.ServiceTypeRepository;
import com.pawconnect.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final ServiceBookingRepository bookingRepository;
    private final BranchRepository branchRepository;
    private final ServiceTypeRepository serviceTypeRepository;

    @Override
    @Transactional
    public BookingResponse createBooking(BookingRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found"));
        ServiceType serviceType = serviceTypeRepository.findById(request.getServiceTypeId())
                .orElseThrow(() -> new RuntimeException("Service Type not found"));

        // Check for conflicts
        LocalDateTime start = request.getBookingTime();
        LocalDateTime end = start.plusMinutes(serviceType.getDuration());

        // Fetch bookings for the same day to filter in memory
        LocalDateTime startOfDay = start.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<ServiceBooking> bookings = bookingRepository.findByBranchIdAndBookingTimeBetween(
                branch.getId(), startOfDay, endOfDay);
        
        // Filter out canceled or rejected bookings, and check overlap
        // overlap condition: (StartA < EndB) and (EndA > StartB)
        boolean hasConflict = bookings.stream()
                .filter(b -> !b.getStatus().equals("CANCELED") && !b.getStatus().equals("REJECTED"))
                .anyMatch(b -> {
                    LocalDateTime oldStart = b.getBookingTime();
                    LocalDateTime oldEnd = oldStart.plusMinutes(b.getServiceType().getDuration());
                    return start.isBefore(oldEnd) && end.isAfter(oldStart);
                });

        if (hasConflict) {
            throw new RuntimeException("Booking time conflict at this branch.");
        }

        ServiceBooking booking = ServiceBooking.builder()
                .userId(userId)
                .branch(branch)
                .serviceType(serviceType)
                .bookingTime(request.getBookingTime())
                .status("PENDING")
                .build();

        bookingRepository.save(booking);
        return mapToResponse(booking);
    }

    @Override
    public List<BookingResponse> getMyBookings() {
        Long userId = SecurityUtils.getCurrentUserId();
        return bookingRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public BookingResponse updateBookingStatus(Long bookingId, String status) {
        ServiceBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        
        // Optmistic locking will trigger here if concurrently updated
        booking.setStatus(status);
        bookingRepository.save(booking);
        return mapToResponse(booking);
    }

    private BookingResponse mapToResponse(ServiceBooking booking) {
        BookingResponse res = new BookingResponse();
        res.setId(booking.getId());
        res.setUserId(booking.getUserId());
        res.setBranchId(booking.getBranch().getId());
        res.setServiceTypeId(booking.getServiceType().getId());
        res.setServiceTypeName(booking.getServiceType().getName());
        res.setBookingTime(booking.getBookingTime());
        res.setStatus(booking.getStatus());
        return res;
    }
}
