package com.pawconnect.service.shop;

import com.pawconnect.entity.Branch;
import com.pawconnect.entity.Category;
import com.pawconnect.entity.ListingStatus;
import com.pawconnect.entity.PuppyListing;
import com.pawconnect.entity.BreedType;
import com.pawconnect.entity.LifeStage;
import com.pawconnect.repository.BranchRepository;
import com.pawconnect.repository.CategoryRepository;
import com.pawconnect.repository.ProductRepository;
import com.pawconnect.repository.PuppyListingRepository;
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
public class PuppyListingServiceConcurrencyTest {

    @Autowired
    private PuppyListingRepository puppyListingRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    private PuppyListing testPuppy;

    @BeforeEach
    void setUp() {
        Branch branch = Branch.builder()
                .name("Test Branch " + System.currentTimeMillis())
                .code("TB_" + System.currentTimeMillis())
                .address("123 Test")
                .phone("123456789")
                .build();
        branch = branchRepository.save(branch);

        Category category = Category.builder()
                .name("Breeding Dogs")
                .description("Purebred dogs")
                .build();
        category = categoryRepository.save(category);

        PuppyListing puppy = new PuppyListing();
        puppy.setListingTitle("Exclusive Golden Retriever Puppy");
        puppy.setBranch(branch);
        puppy.setCategory(category);
        puppy.setBreedCode("BREED_GOLDEN");
        puppy.setBreedType(BreedType.PUREBRED);
        puppy.setLifeStage(LifeStage.PUPPY);
        puppy.setAgeMonths(2);
        puppy.setPricePerPuppyVnd(new BigDecimal("15000000"));
        puppy.setStock(1); // Only 1 exclusive puppy
        puppy.setStatus(ListingStatus.AVAILABLE);

        testPuppy = puppyListingRepository.save(puppy);
    }

    @AfterEach
    void tearDown() {
        if (testPuppy != null) {
            puppyListingRepository.delete(testPuppy);
        }
    }

    @Test
    void testDecrementStockConcurrency() throws InterruptedException {
        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successfulDecrements = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.execute(() -> {
                try {
                    int updated = puppyListingRepository.decrementStock(testPuppy.getId(), 1);
                    if (updated > 0) {
                        successfulDecrements.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        PuppyListing updatedPuppy = puppyListingRepository.findById(testPuppy.getId()).orElseThrow();
        
        // Stock was 1, so only 1 thread should succeed in decrementing it
        assertEquals(0, updatedPuppy.getStock());
        assertEquals(1, successfulDecrements.get(), "Only one thread should have successfully decremented the stock");
    }
}
