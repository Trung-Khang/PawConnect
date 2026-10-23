package com.pawconnect.service.shop;

import com.pawconnect.entity.ServiceType;
import com.pawconnect.repository.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceTypeServiceImpl implements ServiceTypeService {

    private final ServiceTypeRepository serviceTypeRepository;

    @Override
    public List<ServiceType> getAllServiceTypes() {
        return serviceTypeRepository.findAll();
    }
}
