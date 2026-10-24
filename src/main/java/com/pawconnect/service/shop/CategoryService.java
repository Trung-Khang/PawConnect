package com.pawconnect.service.shop;

import com.pawconnect.dto.category.CategoryResponse;

import java.util.List;

public interface CategoryService {
    List<CategoryResponse> getAllCategories();
}
