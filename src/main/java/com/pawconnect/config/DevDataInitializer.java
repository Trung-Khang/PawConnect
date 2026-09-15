package com.pawconnect.config;

import com.pawconnect.entity.Branch;
import com.pawconnect.entity.Category;
import com.pawconnect.entity.Product;
import com.pawconnect.entity.ServiceType;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.CategoryRepository;
import com.pawconnect.repository.ProductRepository;
import com.pawconnect.repository.ServiceTypeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class DevDataInitializer {

    @Bean
    CommandLineRunner initializeTV1Data(
            BranchRepository branchRepository,
            CategoryRepository categoryRepository,
            ServiceTypeRepository serviceTypeRepository,
            ProductRepository productRepository) {
        return arguments -> {
            // Only run if empty
            if (branchRepository.count() > 0) {
                return;
            }

            try {
                String basePath = System.getProperty("user.dir") + "/data-pipeline/data/seed/v3/";

                // 1. Load Branches
                Map<String, Branch> branchMap = new HashMap<>();
                File branchFile = new File(basePath + "reference/branches.csv");
                if (branchFile.exists()) {
                    List<String> lines = Files.readAllLines(branchFile.toPath());
                    for (int i = 1; i < lines.size(); i++) {
                        String[] parts = lines.get(i).split(",");
                        if (parts.length >= 7) {
                            String code = parts[1];
                            Branch branch = Branch.builder()
                                    .name(parts[2])
                                    .address(parts[3])
                                    .phone(parts[4])
                                    .latitude(Double.parseDouble(parts[5]))
                                    .longitude(Double.parseDouble(parts[6]))
                                    .build();
                            branchRepository.save(branch);
                            branchMap.put(code, branch);
                        }
                    }
                }

                // 2. Load Categories
                Map<String, Category> categoryMap = new HashMap<>();
                File categoryFile = new File(basePath + "reference/categories.csv");
                if (categoryFile.exists()) {
                    List<String> lines = Files.readAllLines(categoryFile.toPath());
                    for (int i = 1; i < lines.size(); i++) {
                        String[] parts = lines.get(i).split(",");
                        if (parts.length >= 4) {
                            String code = parts[1];
                            Category category = Category.builder()
                                    .name(parts[2])
                                    .description(parts[3])
                                    .build();
                            categoryRepository.save(category);
                            categoryMap.put(code, category);
                        }
                    }
                }

                // 3. Load Service Types
                File serviceFile = new File(basePath + "reference/service_types.csv");
                if (serviceFile.exists()) {
                    List<String> lines = Files.readAllLines(serviceFile.toPath());
                    for (int i = 1; i < lines.size(); i++) {
                        String[] parts = lines.get(i).split(",");
                        if (parts.length >= 5) {
                            ServiceType serviceType = ServiceType.builder()
                                    .name(parts[2])
                                    .duration(Integer.parseInt(parts[3]))
                                    .price(new BigDecimal(parts[4]))
                                    .build();
                            serviceTypeRepository.save(serviceType);
                        }
                    }
                }

                // 4. Load Products
                File productFile = new File(basePath + "commerce/products.csv");
                if (productFile.exists()) {
                    List<String> lines = Files.readAllLines(productFile.toPath());
                    for (int i = 1; i < lines.size(); i++) {
                        String line = lines.get(i);
                        if (line.trim().isEmpty()) continue;
                        String[] parts = line.split(",", -1);
                        if (parts.length >= 20) {
                            String name = parts[2];
                            String description = parts[3];
                            BigDecimal price = new BigDecimal(parts[4]);
                            Integer stock = Integer.parseInt(parts[5]);
                            String imageUrl = parts[6];
                            String suitableSize = parts[7];
                            Boolean isBreedingDog = Boolean.parseBoolean(parts[8]);
                            String categoryCode = parts[9];
                            String branchCode = parts[10];
                            String age = parts[14];
                            String breed = parts[12];
                            String healthStatus = parts[18];
                            String careInstructions = parts[19];

                            Product product = Product.builder()
                                    .name(name)
                                    .description(description)
                                    .price(price)
                                    .stock(stock)
                                    .imageUrl(imageUrl)
                                    .suitableSize(suitableSize)
                                    .isBreedingDog(isBreedingDog)
                                    .category(categoryMap.get(categoryCode))
                                    .branch(branchMap.get(branchCode))
                                    .age(age)
                                    .breed(breed)
                                    .healthStatus(healthStatus)
                                    .careInstructions(careInstructions)
                                    .build();
                            productRepository.save(product);
                        }
                    }
                }

                System.out.println("DEV DATA INITIALIZED SUCCESSFULLY!");
            } catch (Exception e) {
                System.err.println("FAILED TO INITIALIZE DEV DATA: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }
}
