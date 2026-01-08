package com.bss.infrastructure.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = OpenApiConfig.class)
@ActiveProfiles("test")
class OpenApiConfigTest {

    @Autowired
    private OpenAPI openAPI;

    @Test
    @DisplayName("Should create OpenAPI bean with correct information")
    void customOpenAPI_shouldHaveCorrectInfo() {
        // Assert
        assertThat(openAPI).isNotNull();
        
        Info info = openAPI.getInfo();
        assertThat(info).isNotNull();
        assertThat(info.getTitle()).isEqualTo("Ledger Service API");
        assertThat(info.getVersion()).isEqualTo("v1");
        assertThat(info.getDescription()).contains("API for managing financial ledger operations");
        assertThat(info.getTermsOfService()).isEqualTo("http://swagger.io/terms/");
        
        assertThat(info.getLicense()).isNotNull();
        assertThat(info.getLicense().getName()).isEqualTo("Apache 2.0");
        assertThat(info.getLicense().getUrl()).isEqualTo("http://springdoc.org");
    }
}
