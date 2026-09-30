package com.pawconnect.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawconnect.service.seed.AdoptionSeedImportService;
import com.pawconnect.service.seed.AdoptionSeedImportSummary;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdoptionSeedInitializerTest {

    private final AdoptionSeedInitializer initializer = new AdoptionSeedInitializer();

    @Test
    void importsOnlyTheConfiguredExistingSeedDirectory() throws Exception {
        AdoptionSeedImportService service = mock(AdoptionSeedImportService.class);
        Path directory = Files.createTempDirectory("pawconnect-seed-v3-");
        when(service.importSeed(directory.toAbsolutePath().normalize()))
                .thenReturn(new AdoptionSeedImportSummary(30, 0, 30, 0, 36, 0));

        initializer.initializeAdoptionSeed(service, directory.toString()).run();

        verify(service).importSeed(directory.toAbsolutePath().normalize());
    }

    @Test
    void failsClearlyWhenTheConfiguredSeedDirectoryIsMissing() {
        AdoptionSeedImportService service = mock(AdoptionSeedImportService.class);

        assertThatThrownBy(() -> initializer.initializeAdoptionSeed(service, "missing-seed-v3-directory").run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Seed V3 directory does not exist");
    }
}
