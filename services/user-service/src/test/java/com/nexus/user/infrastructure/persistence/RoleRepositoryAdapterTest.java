package com.nexus.user.infrastructure.persistence;

import com.nexus.user.domain.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RoleRepositoryAdapter.class)
class RoleRepositoryAdapterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("user_db").withUsername("nexus").withPassword("nexus");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private RoleRepositoryAdapter roleRepositoryAdapter;

    @Test
    void findByCode_returnsSeedRoleWithItsPrivileges() {
        Optional<Role> role = roleRepositoryAdapter.findByCode("BUYER");

        assertThat(role).isPresent();
        assertThat(role.get().privilegeCodes()).contains("PROFILE.CHANGE_PASSWORD", "AUTH.LOGIN");
    }

    @Test
    void findByCode_returnsEmptyForUnknownCode() {
        assertThat(roleRepositoryAdapter.findByCode("NOT_A_ROLE")).isEmpty();
    }
}
