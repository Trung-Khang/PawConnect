package com.pawconnect.config;

import com.pawconnect.entity.Branch;
import com.pawconnect.entity.Category;
import com.pawconnect.entity.Product;
import com.pawconnect.entity.ServiceType;
import com.pawconnect.entity.PuppyListing;
import com.pawconnect.entity.ListingStatus;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.CategoryRepository;
import com.pawconnect.repository.ProductRepository;
import com.pawconnect.repository.PuppyListingRepository;
import com.pawconnect.repository.ServiceTypeRepository;
import com.pawconnect.entity.BreedType;
import com.pawconnect.entity.LifeStage;
import com.pawconnect.entity.DogSize;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.CommandLineRunner;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;

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
            ProductRepository productRepository,
            PuppyListingRepository puppyListingRepository) {
        return arguments -> {
            try {
                String basePath = System.getProperty("user.dir") + "/data-pipeline/data/seed/v3/";

                // 1. Load Branches
                Map<String, Branch> branchMap = new HashMap<>();
                File branchFile = new File(basePath + "reference/branches.csv");
                if (branchFile.exists()) {
                    List<String> lines = Files.readAllLines(branchFile.toPath());
                    for (int i = 1; i < lines.size(); i++) {
                        String[] parts = parseCsvLine(lines.get(i));
                        if (parts.length >= 7) {
                            String code = parts[1];
                            Branch branch = branchRepository.findByCode(code).orElse(new Branch());
                            branch.setName(parts[2]);
                            branch.setCode(code);
                            branch.setAddress(parts[3]);
                            branch.setPhone(parts[4]);
                            branch.setLatitude(Double.parseDouble(parts[5]));
                            branch.setLongitude(Double.parseDouble(parts[6]));
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
                        String[] parts = parseCsvLine(lines.get(i));
                        if (parts.length >= 4) {
                            String code = parts[1];
                            Category category = categoryRepository.findByName(parts[2]).orElse(new Category());
                            category.setName(parts[2]);
                            category.setDescription(parts[3]);
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
                        String[] parts = parseCsvLine(lines.get(i));
                        if (parts.length >= 5) {
                            // Skipping idempotent service type update because we didn't add findByName, and it doesn't have a code.
                            // Assuming serviceType is not strictly TV1's main domain for now, or we can just skip it if it exists.
                            if (serviceTypeRepository.findAll().stream().noneMatch(s -> s.getName().equals(parts[2]))) {
                                ServiceType serviceType = ServiceType.builder()
                                        .name(parts[2])
                                        .duration(Integer.parseInt(parts[3]))
                                        .price(new BigDecimal(parts[4]))
                                        .build();
                                serviceTypeRepository.save(serviceType);
                            }
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
                        String[] parts = parseCsvLine(line);
                        if (parts.length >= 20) {
                            String name = parts[2];
                            String description = parts[3];
                            BigDecimal price = new BigDecimal(parts[4]);
                            Integer stock = Integer.parseInt(parts[5]);
                            String imageUrl = parts[6];
                            String suitableSize = parts[7];
                            String categoryCode = parts[9];
                            String branchCode = parts[10];

                            Product product = productRepository.findByName(name).orElse(new Product());
                            product.setName(name);
                            product.setDescription(description);
                            product.setPrice(price);
                            product.setStock(stock);
                            product.setImageUrl(imageUrl);
                            product.setSuitableSize(suitableSize);
                            product.setCategory(categoryMap.get(categoryCode));
                            product.setBranch(branchMap.get(branchCode));
                            productRepository.save(product);
                        }
                    }
                }

                // 5. Load Puppy Listings
                File puppyFile = new File(basePath + "commerce/puppy_listings.csv");
                if (puppyFile.exists()) {
                    List<String> lines = Files.readAllLines(puppyFile.toPath());
                    for (int i = 1; i < lines.size(); i++) {
                        String line = lines.get(i);
                        if (line.trim().isEmpty()) continue;
                        String[] parts = parseCsvLine(line);
                        if (parts.length >= 18) {
                            String seedKey = parts[0];
                            String listingTitle = parts[1];
                            String description = parts[2];
                            String branchCode = parts[3];
                            String categoryCode = parts[4];
                            String breedCode = parts[5];
                            BreedType breedType = BreedType.valueOf(parts[6]);
                            LifeStage lifeStage = LifeStage.valueOf(parts[7]);
                            Integer ageMonths = 0;
                            if(!parts[8].isEmpty()) {
                                try { ageMonths = Integer.parseInt(parts[8]); } catch (Exception e) {}
                            }
                            BigDecimal currentWeightKg = null;
                            if(!parts[9].isEmpty()) {
                                try { currentWeightKg = new BigDecimal(parts[9]); } catch (Exception e) {}
                            }
                            DogSize currentSize = null;
                            if(!parts[10].isEmpty()) currentSize = DogSize.valueOf(parts[10]);
                            DogSize expectedAdultSize = null;
                            if(!parts[11].isEmpty()) expectedAdultSize = DogSize.valueOf(parts[11]);
                            
                            BigDecimal pricePerPuppyVnd = new BigDecimal(parts[12]);
                            Integer stock = Integer.parseInt(parts[13]);
                            ListingStatus status = ListingStatus.valueOf(parts[14]);
                            String imageUrl = parts[15];
                            String healthStatus = parts[16];
                            String careInstructions = parts[17];
                            
                            PuppyListing puppy = puppyListingRepository.findBySeedKey(seedKey).orElse(new PuppyListing());
                            puppy.setSeedKey(seedKey);
                            puppy.setListingTitle(listingTitle);
                            puppy.setDescription(description);
                            puppy.setBranch(branchMap.get(branchCode));
                            puppy.setCategory(categoryMap.get(categoryCode));
                            puppy.setBreedCode(breedCode);
                            puppy.setBreedType(breedType);
                            puppy.setLifeStage(lifeStage);
                            puppy.setAgeMonths(ageMonths);
                            puppy.setCurrentWeightKg(currentWeightKg);
                            puppy.setCurrentSize(currentSize);
                            puppy.setExpectedAdultSize(expectedAdultSize);
                            puppy.setPricePerPuppyVnd(pricePerPuppyVnd);
                            puppy.setStock(stock);
                            puppy.setStatus(status);
                            if (imageUrl != null && !imageUrl.isEmpty()) {
                                puppy.setImageUrl(imageUrl);
                            }
                            puppy.setHealthStatus(healthStatus);
                            puppy.setCareInstructions(careInstructions);
                            
                            puppyListingRepository.save(puppy);
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

    private String[] parseCsvLine(String line) {
        String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
        for (int j = 0; j < parts.length; j++) {
            parts[j] = parts[j].replace("\"", "").trim();
        }
        return parts;
    }
}
