package com.pawconnect.service.shop;

import com.pawconnect.dto.booking.BookingRequest;
import com.pawconnect.dto.booking.BookingResponse;
import com.pawconnect.entity.Branch;
import com.pawconnect.entity.ServiceBooking;
import com.pawconnect.entity.ServiceType;
import com.pawconnect.exception.BusinessException;
import com.pawconnect.exception.ResourceNotFoundException;
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

        // Use pessimistic write lock on Branch to prevent race condition across multiple bookings
        Branch branch = branchRepository.findByIdWithPessimisticLock(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        ServiceType serviceType = serviceTypeRepository.findById(request.getServiceTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Service type not found"));

        LocalDateTime requestedTime = request.getBookingTime();
        LocalDateTime endTime = requestedTime.plusMinutes(serviceType.getDuration());

        // Now safe from race conditions for this branch because of pessimistic lock
        List<ServiceBooking> existingBookings = bookingRepository.findByBranchIdAndBookingTimeBetween(
                branch.getId(), requestedTime.minusHours(2), requestedTime.plusHours(2));

        for (ServiceBooking existing : existingBookings) {
            if (existing.getStatus().equals("CANCELLED")) continue;
            LocalDateTime exStart = existing.getBookingTime();
            LocalDateTime exEnd = exStart.plusMinutes(existing.getServiceType().getDuration());

            if (requestedTime.isBefore(exEnd) && endTime.isAfter(exStart)) {
                throw new BusinessException("Time slot is already booked");
            }
        }

        ServiceBooking booking = ServiceBooking.builder()
                .userId(userId)
                .branch(branch)
                .serviceType(serviceType)
                .bookingTime(requestedTime)
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
    @Transactional
    public BookingResponse updateBookingStatus(Long bookingId, String status) {
        ServiceBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        
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
