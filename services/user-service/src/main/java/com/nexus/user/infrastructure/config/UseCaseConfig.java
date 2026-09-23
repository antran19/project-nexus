package com.nexus.user.infrastructure.config;

import com.nexus.user.application.port.out.EventPublisherPort;
import com.nexus.user.application.port.out.PasswordHasherPort;
import com.nexus.user.application.port.out.RoleRepositoryPort;
import com.nexus.user.application.port.out.UserRepositoryPort;
import com.nexus.user.application.usecase.RegisterUserUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public RegisterUserUseCase registerUserUseCase(UserRepositoryPort userRepositoryPort,
                                                     RoleRepositoryPort roleRepositoryPort,
                                                     PasswordHasherPort passwordHasherPort,
                                                     EventPublisherPort eventPublisherPort) {
        return new RegisterUserUseCase(userRepositoryPort, roleRepositoryPort, passwordHasherPort, eventPublisherPort);
    }
}
