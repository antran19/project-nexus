package com.nexus.user.application.usecase;

import com.nexus.user.application.exception.DuplicateEmailException;
import com.nexus.user.application.port.out.PasswordHasherPort;
import com.nexus.user.application.port.out.RoleRepositoryPort;
import com.nexus.user.application.port.out.UserRepositoryPort;
import com.nexus.user.domain.model.Role;
import com.nexus.user.domain.model.RoleId;
import com.nexus.user.domain.model.User;
import com.nexus.user.domain.service.PasswordPolicy;
import org.springframework.transaction.annotation.Transactional;

public class RegisterUserUseCase {

    private static final String DEFAULT_ROLE_CODE = "BUYER";

    private final UserRepositoryPort userRepositoryPort;
    private final RoleRepositoryPort roleRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;

    public RegisterUserUseCase(UserRepositoryPort userRepositoryPort,
                                RoleRepositoryPort roleRepositoryPort,
                                PasswordHasherPort passwordHasherPort) {
        this.userRepositoryPort = userRepositoryPort;
        this.roleRepositoryPort = roleRepositoryPort;
        this.passwordHasherPort = passwordHasherPort;
    }

    @Transactional
    public UserRegistrationResult register(RegisterUserCommand command) {
        PasswordPolicy.validate(command.rawPassword());

        if (userRepositoryPort.findByEmail(command.email()).isPresent()) {
            throw new DuplicateEmailException(command.email());
        }

        Role defaultRole = roleRepositoryPort.findByCode(DEFAULT_ROLE_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "Default role '" + DEFAULT_ROLE_CODE + "' is not seeded — check V2 migration"));

        String hashedPassword = passwordHasherPort.hash(command.rawPassword());
        User user = User.register(command.email(), hashedPassword, command.fullName(), new RoleId(defaultRole.id()));
        User saved = userRepositoryPort.save(user);

        return new UserRegistrationResult(saved.getId(), saved.getEmail(), saved.getFullName());
    }
}
