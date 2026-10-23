package com.pawconnect.service.shop;

import com.pawconnect.dto.category.CategoryResponse;
import com.pawconnect.entity.Category;

import java.util.List;

public interface CategoryService {
    List<CategoryResponse> getAllCategories();
}
