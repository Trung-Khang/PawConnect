package com.pawconnect.service.shop;

import com.pawconnect.entity.Branch;
import com.pawconnect.entity.Category;
import com.pawconnect.entity.Product;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.CategoryRepository;
import com.pawconnect.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class ProductServiceConcurrencyTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Long productId;

    @BeforeEach
    public void setup() {
        Branch branch = Branch.builder().name("Test Branch").address("Address").build();
        branch = branchRepository.save(branch);

        Category cat = Category.builder().name("Dog").build();
        cat = categoryRepository.save(cat);

        Product p = Product.builder()
                .name("Golden Retriever")
                .price(new BigDecimal("5000000"))
                .stock(1) // CHÓ GIỐNG ĐỘC BẢN CÓ STOCK = 1
                .branch(branch)
                .category(cat)
                .isBreedingDog(true)
                .build();
        p = productRepository.save(p);
        productId = p.getId();
    }

    @AfterEach
    public void cleanup() {
        if (productId != null) {
            productRepository.deleteById(productId);
        }
        // Cannot easily delete category and branch because they might be referenced by other tests if they share DB state. 
        // But since we delete the specific product, it should avoid the FK constraint issue.
    }

    @Test
    public void testConcurrentDecreaseStock_RaceConditionPrevention() throws InterruptedException {
        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.execute(() -> {
                try {
                    // Try to buy the same dog concurrently
                    int updatedRows = productRepository.decreaseStock(productId);
                    if (updatedRows > 0) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // CHỈ ĐƯỢC 1 LUỒNG MUA THÀNH CÔNG, 9 LUỒNG THẤT BẠI
        assertEquals(1, successCount.get(), "Chỉ 1 người được mua chó giống thành công");
        assertEquals(9, failureCount.get(), "9 người khác phải bị từ chối do hết stock");

        Product productInDb = productRepository.findById(productId).orElseThrow();
        assertEquals(0, productInDb.getStock(), "Stock cuối cùng phải là 0");
    }
}
