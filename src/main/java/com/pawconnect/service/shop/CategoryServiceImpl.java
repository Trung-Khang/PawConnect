package com.pawconnect.service.shop;

import com.pawconnect.dto.category.CategoryResponse;
import com.pawconnect.entity.Category;
import com.pawconnect.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream().map(category -> {
            CategoryResponse response = new CategoryResponse();
            response.setId(category.getId());
            response.setName(category.getName());
            response.setDescription(category.getDescription());
            return response;
        }).collect(Collectors.toList());
    }
}
