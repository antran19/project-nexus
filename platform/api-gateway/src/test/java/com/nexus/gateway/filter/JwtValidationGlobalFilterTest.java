package com.nexus.gateway.filter;

import com.nexus.common.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtValidationGlobalFilterTest {

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final JwtValidationGlobalFilter filter = new JwtValidationGlobalFilter(jwtTokenProvider);
    private final GatewayFilterChain chain = mock(GatewayFilterChain.class);

    @Test
    void allowsPublicPathWithoutToken() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void rejectsProtectedPathWithoutToken() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me/password").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void rejectsProtectedPathWithInvalidToken() {
        when(jwtTokenProvider.isValid("bad-token")).thenReturn(false);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me/password")
                        .header("Authorization", "Bearer bad-token").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void allowsProtectedPathWithValidToken() {
        when(jwtTokenProvider.isValid("good-token")).thenReturn(true);
        when(chain.filter(any())).thenReturn(Mono.empty());
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me/password")
                        .header("Authorization", "Bearer good-token").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }
}
