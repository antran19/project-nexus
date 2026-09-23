package com.nexus.common.web;

import com.nexus.common.core.ApiResponse;
import com.nexus.common.core.exception.ConflictException;
import com.nexus.common.core.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundException_mapsTo404() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleNotFound(new NotFoundException("USER_NOT_FOUND", "User not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getError().getCode()).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void conflictException_mapsTo409() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleConflict(new ConflictException("EMAIL_TAKEN", "Email already registered"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getError().getCode()).isEqualTo("EMAIL_TAKEN");
    }
}
