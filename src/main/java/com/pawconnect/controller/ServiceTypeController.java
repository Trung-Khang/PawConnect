package com.pawconnect.controller;

import com.pawconnect.entity.ServiceType;
import com.pawconnect.service.shop.ServiceTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/service-types")
@RequiredArgsConstructor
public class ServiceTypeController {

    private final ServiceTypeService serviceTypeService;

    @GetMapping
    public List<ServiceType> getAllServiceTypes() {
        return serviceTypeService.getAllServiceTypes();
    }
}
