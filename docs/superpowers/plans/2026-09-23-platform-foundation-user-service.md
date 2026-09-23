# Platform Foundation + User Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the shared microservices platform (service discovery, API gateway) and a fully working User Service (register, login, change password, JWT-based authn/authz, reliable event publishing via the Outbox pattern), runnable end-to-end with `docker-compose up`.

**Architecture:** Maven multi-module monorepo. Each runtime component (`discovery-server`, `api-gateway`, `user-service`) is an independently buildable/deployable Spring Boot application. `user-service` follows hexagonal architecture (api / application / domain / infrastructure). Cross-cutting code lives in `libs/` (`common-core`, `common-web`, `common-events`, `common-security`) and is depended on by the runtime modules, never the other way around.

**Tech Stack:** Java 21, Spring Boot 3.3.4, Spring Cloud 2023.0.3 (Eureka, Gateway), Maven, PostgreSQL 16 + Flyway, Apache Kafka 3.7 (KRaft mode), jjwt 0.12.6, Testcontainers 1.20.1, MapStruct 1.6.2, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-22-platform-foundation-user-service-design.md`

## Global Constraints

- Java 21 everywhere; Spring Boot 3.3.4; Maven (not Gradle).
- Monorepo: one Git repo, one parent `pom.xml`, independently buildable modules — see spec "Repository structure".
- Each service owns its own database; no service reads another service's schema directly (SRS §4.1).
- Standardized JSON error response shape defined once in `common-web`, reused by every service (spec "Error handling").
- Access tokens expire after 60 minutes by default (`ACCESS_TOKEN_EXPIRY_MINUTES=60`, SRS §2.6 config table).
- Authorization is enforced per-service against the JWT's `privileges` claim, never at the gateway (spec "Authentication vs. authorization").
- Out of scope for this plan (per spec): the other 5 business services, Kubernetes manifests, centralized observability (OpenTelemetry/Prometheus/Grafana), config server, Kafka schema registry.
- `domain` packages must not import Spring, JPA, or Kafka classes (spec "Internal layering").

---

## Task 1: Root monorepo skeleton

**Files:**
- Create: `pom.xml` (parent)
- Create: `.gitignore`
- Create: `libs/common-core/pom.xml` (placeholder module, no source yet)
- Create: `libs/common-web/pom.xml`
- Create: `libs/common-events/pom.xml`
- Create: `libs/common-security/pom.xml`
- Create: `platform/discovery-server/pom.xml`
- Create: `platform/api-gateway/pom.xml`
- Create: `services/user-service/pom.xml`

**Interfaces:**
- Produces: a `mvn -q validate` that succeeds from the repo root, and a Maven module coordinate (`groupId=com.nexus`, matching `artifactId` per folder name) that every later task's `pom.xml` inherits from.

- [ ] **Step 1: Write the parent POM**

`pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.nexus</groupId>
  <artifactId>project-nexus</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>pom</packaging>

  <properties>
    <java.version>21</java.version>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <spring-boot.version>3.3.4</spring-boot.version>
    <spring-cloud.version>2023.0.3</spring-cloud.version>
    <mapstruct.version>1.6.2</mapstruct.version>
    <jjwt.version>0.12.6</jjwt.version>
    <testcontainers.version>1.20.1</testcontainers.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-dependencies</artifactId>
        <version>${spring-boot.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
      <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-dependencies</artifactId>
        <version>${spring-cloud.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
      <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers-bom</artifactId>
        <version>${testcontainers.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <modules>
    <module>libs/common-core</module>
    <module>libs/common-web</module>
    <module>libs/common-events</module>
    <module>libs/common-security</module>
    <module>platform/discovery-server</module>
    <module>platform/api-gateway</module>
    <module>services/user-service</module>
  </modules>
</project>
```

- [ ] **Step 2: Write each module's placeholder POM**

`libs/common-core/pom.xml` (repeat the same shape for `common-web`, `common-events`, `common-security`, changing only `artifactId`):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.nexus</groupId>
    <artifactId>project-nexus</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>
  <artifactId>common-core</artifactId>
  <packaging>jar</packaging>
</project>
```

`platform/discovery-server/pom.xml` and `platform/api-gateway/pom.xml` and `services/user-service/pom.xml` use `<relativePath>../../pom.xml</relativePath>` and their own `<artifactId>` (`discovery-server`, `api-gateway`, `user-service`); packaging stays `jar` (Spring Boot apps still package as jar, the boot plugin makes them executable — the boot plugin itself is added in later tasks that need it).

- [ ] **Step 3: Write `.gitignore`**

```
target/
*.class
.idea/
*.iml
.env
```

- [ ] **Step 4: Verify the multi-module build resolves**

Run: `mvn -q validate`
Expected: exits 0, no errors (nothing to compile yet, this only checks the POM graph is valid).

- [ ] **Step 5: Commit**

```bash
git add pom.xml .gitignore libs platform services
git commit -m "chore: scaffold monorepo multi-module skeleton"
```

---

## Task 2: common-core — API response envelope and domain exceptions

**Files:**
- Create: `libs/common-core/pom.xml` (finalize, no extra deps needed — pure Java)
- Create: `libs/common-core/src/main/java/com/nexus/common/core/ApiResponse.java`
- Create: `libs/common-core/src/main/java/com/nexus/common/core/ApiError.java`
- Create: `libs/common-core/src/main/java/com/nexus/common/core/FieldError.java`
- Create: `libs/common-core/src/main/java/com/nexus/common/core/exception/DomainException.java`
- Create: `libs/common-core/src/main/java/com/nexus/common/core/exception/NotFoundException.java`
- Create: `libs/common-core/src/main/java/com/nexus/common/core/exception/ConflictException.java`
- Create: `libs/common-core/src/main/java/com/nexus/common/core/exception/ValidationException.java`
- Create: `libs/common-core/src/main/java/com/nexus/common/core/exception/UnauthorizedException.java`
- Create: `libs/common-core/src/main/java/com/nexus/common/core/exception/ForbiddenException.java`
- Test: `libs/common-core/src/test/java/com/nexus/common/core/ApiResponseTest.java`

**Interfaces:**
- Produces: `ApiResponse<T>` with static factories `ApiResponse.ok(T data)` and `ApiResponse.error(ApiError error)`; `ApiError(String code, String message, List<FieldError> fieldErrors)`; `FieldError(String field, String message)`; exception hierarchy rooted at `DomainException(String errorCode, String message)` with subclasses `NotFoundException`, `ConflictException`, `ValidationException(List<FieldError> fieldErrors)`, `UnauthorizedException`, `ForbiddenException` — every later task's exceptions extend one of these.

- [ ] **Step 1: Write the failing test**

`libs/common-core/src/test/java/com/nexus/common/core/ApiResponseTest.java`:
```java
package com.nexus.common.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void ok_wrapsDataAndMarksSuccess() {
        ApiResponse<String> response = ApiResponse.ok("hello");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("hello");
        assertThat(response.getError()).isNull();
    }

    @Test
    void error_carriesErrorAndNoData() {
        ApiError error = new ApiError("USER_NOT_FOUND", "User not found", List.of());
        ApiResponse<Object> response = ApiResponse.error(error);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getError().getCode()).isEqualTo("USER_NOT_FOUND");
    }
}
```

Add to `libs/common-core/pom.xml` (inside a new `<dependencies>` block, before `</project>`):
```xml
  <dependencies>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl libs/common-core test`
Expected: FAIL — compilation error, `ApiResponse`/`ApiError` do not exist yet.

- [ ] **Step 3: Write the implementation**

`libs/common-core/src/main/java/com/nexus/common/core/FieldError.java`:
```java
package com.nexus.common.core;

public record FieldError(String field, String message) {
}
```

`libs/common-core/src/main/java/com/nexus/common/core/ApiError.java`:
```java
package com.nexus.common.core;

import java.util.List;

public class ApiError {
    private final String code;
    private final String message;
    private final List<FieldError> fieldErrors;

    public ApiError(String code, String message, List<FieldError> fieldErrors) {
        this.code = code;
        this.message = message;
        this.fieldErrors = fieldErrors == null ? List.of() : fieldErrors;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public List<FieldError> getFieldErrors() { return fieldErrors; }
}
```

`libs/common-core/src/main/java/com/nexus/common/core/ApiResponse.java`:
```java
package com.nexus.common.core;

public class ApiResponse<T> {
    private final boolean success;
    private final T data;
    private final ApiError error;

    private ApiResponse(boolean success, T data, ApiError error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }

    public boolean isSuccess() { return success; }
    public T getData() { return data; }
    public ApiError getError() { return error; }
}
```

`libs/common-core/src/main/java/com/nexus/common/core/exception/DomainException.java`:
```java
package com.nexus.common.core.exception;

public abstract class DomainException extends RuntimeException {
    private final String errorCode;

    protected DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}
```

`libs/common-core/src/main/java/com/nexus/common/core/exception/NotFoundException.java`:
```java
package com.nexus.common.core.exception;

public class NotFoundException extends DomainException {
    public NotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }
}
```

`libs/common-core/src/main/java/com/nexus/common/core/exception/ConflictException.java`:
```java
package com.nexus.common.core.exception;

public class ConflictException extends DomainException {
    public ConflictException(String errorCode, String message) {
        super(errorCode, message);
    }
}
```

`libs/common-core/src/main/java/com/nexus/common/core/exception/UnauthorizedException.java`:
```java
package com.nexus.common.core.exception;

public class UnauthorizedException extends DomainException {
    public UnauthorizedException(String errorCode, String message) {
        super(errorCode, message);
    }
}
```

`libs/common-core/src/main/java/com/nexus/common/core/exception/ForbiddenException.java`:
```java
package com.nexus.common.core.exception;

public class ForbiddenException extends DomainException {
    public ForbiddenException(String errorCode, String message) {
        super(errorCode, message);
    }
}
```

`libs/common-core/src/main/java/com/nexus/common/core/exception/ValidationException.java`:
```java
package com.nexus.common.core.exception;

import com.nexus.common.core.FieldError;
import java.util.List;

public class ValidationException extends DomainException {
    private final List<FieldError> fieldErrors;

    public ValidationException(List<FieldError> fieldErrors) {
        super("VALIDATION_ERROR", "One or more fields are invalid");
        this.fieldErrors = fieldErrors;
    }

    public List<FieldError> getFieldErrors() { return fieldErrors; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl libs/common-core test`
Expected: PASS, 2 tests green.

- [ ] **Step 5: Commit**

```bash
git add libs/common-core
git commit -m "feat(common-core): add ApiResponse envelope and domain exception hierarchy"
```

---

## Task 3: common-web — global exception handler

**Files:**
- Create: `libs/common-web/pom.xml` (finalize — depends on `common-core` + `spring-boot-starter-web`)
- Create: `libs/common-web/src/main/java/com/nexus/common/web/GlobalExceptionHandler.java`
- Test: `libs/common-web/src/test/java/com/nexus/common/web/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `ApiResponse`, `ApiError`, `FieldError`, and the exception hierarchy from Task 2 (`com.nexus.common.core.*`).
- Produces: a `@RestControllerAdvice` class every service includes on its component-scan path (services scan `com.nexus` as base package, see Task 5) that turns `NotFoundException`→404, `ConflictException`→409, `ValidationException`/`MethodArgumentNotValidException`→400, `UnauthorizedException`→401, `ForbiddenException`→403, anything else→500, always as `ApiResponse.error(...)`.

- [ ] **Step 1: Write the failing test**

`libs/common-web/src/test/java/com/nexus/common/web/GlobalExceptionHandlerTest.java`:
```java
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
```

`libs/common-web/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.nexus</groupId>
    <artifactId>project-nexus</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>
  <artifactId>common-web</artifactId>
  <packaging>jar</packaging>
  <dependencies>
    <dependency>
      <groupId>com.nexus</groupId>
      <artifactId>common-core</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl libs/common-web -am test`
Expected: FAIL — `GlobalExceptionHandler` does not exist.

- [ ] **Step 3: Write the implementation**

`libs/common-web/src/main/java/com/nexus/common/web/GlobalExceptionHandler.java`:
```java
package com.nexus.common.web;

import com.nexus.common.core.ApiError;
import com.nexus.common.core.ApiResponse;
import com.nexus.common.core.FieldError;
import com.nexus.common.core.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError as SpringFieldError;
import org.springframework.web.HttpMediaTypeException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Object>> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnauthorized(UnauthorizedException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Object>> handleForbidden(ForbiddenException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(ValidationException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage(), ex.getFieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "One or more fields are invalid", fieldErrors);
    }

    // org.springframework.web.ErrorResponse is an interface, not a Throwable, so it cannot be used
    // directly as an @ExceptionHandler value (Spring requires Class<? extends Throwable>). Instead we
    // enumerate the concrete built-in Spring MVC exceptions that implement it, which covers every
    // "wrong method / wrong media type / unmapped route / etc." case Spring itself would otherwise
    // resolve to a real 4xx. MethodArgumentNotValidException also implements ErrorResponse but is
    // deliberately left off this list: it keeps its own more specific handler above.
    // NOTE: HttpMessageNotReadableException (malformed JSON body) does NOT implement ErrorResponse in
    // Spring Framework 6.1.x, so it is not covered here and still falls through to handleUnexpected.
    @ExceptionHandler({
            HttpMediaTypeException.class,
            HttpRequestMethodNotSupportedException.class,
            ServletRequestBindingException.class,
            MissingServletRequestPartException.class,
            NoHandlerFoundException.class,
            NoResourceFoundException.class,
            AsyncRequestTimeoutException.class,
            MaxUploadSizeExceededException.class,
            ErrorResponseException.class
    })
    public ResponseEntity<ApiResponse<Object>> handleErrorResponse(org.springframework.web.ErrorResponse ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getBody().getDetail() != null ? ex.getBody().getDetail() : status.getReasonPhrase();
        return build(status, "REQUEST_ERROR", message, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error", List.of());
    }

    private ResponseEntity<ApiResponse<Object>> build(HttpStatus status, String code, String message, List<FieldError> fieldErrors) {
        ApiError error = new ApiError(code, message, fieldErrors);
        return ResponseEntity.status(status).body(ApiResponse.error(error));
    }
}
```

Note: the line `import org.springframework.validation.FieldError as SpringFieldError;` above is Kotlin-style aliasing and **does not compile in Java** — Java has no import-alias syntax. Because `common-core`'s `FieldError` and Spring's `org.springframework.validation.FieldError` share a simple name, resolve the collision by fully qualifying Spring's type inline instead of importing it:

Replace that line (delete it) and change the `handleBeanValidation` body to:
```java
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "One or more fields are invalid", fieldErrors);
    }
```
(`fe` here is already typed as `org.springframework.validation.FieldError` by `getFieldErrors()`'s return type — no import of it is needed at all since it's only used inline as the lambda parameter's inferred type.)

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl libs/common-web -am test`
Expected: PASS, 2 tests green.

- [ ] **Step 5: Commit**

```bash
git add libs/common-web
git commit -m "feat(common-web): add GlobalExceptionHandler mapping domain exceptions to ApiResponse"
```

---

## Task 4: discovery-server (Eureka)

**Files:**
- Create: `platform/discovery-server/pom.xml` (finalize)
- Create: `platform/discovery-server/src/main/java/com/nexus/discovery/DiscoveryServerApplication.java`
- Create: `platform/discovery-server/src/main/resources/application.yml`
- Test: `platform/discovery-server/src/test/java/com/nexus/discovery/DiscoveryServerApplicationTests.java`

**Interfaces:**
- Produces: a running Eureka server on `http://localhost:8761`, exposing `/eureka/apps` and Actuator `/actuator/health`, that later tasks' services register with.

- [ ] **Step 1: Write the failing test**

`platform/discovery-server/src/test/java/com/nexus/discovery/DiscoveryServerApplicationTests.java`:
```java
package com.nexus.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServerApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

`platform/discovery-server/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.nexus</groupId>
    <artifactId>project-nexus</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>
  <artifactId>discovery-server</artifactId>
  <packaging>jar</packaging>
  <dependencies>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl platform/discovery-server -am test`
Expected: FAIL — `DiscoveryServerApplication` does not exist.

- [ ] **Step 3: Write the implementation**

`platform/discovery-server/src/main/java/com/nexus/discovery/DiscoveryServerApplication.java`:
```java
package com.nexus.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
```

`platform/discovery-server/src/main/resources/application.yml`:
```yaml
server:
  port: 8761

spring:
  application:
    name: discovery-server

eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
  server:
    enable-self-preservation: false

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl platform/discovery-server -am test`
Expected: PASS, Spring context loads with Eureka server auto-configured.

- [ ] **Step 5: Manual verification**

Run: `mvn -q -pl platform/discovery-server -am spring-boot:run` (in a separate terminal), then `curl http://localhost:8761/actuator/health`
Expected: `{"status":"UP"}`. Stop the process afterward (Ctrl+C).

- [ ] **Step 6: Commit**

```bash
git add platform/discovery-server
git commit -m "feat(discovery-server): stand up Eureka service registry"
```

---

## Task 5: user-service bootstrap — Eureka client, Actuator, Postgres via Flyway

**Files:**
- Create: `services/user-service/pom.xml` (finalize)
- Create: `services/user-service/src/main/java/com/nexus/user/UserServiceApplication.java`
- Create: `services/user-service/src/main/resources/application.yml`
- Create: `services/user-service/src/main/resources/db/migration/V1__create_users_table.sql`
- Create: `docker-compose.yml` (root — Postgres + discovery-server + user-service only, for now)
- Test: `services/user-service/src/test/java/com/nexus/user/UserServiceApplicationTests.java`

**Interfaces:**
- Consumes: `common-web`'s `GlobalExceptionHandler` (component-scanned via base package `com.nexus`).
- Produces: `user-service` running on port 8081, registered with Eureka as `user-service`, connected to its own `user_db` Postgres database with a `users` table present after Flyway runs on startup.

- [ ] **Step 1: Write the failing test**

`services/user-service/src/test/java/com/nexus/user/UserServiceApplicationTests.java`:
```java
package com.nexus.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserServiceApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("user_db")
            .withUsername("nexus")
            .withPassword("nexus");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Test
    void contextLoads() {
    }
}
```

`services/user-service/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.nexus</groupId>
    <artifactId>project-nexus</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>
  <artifactId>user-service</artifactId>
  <packaging>jar</packaging>
  <dependencies>
    <dependency>
      <groupId>com.nexus</groupId>
      <artifactId>common-core</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>com.nexus</groupId>
      <artifactId>common-web</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-database-postgresql</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>postgresql</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl services/user-service -am test`
Expected: FAIL — `UserServiceApplication` does not exist.

- [ ] **Step 3: Write the implementation**

`services/user-service/src/main/java/com/nexus/user/UserServiceApplication.java`:
```java
package com.nexus.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.nexus")
@EnableDiscoveryClient
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
```

`services/user-service/src/main/resources/application.yml`:
```yaml
server:
  port: 8081

spring:
  application:
    name: user-service
  datasource:
    url: jdbc:postgresql://localhost:5432/user_db
    username: nexus
    password: nexus
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka
  instance:
    prefer-ip-address: true

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

`services/user-service/src/main/resources/db/migration/V1__create_users_table.sql`:
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    hashed_password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

`docker-compose.yml` (root):
```yaml
services:
  postgres-user:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: user_db
      POSTGRES_USER: nexus
      POSTGRES_PASSWORD: nexus
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U nexus -d user_db"]
      interval: 5s
      timeout: 5s
      retries: 10

  discovery-server:
    build: ./platform/discovery-server
    ports:
      - "8761:8761"

  user-service:
    build: ./services/user-service
    depends_on:
      postgres-user:
        condition: service_healthy
      discovery-server:
        condition: service_started
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres-user:5432/user_db
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://discovery-server:8761/eureka
    ports:
      - "8081:8081"
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl services/user-service -am test`
Expected: PASS — Testcontainers starts a real Postgres, Flyway applies `V1__create_users_table.sql`, Spring context loads successfully.

- [ ] **Step 5: Commit**

```bash
git add services/user-service docker-compose.yml
git commit -m "feat(user-service): bootstrap Spring Boot app with Eureka client and Postgres/Flyway"
```

---

## Task 6: Domain layer — User entity and PasswordPolicy

**Files:**
- Create: `services/user-service/src/main/java/com/nexus/user/domain/model/User.java`
- Create: `services/user-service/src/main/java/com/nexus/user/domain/model/RoleId.java`
- Create: `services/user-service/src/main/java/com/nexus/user/domain/service/PasswordPolicy.java`
- Test: `services/user-service/src/test/java/com/nexus/user/domain/service/PasswordPolicyTest.java`
- Test: `services/user-service/src/test/java/com/nexus/user/domain/model/UserTest.java`

**Interfaces:**
- Produces: `RoleId(String value)` (record wrapper), `User.register(String email, String hashedPassword, String fullName, RoleId roleId)` static factory returning a `User` with a generated `id` (`String`, UUID), `getters: getId(), getEmail(), getHashedPassword(), getFullName(), getRoleId(), getCreatedAt()`; `PasswordPolicy.validate(String rawPassword)` throwing `ValidationException` (from `common-core`) when the password is under 8 characters. **No Spring/JPA imports in this package** — plain Java only (spec "Internal layering").

- [ ] **Step 1: Write the failing tests**

`services/user-service/src/test/java/com/nexus/user/domain/service/PasswordPolicyTest.java`:
```java
package com.nexus.user.domain.service;

import com.nexus.common.core.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void validate_acceptsPasswordOfEightOrMoreChars() {
        assertThatCode(() -> PasswordPolicy.validate("longenough")).doesNotThrowAnyException();
    }

    @Test
    void validate_rejectsPasswordUnderEightChars() {
        assertThatThrownBy(() -> PasswordPolicy.validate("short"))
                .isInstanceOf(ValidationException.class);
    }

    private static void assertThatCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        org.assertj.core.api.Assertions.assertThatCode(callable).doesNotThrowAnyException();
    }
}
```

`services/user-service/src/test/java/com/nexus/user/domain/model/UserTest.java`:
```java
package com.nexus.user.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void register_createsUserWithGeneratedIdAndProvidedFields() {
        User user = User.register("alice@example.com", "hashed-value", "Alice Nguyen", new RoleId("role-buyer"));

        assertThat(user.getId()).isNotBlank();
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getHashedPassword()).isEqualTo("hashed-value");
        assertThat(user.getFullName()).isEqualTo("Alice Nguyen");
        assertThat(user.getRoleId()).isEqualTo(new RoleId("role-buyer"));
        assertThat(user.getCreatedAt()).isNotNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl services/user-service -am test`
Expected: FAIL — `User`, `RoleId`, `PasswordPolicy` do not exist.

- [ ] **Step 3: Write the implementation**

`services/user-service/src/main/java/com/nexus/user/domain/model/RoleId.java`:
```java
package com.nexus.user.domain.model;

public record RoleId(String value) {
}
```

`services/user-service/src/main/java/com/nexus/user/domain/model/User.java`:
```java
package com.nexus.user.domain.model;

import java.time.Instant;
import java.util.UUID;

public class User {
    private final String id;
    private final String email;
    private final String hashedPassword;
    private final String fullName;
    private final RoleId roleId;
    private final Instant createdAt;

    private User(String id, String email, String hashedPassword, String fullName, RoleId roleId, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.hashedPassword = hashedPassword;
        this.fullName = fullName;
        this.roleId = roleId;
        this.createdAt = createdAt;
    }

    public static User register(String email, String hashedPassword, String fullName, RoleId roleId) {
        return new User(UUID.randomUUID().toString(), email, hashedPassword, fullName, roleId, Instant.now());
    }

    public static User reconstitute(String id, String email, String hashedPassword, String fullName, RoleId roleId, Instant createdAt) {
        return new User(id, email, hashedPassword, fullName, roleId, createdAt);
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getHashedPassword() { return hashedPassword; }
    public String getFullName() { return fullName; }
    public RoleId getRoleId() { return roleId; }
    public Instant getCreatedAt() { return createdAt; }
}
```

`services/user-service/src/main/java/com/nexus/user/domain/service/PasswordPolicy.java`:
```java
package com.nexus.user.domain.service;

import com.nexus.common.core.FieldError;
import com.nexus.common.core.exception.ValidationException;

import java.util.List;

public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new ValidationException(List.of(
                    new FieldError("password", "Password must be at least " + MIN_LENGTH + " characters")));
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl services/user-service -am test`
Expected: PASS, all tests green.

- [ ] **Step 5: Commit**

```bash
git add services/user-service/src/main/java/com/nexus/user/domain services/user-service/src/test/java/com/nexus/user/domain
git commit -m "feat(user-service): add User domain model and PasswordPolicy"
```

---

## Task 7: Role & Privilege domain, persistence, and seed data

Moved ahead of the register flow because `RegisterUserUseCase` (Task 8) must assign a
default role, and `LoginUseCase` (Task 14) must resolve a role's privileges — both
need this table to exist first.

**Files:**
- Create: `services/user-service/src/main/java/com/nexus/user/domain/model/Role.java`
- Create: `services/user-service/src/main/java/com/nexus/user/application/port/out/RoleRepositoryPort.java`
- Create: `services/user-service/src/main/java/com/nexus/user/infrastructure/persistence/entity/RoleJpaEntity.java`
- Create: `services/user-service/src/main/java/com/nexus/user/infrastructure/persistence/entity/PrivilegeJpaEntity.java`
- Create: `services/user-service/src/main/java/com/nexus/user/infrastructure/persistence/RoleJpaRepository.java`
- Create: `services/user-service/src/main/java/com/nexus/user/infrastructure/persistence/RoleRepositoryAdapter.java`
- Create: `services/user-service/src/main/resources/db/migration/V2__create_roles_and_privileges.sql`
- Test: `services/user-service/src/test/java/com/nexus/user/infrastructure/persistence/RoleRepositoryAdapterTest.java`

**Interfaces:**
- Produces: `Role(String id, String code, String name, Set<String> privilegeCodes)` (plain
  record, `domain.model`); `RoleRepositoryPort` with `Optional<Role> findByCode(String code)`
  and `Optional<Role> findById(String id)` — consumed by `RegisterUserUseCase` (Task 8) and
  `LoginUseCase` (Task 14).

- [ ] **Step 1: Write the failing test**

`services/user-service/src/test/java/com/nexus/user/infrastructure/persistence/RoleRepositoryAdapterTest.java`:
```java
package com.nexus.user.infrastructure.persistence;

import com.nexus.user.domain.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestDatabase;
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl services/user-service -am test`
Expected: FAIL — `Role`, `RoleRepositoryAdapter`, and the V2 migration do not exist.

- [ ] **Step 3: Write the implementation**

`services/user-service/src/main/resources/db/migration/V2__create_roles_and_privileges.sql`:
```sql
CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE privileges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE role_privileges (
    role_id UUID NOT NULL REFERENCES roles(id),
    privilege_id UUID NOT NULL REFERENCES privileges(id),
    PRIMARY KEY (role_id, privilege_id)
);

INSERT INTO privileges (code) VALUES
    ('AUTH.LOGIN'), ('AUTH.LOGOUT'),
    ('PROFILE.VIEW'), ('PROFILE.UPDATE'), ('PROFILE.CHANGE_PASSWORD'), ('PROFILE.RESET_PASSWORD'),
    ('USER.CREATE'), ('USER.VIEW'), ('USER.LIST'), ('USER.UPDATE'), ('USER.DELETE'), ('USER.CHANGE_PASSWORD'),
    ('ROLE.VIEW'), ('ROLE.LIST');

INSERT INTO roles (code, name) VALUES
    ('ADMIN', 'Administrator'), ('SELLER', 'Seller'), ('BUYER', 'Buyer'), ('SUPPORT_STAFF', 'Support Staff');

-- ADMIN: every seeded privilege
INSERT INTO role_privileges (role_id, privilege_id)
SELECT (SELECT id FROM roles WHERE code = 'ADMIN'), id FROM privileges;

-- BUYER and SELLER: self-service auth/profile privileges only
INSERT INTO role_privileges (role_id, privilege_id)
SELECT r.id, p.id
FROM roles r, privileges p
WHERE r.code IN ('BUYER', 'SELLER')
  AND p.code IN ('AUTH.LOGIN', 'AUTH.LOGOUT', 'PROFILE.VIEW', 'PROFILE.UPDATE', 'PROFILE.CHANGE_PASSWORD', 'PROFILE.RESET_PASSWORD');

-- SUPPORT_STAFF: auth/profile plus read-only user/role visibility
INSERT INTO role_privileges (role_id, privilege_id)
SELECT r.id, p.id
FROM roles r, privileges p
WHERE r.code = 'SUPPORT_STAFF'
  AND p.code IN ('AUTH.LOGIN', 'AUTH.LOGOUT', 'PROFILE.VIEW', 'PROFILE.UPDATE', 'PROFILE.CHANGE_PASSWORD',
                 'USER.VIEW', 'USER.LIST', 'ROLE.VIEW', 'ROLE.LIST');
```

Note: this seeds only the subset of the SRS §2.7 privilege catalog exercised by this
sub-project. Extending it to the full ~60-privilege list is a matter of adding more
`INSERT` rows in a later migration — not a design change.

`services/user-service/src/main/java/com/nexus/user/domain/model/Role.java`:
```java
package com.nexus.user.domain.model;

import java.util.Set;

public record Role(String id, String code, String name, Set<String> privilegeCodes) {
}
```

`services/user-service/src/main/java/com/nexus/user/application/port/out/RoleRepositoryPort.java`:
```java
package com.nexus.user.application.port.out;

import com.nexus.user.domain.model.Role;

import java.util.Optional;

public interface RoleRepositoryPort {
    Optional<Role> findByCode(String code);
    Optional<Role> findById(String id);
}
```

`services/user-service/src/main/java/com/nexus/user/infrastructure/persistence/entity/PrivilegeJpaEntity.java`:
```java
package com.nexus.user.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "privileges")
public class PrivilegeJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    protected PrivilegeJpaEntity() {
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
}
```

`services/user-service/src/main/java/com/nexus/user/infrastructure/persistence/entity/RoleJpaEntity.java`:
```java
package com.nexus.user.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles")
public class RoleJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "role_privileges",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "privilege_id"))
    private Set<PrivilegeJpaEntity> privileges;

    protected RoleJpaEntity() {
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public Set<PrivilegeJpaEntity> getPrivileges() { return privileges; }
}
```

`services/user-service/src/main/java/com/nexus/user/infrastructure/persistence/RoleJpaRepository.java`:
```java
package com.nexus.user.infrastructure.persistence;

import com.nexus.user.infrastructure.persistence.entity.RoleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, UUID> {
    Optional<RoleJpaEntity> findByCode(String code);
}
```

`services/user-service/src/main/java/com/nexus/user/infrastructure/persistence/RoleRepositoryAdapter.java`:
```java
package com.nexus.user.infrastructure.persistence;

import com.nexus.user.application.port.out.RoleRepositoryPort;
import com.nexus.user.domain.model.Role;
import com.nexus.user.infrastructure.persistence.entity.PrivilegeJpaEntity;
import com.nexus.user.infrastructure.persistence.entity.RoleJpaEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class RoleRepositoryAdapter implements RoleRepositoryPort {

    private final RoleJpaRepository roleJpaRepository;

    public RoleRepositoryAdapter(RoleJpaRepository roleJpaRepository) {
        this.roleJpaRepository = roleJpaRepository;
    }

    @Override
    public Optional<Role> findByCode(String code) {
        return roleJpaRepository.findByCode(code).map(this::toDomain);
    }

    @Override
    public Optional<Role> findById(String id) {
        return roleJpaRepository.findById(UUID.fromString(id)).map(this::toDomain);
    }

    private Role toDomain(RoleJpaEntity entity) {
        Set<String> privilegeCodes = entity.getPrivileges().stream()
                .map(PrivilegeJpaEntity::getCode)
                .collect(Collectors.toSet());
        return new Role(entity.getId().toString(), entity.getCode(), entity.getName(), privilegeCodes);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl services/user-service -am test`
Expected: PASS — Flyway applies V1 and V2 (schema + seed), `findByCode("BUYER")` returns
a `Role` whose `privilegeCodes` contains `PROFILE.CHANGE_PASSWORD` and `AUTH.LOGIN`.

- [ ] **Step 5: Commit**

```bash
git add services/user-service/src/main/java/com/nexus/user/domain/model/Role.java \
        services/user-service/src/main/java/com/nexus/user/application/port/out/RoleRepositoryPort.java \
        services/user-service/src/main/java/com/nexus/user/infrastructure/persistence \
        services/user-service/src/main/resources/db/migration/V2__create_roles_and_privileges.sql \
        services/user-service/src/test/java/com/nexus/user/infrastructure/persistence/RoleRepositoryAdapterTest.java
git commit -m "feat(user-service): add Role/Privilege persistence with seeded RBAC data"
```

---

## Task 8: RegisterUserUseCase — application logic + persistence adapters

Event publishing is deliberately **not** included here — it is added in Task 11 once
`common-events` (Task 10) exists, so the use case grows by one call rather than being
written with a dependency that doesn't compile yet.

**Files:**
- Create: `services/user-service/src/main/java/com/nexus/user/application/port/out/UserRepositoryPort.java`
- Create: `services/user-service/src/main/java/com/nexus/user/application/port/out/PasswordHasherPort.java`
- Create: `services/user-service/src/main/java/com/nexus/user/application/usecase/RegisterUserUseCase.java`
- Create: `services/user-service/src/main/java/com/nexus/user/application/exception/DuplicateEmailException.java`
- Create: `services/user-service/src/main/java/com/nexus/user/infrastructure/persistence/entity/UserJpaEntity.java`
- Create: `services/user-service/src/main/java/com/nexus/user/infrastructure/persistence/UserJpaRepository.java`
- Create: `services/user-service/src/main/java/com/nexus/user/infrastructure/persistence/UserRepositoryAdapter.java`
- Create: `services/user-service/src/main/java/com/nexus/user/infrastructure/security/BCryptPasswordHasherAdapter.java`
- Modify: `services/user-service/pom.xml` (add `spring-boot-starter-security` — brings in `BCryptPasswordEncoder`)
- Test: `services/user-service/src/test/java/com/nexus/user/application/usecase/RegisterUserUseCaseTest.java`
- Test: `services/user-service/src/test/java/com/nexus/user/infrastructure/persistence/UserRepositoryAdapterTest.java`

**Interfaces:**
- Consumes: `RoleRepositoryPort` (Task 7), `PasswordPolicy` (Task 6).
- Produces: `RegisterUserUseCase.register(RegisterUserCommand command)` returning
  `UserRegistrationResult(String userId, String email, String fullName)`, where
  `RegisterUserCommand(String email, String rawPassword, String fullName)` — the REST
  layer (Task 9) calls this directly. Throws `DuplicateEmailException` (extends
  `ConflictException`, code `EMAIL_ALREADY_REGISTERED`) when the email is taken.

- [ ] **Step 1: Write the failing tests**

`services/user-service/src/test/java/com/nexus/user/application/usecase/RegisterUserUseCaseTest.java`:
```java
package com.nexus.user.application.usecase;

import com.nexus.user.application.exception.DuplicateEmailException;
import com.nexus.user.application.port.out.PasswordHasherPort;
import com.nexus.user.application.port.out.RoleRepositoryPort;
import com.nexus.user.application.port.out.UserRepositoryPort;
import com.nexus.user.domain.model.Role;
import com.nexus.user.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegisterUserUseCaseTest {

    private UserRepositoryPort userRepositoryPort;
    private RoleRepositoryPort roleRepositoryPort;
    private PasswordHasherPort passwordHasherPort;
    private RegisterUserUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepositoryPort = mock(UserRepositoryPort.class);
        roleRepositoryPort = mock(RoleRepositoryPort.class);
        passwordHasherPort = mock(PasswordHasherPort.class);
        useCase = new RegisterUserUseCase(userRepositoryPort, roleRepositoryPort, passwordHasherPort);

        when(roleRepositoryPort.findByCode("BUYER"))
                .thenReturn(Optional.of(new Role("role-buyer", "BUYER", "Buyer", Set.of("AUTH.LOGIN"))));
        when(passwordHasherPort.hash("longenough")).thenReturn("hashed-longenough");
        when(userRepositoryPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void register_savesNewUserWithDefaultBuyerRole() {
        when(userRepositoryPort.findByEmail("alice@example.com")).thenReturn(Optional.empty());

        UserRegistrationResult result = useCase.register(
                new RegisterUserCommand("alice@example.com", "longenough", "Alice Nguyen"));

        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(result.fullName()).isEqualTo("Alice Nguyen");
        verify(userRepositoryPort).save(argThat(u ->
                u.getEmail().equals("alice@example.com")
                        && u.getHashedPassword().equals("hashed-longenough")
                        && u.getRoleId().value().equals("role-buyer")));
    }

    @Test
    void register_rejectsDuplicateEmail() {
        when(userRepositoryPort.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(mock(User.class)));

        assertThatThrownBy(() -> useCase.register(
                new RegisterUserCommand("alice@example.com", "longenough", "Alice Nguyen")))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepositoryPort, never()).save(any());
    }
}
```

`services/user-service/src/test/java/com/nexus/user/infrastructure/persistence/UserRepositoryAdapterTest.java`:
```java
package com.nexus.user.infrastructure.persistence;

import com.nexus.user.domain.model.RoleId;
import com.nexus.user.domain.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserRepositoryAdapter.class)
class UserRepositoryAdapterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("user_db").withUsername("nexus").withPassword("nexus");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRepositoryAdapter userRepositoryAdapter;

    @Test
    void saveThenFindByEmail_roundTripsTheUser() {
        User user = User.register("bob@example.com", "hashed-pw", "Bob Tran",
                new RoleId(UUID.randomUUID().toString()));

        userRepositoryAdapter.save(user);
        Optional<User> found = userRepositoryAdapter.findByEmail("bob@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Bob Tran");
    }

    @Test
    void findByEmail_returnsEmptyWhenNotFound() {
        assertThat(userRepositoryAdapter.findByEmail("nobody@example.com")).isEmpty();
    }
}
```

Add to `services/user-service/pom.xml`, inside `<dependencies>`:
```xml
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl services/user-service -am test`
Expected: FAIL — `RegisterUserUseCase`, `UserRepositoryAdapter`, etc. do not exist.

- [ ] **Step 3: Write the implementation**

`services/user-service/src/main/java/com/nexus/user/application/port/out/UserRepositoryPort.java`:
```java
package com.nexus.user.application.port.out;

import com.nexus.user.domain.model.User;

import java.util.Optional;

public interface UserRepositoryPort {
    User save(User user);
    Optional<User> findByEmail(String email);
    Optional<User> findById(String id);
}
```

`services/user-service/src/main/java/com/nexus/user/application/port/out/PasswordHasherPort.java`:
```java
package com.nexus.user.application.port.out;

public interface PasswordHasherPort {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String hashedPassword);
}
```

`services/user-service/src/main/java/com/nexus/user/application/exception/DuplicateEmailException.java`:
```java
package com.nexus.user.application.exception;

import com.nexus.common.core.exception.ConflictException;

public class DuplicateEmailException extends ConflictException {
    public DuplicateEmailException(String email) {
        super("EMAIL_ALREADY_REGISTERED", "Email already registered: " + email);
    }
}
```

`services/user-service/src/main/java/com/nexus/user/application/usecase/RegisterUserUseCase.java`
(put `RegisterUserCommand` and `UserRegistrationResult` as top-level records in the same
package, in their own files):
```java
package com.nexus.user.application.usecase;

public record RegisterUserCommand(String email, String rawPassword, String fullName) {
}
```
```java
package com.nexus.user.application.usecase;

public record UserRegistrationResult(String userId, String email, String fullName) {
}
```
```java
package com.nexus.user.application.usecase;

import com.nexus.user.application.exception.DuplicateEmailException;
import com.nexus.user.application.port.out.PasswordHasherPort;
import com.nexus.user.application.port.out.RoleRepositoryPort;
import com.nexus.user.application.port.out.UserRepositoryPort;
import com.nexus.user.domain.model.Role;
import com.nexus.user.domain.model.RoleId;
import com.nexus.user.domain.model.User;
import com.nexus.user.domain.service.PasswordPolicy;
import org.springframework.stereotype.Service;
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
```

Note: `RegisterUserUseCase` is instantiated as a Spring bean explicitly in Task 9's
`@Configuration` class (application/use-case classes stay framework-agnostic — no
`@Service` annotation on the use case itself — while wiring happens in `infrastructure`
or a small `config` package, consistent with the dependency-direction rule in the spec).

`services/user-service/src/main/java/com/nexus/user/infrastructure/persistence/entity/UserJpaEntity.java`:
```java
package com.nexus.user.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "hashed_password", nullable = false)
    private String hashedPassword;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserJpaEntity() {
    }

    public UserJpaEntity(UUID id, String email, String hashedPassword, String fullName, UUID roleId, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.hashedPassword = hashedPassword;
        this.fullName = fullName;
        this.roleId = roleId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getHashedPassword() { return hashedPassword; }
    public String getFullName() { return fullName; }
    public UUID getRoleId() { return roleId; }
    public Instant getCreatedAt() { return createdAt; }
}
```

`services/user-service/src/main/java/com/nexus/user/infrastructure/persistence/UserJpaRepository.java`:
```java
package com.nexus.user.infrastructure.persistence;

import com.nexus.user.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {
    Optional<UserJpaEntity> findByEmail(String email);
}
```

`services/user-service/src/main/java/com/nexus/user/infrastructure/persistence/UserRepositoryAdapter.java`:
```java
package com.nexus.user.infrastructure.persistence;

import com.nexus.user.application.port.out.UserRepositoryPort;
import com.nexus.user.domain.model.RoleId;
import com.nexus.user.domain.model.User;
import com.nexus.user.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final UserJpaRepository userJpaRepository;

    public UserRepositoryAdapter(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = new UserJpaEntity(
                UUID.fromString(user.getId()),
                user.getEmail(),
                user.getHashedPassword(),
                user.getFullName(),
                UUID.fromString(user.getRoleId().value()),
                user.getCreatedAt());
        userJpaRepository.save(entity);
        return user;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public Optional<User> findById(String id) {
        return userJpaRepository.findById(UUID.fromString(id)).map(this::toDomain);
    }

    private User toDomain(UserJpaEntity entity) {
        return User.reconstitute(
                entity.getId().toString(),
                entity.getEmail(),
                entity.getHashedPassword(),
                entity.getFullName(),
                new RoleId(entity.getRoleId().toString()),
                entity.getCreatedAt());
    }
}
```

`services/user-service/src/main/java/com/nexus/user/infrastructure/security/BCryptPasswordHasherAdapter.java`:
```java
package com.nexus.user.infrastructure.security;

import com.nexus.user.application.port.out.PasswordHasherPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordHasherAdapter implements PasswordHasherPort {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hashedPassword) {
        return encoder.matches(rawPassword, hashedPassword);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl services/user-service -am test`
Expected: PASS, all tests green.

- [ ] **Step 5: Commit**

```bash
git add services/user-service
git commit -m "feat(user-service): add RegisterUserUseCase with persistence and password hashing adapters"
```

---

## Task 9: Register REST endpoint

**Files:**
- Create: `services/user-service/src/main/java/com/nexus/user/api/UserController.java`
- Create: `services/user-service/src/main/java/com/nexus/user/api/dto/request/RegisterUserRequest.java`
- Create: `services/user-service/src/main/java/com/nexus/user/api/dto/response/UserResponse.java`
- Create: `services/user-service/src/main/java/com/nexus/user/api/mapper/UserApiMapper.java`
- Create: `services/user-service/src/main/java/com/nexus/user/infrastructure/config/UseCaseConfig.java`
- Create: `services/user-service/src/main/java/com/nexus/user/infrastructure/config/SecurityConfig.java`
- Modify: `services/user-service/pom.xml` (add MapStruct dependency + annotation processor)
- Test: `services/user-service/src/test/java/com/nexus/user/api/UserControllerTest.java`

**Interfaces:**
- Consumes: `RegisterUserUseCase` (Task 8).
- Produces: `POST /api/v1/users/register` — request body `{"email","password","fullName"}`,
  201 response `{"success":true,"data":{"id","email","fullName"}}` on success, 409 via
  `GlobalExceptionHandler` (Task 3) on duplicate email, 400 on bean-validation failures.

- [ ] **Step 1: Write the failing test**

`services/user-service/src/test/java/com/nexus/user/api/UserControllerTest.java`:
```java
package com.nexus.user.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.user.application.exception.DuplicateEmailException;
import com.nexus.user.application.usecase.RegisterUserUseCase;
import com.nexus.user.application.usecase.UserRegistrationResult;
import com.nexus.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@WebMvcTest(UserController.class)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegisterUserUseCase registerUserUseCase;

    @Test
    void register_returns201WithCreatedUser() throws Exception {
        when(registerUserUseCase.register(any()))
                .thenReturn(new UserRegistrationResult("user-1", "alice@example.com", "Alice Nguyen"));

        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"longenough","fullName":"Alice Nguyen"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"));
    }

    @Test
    void register_returns409WhenEmailTaken() throws Exception {
        when(registerUserUseCase.register(any()))
                .thenThrow(new DuplicateEmailException("alice@example.com"));

        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"longenough","fullName":"Alice Nguyen"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void register_returns400WhenEmailBlank() throws Exception {
        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"","password":"longenough","fullName":"Alice Nguyen"}"""))
                .andExpect(status().isBadRequest());
    }
}
```

Add to `services/user-service/pom.xml`, inside `<dependencies>`:
```xml
    <dependency>
      <groupId>org.mapstruct</groupId>
      <artifactId>mapstruct</artifactId>
      <version>${mapstruct.version}</version>
    </dependency>
```
And inside `<build><plugins>`, configure the compiler plugin for the MapStruct annotation processor (add this `<plugin>` entry alongside `spring-boot-maven-plugin`):
```xml
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <annotationProcessorPaths>
            <path>
              <groupId>org.mapstruct</groupId>
              <artifactId>mapstruct-processor</artifactId>
              <version>${mapstruct.version}</version>
            </path>
          </annotationProcessorPaths>
        </configuration>
      </plugin>
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl services/user-service -am test`
Expected: FAIL — `UserController` does not exist.

- [ ] **Step 3: Write the implementation**

`services/user-service/src/main/java/com/nexus/user/api/dto/request/RegisterUserRequest.java`:
```java
package com.nexus.user.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterUserRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotBlank String fullName) {
}
```

`services/user-service/src/main/java/com/nexus/user/api/dto/response/UserResponse.java`:
```java
package com.nexus.user.api.dto.response;

public record UserResponse(String id, String email, String fullName) {
}
```

`services/user-service/src/main/java/com/nexus/user/api/mapper/UserApiMapper.java`:
```java
package com.nexus.user.api.mapper;

import com.nexus.user.api.dto.request.RegisterUserRequest;
import com.nexus.user.api.dto.response.UserResponse;
import com.nexus.user.application.usecase.RegisterUserCommand;
import com.nexus.user.application.usecase.UserRegistrationResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserApiMapper {

    RegisterUserCommand toCommand(RegisterUserRequest request);

    UserResponse toResponse(UserRegistrationResult result);
}
```
(MapStruct generates the implementation at compile time as `UserApiMapperImpl` in
`target/generated-sources` — nothing else to write by hand. Field names match exactly
across both records, so no `@Mapping` overrides are needed: `RegisterUserRequest.password`
→ `RegisterUserCommand.rawPassword` is the one mismatch, so add
`@Mapping(source = "password", target = "rawPassword")` above `toCommand`.)

Corrected mapper (replace the interface body above with this — the single `@Mapping` was
missing):
```java
package com.nexus.user.api.mapper;

import com.nexus.user.api.dto.request.RegisterUserRequest;
import com.nexus.user.api.dto.response.UserResponse;
import com.nexus.user.application.usecase.RegisterUserCommand;
import com.nexus.user.application.usecase.UserRegistrationResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserApiMapper {

    @Mapping(source = "password", target = "rawPassword")
    RegisterUserCommand toCommand(RegisterUserRequest request);

    @Mapping(source = "userId", target = "id")
    UserResponse toResponse(UserRegistrationResult result);
}
```

`services/user-service/src/main/java/com/nexus/user/api/UserController.java`:
```java
package com.nexus.user.api;

import com.nexus.common.core.ApiResponse;
import com.nexus.user.api.dto.request.RegisterUserRequest;
import com.nexus.user.api.dto.response.UserResponse;
import com.nexus.user.api.mapper.UserApiMapper;
import com.nexus.user.application.usecase.RegisterUserUseCase;
import com.nexus.user.application.usecase.UserRegistrationResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final RegisterUserUseCase registerUserUseCase;
    private final UserApiMapper mapper;

    public UserController(RegisterUserUseCase registerUserUseCase, UserApiMapper mapper) {
        this.registerUserUseCase = registerUserUseCase;
        this.mapper = mapper;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterUserRequest request) {
        UserRegistrationResult result = registerUserUseCase.register(mapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(mapper.toResponse(result)));
    }
}
```

`services/user-service/src/main/java/com/nexus/user/infrastructure/config/UseCaseConfig.java`:
```java
package com.nexus.user.infrastructure.config;

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
                                                     PasswordHasherPort passwordHasherPort) {
        return new RegisterUserUseCase(userRepositoryPort, roleRepositoryPort, passwordHasherPort);
    }
}
```

`services/user-service/src/main/java/com/nexus/user/infrastructure/config/SecurityConfig.java`
(temporary — permits everything; Task 15 replaces this with JWT-filter-backed rules):
```java
package com.nexus.user.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl services/user-service -am test`
Expected: PASS, all 3 controller tests green.

- [ ] **Step 5: Manual end-to-end check**

Run: `docker-compose up -d postgres-user discovery-server`, then
`mvn -q -pl services/user-service -am spring-boot:run`, then in another terminal:
```bash
curl -X POST http://localhost:8081/api/v1/users/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"longenough","fullName":"Alice Nguyen"}'
```
Expected: HTTP 201 with the created user's `id`, `email`, `fullName`. Stop the app
(Ctrl+C) and `docker-compose down` afterward.

- [ ] **Step 6: Commit**

```bash
git add services/user-service
git commit -m "feat(user-service): expose POST /api/v1/users/register REST endpoint"
```

---

## Task 10: common-events — domain event envelope

**Files:**
- Create: `libs/common-events/pom.xml` (finalize)
- Create: `libs/common-events/src/main/java/com/nexus/common/events/DomainEvent.java`
- Create: `libs/common-events/src/main/java/com/nexus/common/events/UserRegisteredEvent.java`
- Test: `libs/common-events/src/test/java/com/nexus/common/events/UserRegisteredEventTest.java`

**Interfaces:**
- Produces: `DomainEvent` base (`String eventId`, `String eventType`, `Instant occurredAt`,
  `String aggregateId`) and `UserRegisteredEvent extends DomainEvent` (`String userId`,
  `String email`, `String fullName`), both Jackson-serializable — consumed by the outbox
  publisher (Task 11) and, eventually, by Notification Service (a later sub-project) when
  it deserializes events off the `user-events` Kafka topic.

- [ ] **Step 1: Write the failing test**

`libs/common-events/src/test/java/com/nexus/common/events/UserRegisteredEventTest.java`:
```java
package com.nexus.common.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserRegisteredEventTest {

    @Test
    void serializesAndDeserializesRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        UserRegisteredEvent event = new UserRegisteredEvent("user-1", "alice@example.com", "Alice Nguyen");

        String json = mapper.writeValueAsString(event);
        UserRegisteredEvent parsed = mapper.readValue(json, UserRegisteredEvent.class);

        assertThat(parsed.getUserId()).isEqualTo("user-1");
        assertThat(parsed.getEmail()).isEqualTo("alice@example.com");
        assertThat(parsed.getEventType()).isEqualTo("UserRegistered");
        assertThat(parsed.getAggregateId()).isEqualTo("user-1");
    }
}
```

`libs/common-events/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.nexus</groupId>
    <artifactId>project-nexus</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>
  <artifactId>common-events</artifactId>
  <packaging>jar</packaging>
  <dependencies>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.datatype</groupId>
      <artifactId>jackson-datatype-jsr310</artifactId>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl libs/common-events -am test`
Expected: FAIL — `UserRegisteredEvent` does not exist.

- [ ] **Step 3: Write the implementation**

`libs/common-events/src/main/java/com/nexus/common/events/DomainEvent.java`:
```java
package com.nexus.common.events;

import java.time.Instant;
import java.util.UUID;

public abstract class DomainEvent {

    private final String eventId;
    private final String eventType;
    private final Instant occurredAt;
    private final String aggregateId;

    protected DomainEvent(String eventType, String aggregateId) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.occurredAt = Instant.now();
        this.aggregateId = aggregateId;
    }

    protected DomainEvent(String eventId, String eventType, Instant occurredAt, String aggregateId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.aggregateId = aggregateId;
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getAggregateId() { return aggregateId; }
}
```

`libs/common-events/src/main/java/com/nexus/common/events/UserRegisteredEvent.java`:
```java
package com.nexus.common.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class UserRegisteredEvent extends DomainEvent {

    private final String userId;
    private final String email;
    private final String fullName;

    public UserRegisteredEvent(String userId, String email, String fullName) {
        super("UserRegistered", userId);
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
    }

    @JsonCreator
    public UserRegisteredEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("aggregateId") String aggregateId,
            @JsonProperty("userId") String userId,
            @JsonProperty("email") String email,
            @JsonProperty("fullName") String fullName) {
        super(eventId, eventType, occurredAt, aggregateId);
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
    }

    public String getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl libs/common-events -am test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add libs/common-events
git commit -m "feat(common-events): add DomainEvent envelope and UserRegisteredEvent"
```

---

## Task 11: Outbox pattern — reliable event publishing on register

**Files:**
- Create: `services/user-service/src/main/java/com/nexus/user/application/port/out/EventPublisherPort.java`
- Create: `services/user-service/src/main/java/com/nexus/user/infrastructure/persistence/entity/OutboxJpaEntity.java`
- Create: `services/user-service/src/main/java/com/nexus/user/infrastructure/persistence/OutboxJpaRepository.java`
- Create: `services/user-service/src/main/java/com/nexus/user/infrastructure/messaging/OutboxEventPublisherAdapter.java`
- Create: `services/user-service/src/main/resources/db/migration/V3__create_outbox_table.sql`
- Modify: `services/user-service/src/main/java/com/nexus/user/application/usecase/RegisterUserUseCase.java`
  (constructor gains an `EventPublisherPort` parameter; `register()` publishes a
  `UserRegisteredEvent` after saving the user, inside the same `@Transactional` method)
- Modify: `services/user-service/src/main/java/com/nexus/user/infrastructure/config/UseCaseConfig.java`
  (pass the new `EventPublisherPort` bean into `RegisterUserUseCase`'s constructor)
- Modify: `services/user-service/src/test/java/com/nexus/user/application/usecase/RegisterUserUseCaseTest.java`
  (add a mocked `EventPublisherPort`, verify `publish(...)` is called with a
  `UserRegisteredEvent` carrying the saved user's id)
- Test: `services/user-service/src/test/java/com/nexus/user/infrastructure/messaging/OutboxEventPublisherAdapterTest.java`

**Interfaces:**
- Consumes: `DomainEvent`, `UserRegisteredEvent` (Task 10).
- Produces: `EventPublisherPort.publish(DomainEvent event)` — implemented by
  `OutboxEventPublisherAdapter`, which only writes a row to the `outbox` table (no Kafka
  call here — that is Task 12's `OutboxRelayJob`). Because this happens inside
  `RegisterUserUseCase`'s existing `@Transactional`, "user saved" and "event queued" commit
  or roll back together.

- [ ] **Step 1: Write the failing tests**

Update `services/user-service/src/test/java/com/nexus/user/application/usecase/RegisterUserUseCaseTest.java`
— add the import `com.nexus.user.application.port.out.EventPublisherPort` and
`com.nexus.common.events.UserRegisteredEvent`, then change `setUp()` and the constructor
call to include a mocked publisher, and add one assertion to the success test:

```java
    private EventPublisherPort eventPublisherPort;

    @BeforeEach
    void setUp() {
        userRepositoryPort = mock(UserRepositoryPort.class);
        roleRepositoryPort = mock(RoleRepositoryPort.class);
        passwordHasherPort = mock(PasswordHasherPort.class);
        eventPublisherPort = mock(EventPublisherPort.class);
        useCase = new RegisterUserUseCase(userRepositoryPort, roleRepositoryPort, passwordHasherPort, eventPublisherPort);
        // ...same stubbing as before...
    }
```

Add to `register_savesNewUserWithDefaultBuyerRole`, after the existing `verify(userRepositoryPort)...` line:
```java
        verify(eventPublisherPort).publish(argThat(event ->
                event instanceof UserRegisteredEvent registered
                        && registered.getEmail().equals("alice@example.com")));
```

`services/user-service/src/test/java/com/nexus/user/infrastructure/messaging/OutboxEventPublisherAdapterTest.java`:
```java
package com.nexus.user.infrastructure.messaging;

import com.nexus.common.events.UserRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OutboxEventPublisherAdapter.class)
class OutboxEventPublisherAdapterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("user_db").withUsername("nexus").withPassword("nexus");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OutboxEventPublisherAdapter adapter;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Test
    void publish_writesUnpublishedOutboxRow() {
        adapter.publish(new UserRegisteredEvent("user-1", "alice@example.com", "Alice Nguyen"));

        var rows = outboxJpaRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getEventType()).isEqualTo("UserRegistered");
        assertThat(rows.get(0).getAggregateId()).isEqualTo("user-1");
        assertThat(rows.get(0).getPublishedAt()).isNull();
        assertThat(rows.get(0).getPayload()).contains("alice@example.com");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl services/user-service -am test`
Expected: FAIL — `EventPublisherPort`, `OutboxEventPublisherAdapter`, `OutboxJpaRepository`
do not exist, and `RegisterUserUseCase`'s constructor signature doesn't match the updated test yet.

- [ ] **Step 3: Write the implementation**

`services/user-service/src/main/resources/db/migration/V3__create_outbox_table.sql`:
```sql
CREATE TABLE outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;
```

`services/user-service/src/main/java/com/nexus/user/application/port/out/EventPublisherPort.java`:
```java
package com.nexus.user.application.port.out;

import com.nexus.common.events.DomainEvent;

public interface EventPublisherPort {
    void publish(DomainEvent event);
}
```

`services/user-service/src/main/java/com/nexus/user/infrastructure/persistence/entity/OutboxJpaEntity.java`:
```java
package com.nexus.user.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox")
public class OutboxJpaEntity {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxJpaEntity() {
    }

    public OutboxJpaEntity(UUID id, String aggregateId, String eventType, String payload, Instant createdAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void markPublished(Instant when) { this.publishedAt = when; }
}
```

`services/user-service/src/main/java/com/nexus/user/infrastructure/persistence/OutboxJpaRepository.java`:
```java
package com.nexus.user.infrastructure.persistence;

import com.nexus.user.infrastructure.persistence.entity.OutboxJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, UUID> {
    List<OutboxJpaEntity> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}
```

`services/user-service/src/main/java/com/nexus/user/infrastructure/messaging/OutboxEventPublisherAdapter.java`:
```java
package com.nexus.user.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.events.DomainEvent;
import com.nexus.user.application.port.out.EventPublisherPort;
import com.nexus.user.infrastructure.persistence.OutboxJpaRepository;
import com.nexus.user.infrastructure.persistence.entity.OutboxJpaEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OutboxEventPublisherAdapter implements EventPublisherPort {

    private final OutboxJpaRepository outboxJpaRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisherAdapter(OutboxJpaRepository outboxJpaRepository, ObjectMapper objectMapper) {
        this.outboxJpaRepository = outboxJpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(DomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxJpaEntity entity = new OutboxJpaEntity(
                    UUID.randomUUID(), event.getAggregateId(), event.getEventType(), payload, event.getOccurredAt());
            outboxJpaRepository.save(entity);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize event for outbox: " + event.getEventType(), e);
        }
    }
}
```

Update `services/user-service/src/main/java/com/nexus/user/application/usecase/RegisterUserUseCase.java`
— add the `EventPublisherPort` field/constructor parameter and publish after save:
```java
package com.nexus.user.application.usecase;

import com.nexus.common.events.UserRegisteredEvent;
import com.nexus.user.application.exception.DuplicateEmailException;
import com.nexus.user.application.port.out.EventPublisherPort;
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
    private final EventPublisherPort eventPublisherPort;

    public RegisterUserUseCase(UserRepositoryPort userRepositoryPort,
                                RoleRepositoryPort roleRepositoryPort,
                                PasswordHasherPort passwordHasherPort,
                                EventPublisherPort eventPublisherPort) {
        this.userRepositoryPort = userRepositoryPort;
        this.roleRepositoryPort = roleRepositoryPort;
        this.passwordHasherPort = passwordHasherPort;
        this.eventPublisherPort = eventPublisherPort;
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

        eventPublisherPort.publish(new UserRegisteredEvent(saved.getId(), saved.getEmail(), saved.getFullName()));

        return new UserRegistrationResult(saved.getId(), saved.getEmail(), saved.getFullName());
    }
}
```

Update `services/user-service/src/main/java/com/nexus/user/infrastructure/config/UseCaseConfig.java`:
```java
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
```

Also add `libs/common-events` as a dependency of `services/user-service/pom.xml`:
```xml
    <dependency>
      <groupId>com.nexus</groupId>
      <artifactId>common-events</artifactId>
      <version>${project.version}</version>
    </dependency>
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl services/user-service -am test`
Expected: PASS, all tests green, including the updated `RegisterUserUseCaseTest`.

- [ ] **Step 5: Commit**

```bash
git add services/user-service
git commit -m "feat(user-service): write UserRegistered events to the outbox transactionally on register"
```

---

## Task 12: OutboxRelayJob — publish outbox rows to Kafka

**Files:**
- Create: `services/user-service/src/main/java/com/nexus/user/infrastructure/messaging/OutboxRelayJob.java`
- Modify: `services/user-service/src/main/java/com/nexus/user/UserServiceApplication.java` (add `@EnableScheduling`)
- Modify: `services/user-service/src/main/resources/application.yml` (add `spring.kafka.*`)
- Modify: `services/user-service/pom.xml` (add `spring-kafka`)
- Modify: `docker-compose.yml` (add a Kafka service in KRaft mode)
- Test: `services/user-service/src/test/java/com/nexus/user/infrastructure/messaging/OutboxRelayJobTest.java`

**Interfaces:**
- Consumes: `OutboxJpaRepository` (Task 11).
- Produces: a `@Scheduled` job publishing to Kafka topic `user-events`, keyed by
  `aggregateId`, marking each outbox row's `publishedAt` only after a successful send —
  a failed send leaves the row unpublished for the next run (retry-by-repolling, per the
  spec's "outbox auto-retries with backoff" — the fixed 5-second poll interval **is** the
  backoff for this sub-project; exponential backoff is not implemented here).

- [ ] **Step 1: Write the failing test**

`services/user-service/src/test/java/com/nexus/user/infrastructure/messaging/OutboxRelayJobTest.java`:
```java
package com.nexus.user.infrastructure.messaging;

import com.nexus.user.infrastructure.persistence.OutboxJpaRepository;
import com.nexus.user.infrastructure.persistence.entity.OutboxJpaEntity;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OutboxRelayJobTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("user_db").withUsername("nexus").withPassword("nexus");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.1"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Autowired
    private OutboxRelayJob outboxRelayJob;

    private KafkaConsumer<String, String> consumer;

    @AfterEach
    void tearDown() {
        if (consumer != null) consumer.close();
    }

    @Test
    void relay_publishesUnpublishedRowAndMarksItPublished() {
        OutboxJpaEntity row = new OutboxJpaEntity(
                UUID.randomUUID(), "user-42", "UserRegistered",
                "{\"userId\":\"user-42\"}", Instant.now());
        outboxJpaRepository.save(row);

        outboxRelayJob.relayPendingEvents();

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(List.of("user-events"));

        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
        assertThat(records.count()).isEqualTo(1);
        ConsumerRecord<String, String> record = records.iterator().next();
        assertThat(record.key()).isEqualTo("user-42");
        assertThat(record.value()).contains("user-42");

        OutboxJpaEntity reloaded = outboxJpaRepository.findById(row.getId()).orElseThrow();
        assertThat(reloaded.getPublishedAt()).isNotNull();
    }
}
```

Add to `services/user-service/pom.xml`, inside `<dependencies>`:
```xml
    <dependency>
      <groupId>org.springframework.kafka</groupId>
      <artifactId>spring-kafka</artifactId>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>kafka</artifactId>
      <scope>test</scope>
    </dependency>
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl services/user-service -am test`
Expected: FAIL — `OutboxRelayJob` does not exist.

- [ ] **Step 3: Write the implementation**

`services/user-service/src/main/java/com/nexus/user/infrastructure/messaging/OutboxRelayJob.java`:
```java
package com.nexus.user.infrastructure.messaging;

import com.nexus.user.infrastructure.persistence.OutboxJpaRepository;
import com.nexus.user.infrastructure.persistence.entity.OutboxJpaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxRelayJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayJob.class);
    private static final String TOPIC = "user-events";

    private final OutboxJpaRepository outboxJpaRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelayJob(OutboxJpaRepository outboxJpaRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxJpaRepository = outboxJpaRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    public void relayPendingEvents() {
        List<OutboxJpaEntity> pending = outboxJpaRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxJpaEntity row : pending) {
            try {
                kafkaTemplate.send(TOPIC, row.getAggregateId(), row.getPayload()).get(5, TimeUnit.SECONDS);
                row.markPublished(Instant.now());
                outboxJpaRepository.save(row);
            } catch (Exception e) {
                log.error("Failed to publish outbox row {} (event {}) — will retry on next poll",
                        row.getId(), row.getEventType(), e);
            }
        }
    }
}
```

`services/user-service/src/main/java/com/nexus/user/UserServiceApplication.java` (add `@EnableScheduling`):
```java
package com.nexus.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.nexus")
@EnableDiscoveryClient
@EnableScheduling
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
```

Add to `services/user-service/src/main/resources/application.yml`, under `spring:`:
```yaml
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
```

Add a Kafka service (KRaft mode, no Zookeeper) to `docker-compose.yml`, and wire
`user-service`'s Kafka bootstrap server to it:
```yaml
  kafka:
    image: apache/kafka:3.7.1
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
```
And add `SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092` to `user-service`'s `environment:`
block, plus `kafka: condition: service_started` to its `depends_on:`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl services/user-service -am test`
Expected: PASS — the job publishes the row to the real (Testcontainers) Kafka broker and
marks it published.

- [ ] **Step 5: Commit**

```bash
git add services/user-service docker-compose.yml
git commit -m "feat(user-service): add OutboxRelayJob publishing pending events to Kafka"
```

---

## Task 13: common-security — JwtTokenProvider

**Files:**
- Create: `libs/common-security/pom.xml` (finalize)
- Create: `libs/common-security/src/main/java/com/nexus/common/security/JwtProperties.java`
- Create: `libs/common-security/src/main/java/com/nexus/common/security/JwtTokenProvider.java`
- Test: `libs/common-security/src/test/java/com/nexus/common/security/JwtTokenProviderTest.java`

**Interfaces:**
- Produces: `JwtTokenProvider.generateToken(String userId, String role, List<String> privileges)`
  returning a signed JWT string; `parseClaims(String token)` returning `io.jsonwebtoken.Claims`
  (throws `io.jsonwebtoken.JwtException` on invalid/expired/tampered tokens);
  `isValid(String token)` returning `boolean`. Configured via `nexus.security.jwt.secret`
  and `nexus.security.jwt.expiration-minutes` (default 60, matching SRS
  `ACCESS_TOKEN_EXPIRY_MINUTES`). Consumed by `user-service`'s `LoginUseCase` (Task 14) and
  `JwtAuthenticationFilter` (Task 15), and by `api-gateway`'s validation filter (Task 17).

- [ ] **Step 1: Write the failing test**

`libs/common-security/src/test/java/com/nexus/common/security/JwtTokenProviderTest.java`:
```java
package com.nexus.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-key-must-be-at-least-256-bits-long-for-hs256!!");
        properties.setExpirationMinutes(60);
        provider = new JwtTokenProvider(properties);
    }

    @Test
    void generateThenParse_roundTripsClaims() {
        String token = provider.generateToken("user-1", "BUYER", List.of("AUTH.LOGIN", "PROFILE.VIEW"));

        Claims claims = provider.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("user-1");
        assertThat(claims.get("role", String.class)).isEqualTo("BUYER");
        assertThat(claims.get("privileges", List.class)).containsExactly("AUTH.LOGIN", "PROFILE.VIEW");
    }

    @Test
    void isValid_returnsTrueForFreshToken() {
        String token = provider.generateToken("user-1", "BUYER", List.of("AUTH.LOGIN"));
        assertThat(provider.isValid(token)).isTrue();
    }

    @Test
    void isValid_returnsFalseForTamperedToken() {
        String token = provider.generateToken("user-1", "BUYER", List.of("AUTH.LOGIN"));
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThat(provider.isValid(tampered)).isFalse();
    }

    @Test
    void parseClaims_throwsForGarbageToken() {
        assertThatThrownBy(() -> provider.parseClaims("not-a-jwt")).isInstanceOf(JwtException.class);
    }
}
```

`libs/common-security/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.nexus</groupId>
    <artifactId>project-nexus</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>
  <artifactId>common-security</artifactId>
  <packaging>jar</packaging>
  <dependencies>
    <dependency>
      <groupId>com.nexus</groupId>
      <artifactId>common-core</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-aop</artifactId>
    </dependency>
    <dependency>
      <groupId>org.aspectj</groupId>
      <artifactId>aspectjweaver</artifactId>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-api</artifactId>
      <version>${jjwt.version}</version>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-impl</artifactId>
      <version>${jjwt.version}</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-jackson</artifactId>
      <version>${jjwt.version}</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl libs/common-security -am test`
Expected: FAIL — `JwtProperties`, `JwtTokenProvider` do not exist.

- [ ] **Step 3: Write the implementation**

`libs/common-security/src/main/java/com/nexus/common/security/JwtProperties.java`:
```java
package com.nexus.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus.security.jwt")
public class JwtProperties {

    private String secret;
    private long expirationMinutes = 60;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public long getExpirationMinutes() { return expirationMinutes; }
    public void setExpirationMinutes(long expirationMinutes) { this.expirationMinutes = expirationMinutes; }
}
```

`libs/common-security/src/main/java/com/nexus/common/security/JwtTokenProvider.java`:
```java
package com.nexus.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Component
@EnableConfigurationProperties(JwtProperties.class)
public class JwtTokenProvider {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String userId, String role, List<String> privileges) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getExpirationMinutes() * 60);

        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .claim("privileges", privileges)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl libs/common-security -am test`
Expected: PASS, all 4 tests green.

- [ ] **Step 5: Commit**

```bash
git add libs/common-security
git commit -m "feat(common-security): add JwtTokenProvider for issuing and validating JWTs"
```

---

## Task 14: LoginUseCase + AuthController

**Files:**
- Create: `services/user-service/src/main/java/com/nexus/user/application/exception/InvalidCredentialsException.java`
- Create: `services/user-service/src/main/java/com/nexus/user/application/usecase/LoginCommand.java`
- Create: `services/user-service/src/main/java/com/nexus/user/application/usecase/LoginResult.java`
- Create: `services/user-service/src/main/java/com/nexus/user/application/usecase/LoginUseCase.java`
- Create: `services/user-service/src/main/java/com/nexus/user/api/AuthController.java`
- Create: `services/user-service/src/main/java/com/nexus/user/api/dto/request/LoginRequest.java`
- Create: `services/user-service/src/main/java/com/nexus/user/api/dto/response/AuthResponse.java`
- Modify: `services/user-service/src/main/java/com/nexus/user/infrastructure/config/UseCaseConfig.java` (add `loginUseCase` bean)
- Modify: `services/user-service/pom.xml` (add `common-security` dependency)
- Test: `services/user-service/src/test/java/com/nexus/user/application/usecase/LoginUseCaseTest.java`
- Test: `services/user-service/src/test/java/com/nexus/user/api/AuthControllerTest.java`

**Interfaces:**
- Consumes: `JwtTokenProvider` (Task 13), `RoleRepositoryPort` (Task 7).
- Produces: `POST /api/v1/auth/login` — request `{"email","password"}`, response
  `{"success":true,"data":{"token"}}`; 401 (`INVALID_CREDENTIALS`) on bad email/password
  via `GlobalExceptionHandler`.

- [ ] **Step 1: Write the failing tests**

`services/user-service/src/test/java/com/nexus/user/application/usecase/LoginUseCaseTest.java`:
```java
package com.nexus.user.application.usecase;

import com.nexus.common.security.JwtTokenProvider;
import com.nexus.user.application.exception.InvalidCredentialsException;
import com.nexus.user.application.port.out.PasswordHasherPort;
import com.nexus.user.application.port.out.RoleRepositoryPort;
import com.nexus.user.application.port.out.UserRepositoryPort;
import com.nexus.user.domain.model.Role;
import com.nexus.user.domain.model.RoleId;
import com.nexus.user.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginUseCaseTest {

    private UserRepositoryPort userRepositoryPort;
    private RoleRepositoryPort roleRepositoryPort;
    private PasswordHasherPort passwordHasherPort;
    private JwtTokenProvider jwtTokenProvider;
    private LoginUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepositoryPort = mock(UserRepositoryPort.class);
        roleRepositoryPort = mock(RoleRepositoryPort.class);
        passwordHasherPort = mock(PasswordHasherPort.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        useCase = new LoginUseCase(userRepositoryPort, roleRepositoryPort, passwordHasherPort, jwtTokenProvider);
    }

    @Test
    void login_returnsTokenForValidCredentials() {
        User user = User.reconstitute("user-1", "alice@example.com", "hashed-pw", "Alice Nguyen",
                new RoleId("role-buyer"), java.time.Instant.now());
        when(userRepositoryPort.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordHasherPort.matches("longenough", "hashed-pw")).thenReturn(true);
        when(roleRepositoryPort.findById("role-buyer"))
                .thenReturn(Optional.of(new Role("role-buyer", "BUYER", "Buyer", Set.of("AUTH.LOGIN"))));
        when(jwtTokenProvider.generateToken(eq("user-1"), eq("BUYER"), anyList())).thenReturn("signed-jwt");

        LoginResult result = useCase.login(new LoginCommand("alice@example.com", "longenough"));

        assertThat(result.token()).isEqualTo("signed-jwt");
        assertThat(result.userId()).isEqualTo("user-1");
    }

    @Test
    void login_rejectsUnknownEmail() {
        when(userRepositoryPort.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.login(new LoginCommand("nobody@example.com", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_rejectsWrongPassword() {
        User user = User.reconstitute("user-1", "alice@example.com", "hashed-pw", "Alice Nguyen",
                new RoleId("role-buyer"), java.time.Instant.now());
        when(userRepositoryPort.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordHasherPort.matches("wrong", "hashed-pw")).thenReturn(false);

        assertThatThrownBy(() -> useCase.login(new LoginCommand("alice@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
```

`services/user-service/src/test/java/com/nexus/user/api/AuthControllerTest.java`:
```java
package com.nexus.user.api;

import com.nexus.common.web.GlobalExceptionHandler;
import com.nexus.user.application.exception.InvalidCredentialsException;
import com.nexus.user.application.usecase.LoginResult;
import com.nexus.user.application.usecase.LoginUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoginUseCase loginUseCase;

    @Test
    void login_returns200WithToken() throws Exception {
        when(loginUseCase.login(any())).thenReturn(new LoginResult("signed-jwt", "user-1"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"longenough"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("signed-jwt"));
    }

    @Test
    void login_returns401ForInvalidCredentials() throws Exception {
        when(loginUseCase.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"wrong"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }
}
```

Add to `services/user-service/pom.xml`, inside `<dependencies>`:
```xml
    <dependency>
      <groupId>com.nexus</groupId>
      <artifactId>common-security</artifactId>
      <version>${project.version}</version>
    </dependency>
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl services/user-service -am test`
Expected: FAIL — `LoginUseCase`, `AuthController`, etc. do not exist.

- [ ] **Step 3: Write the implementation**

`services/user-service/src/main/java/com/nexus/user/application/exception/InvalidCredentialsException.java`:
```java
package com.nexus.user.application.exception;

import com.nexus.common.core.exception.UnauthorizedException;

public class InvalidCredentialsException extends UnauthorizedException {
    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Invalid email or password");
    }
}
```

`services/user-service/src/main/java/com/nexus/user/application/usecase/LoginCommand.java`:
```java
package com.nexus.user.application.usecase;

public record LoginCommand(String email, String rawPassword) {
}
```

`services/user-service/src/main/java/com/nexus/user/application/usecase/LoginResult.java`:
```java
package com.nexus.user.application.usecase;

public record LoginResult(String token, String userId) {
}
```

`services/user-service/src/main/java/com/nexus/user/application/usecase/LoginUseCase.java`:
```java
package com.nexus.user.application.usecase;

import com.nexus.common.security.JwtTokenProvider;
import com.nexus.user.application.exception.InvalidCredentialsException;
import com.nexus.user.application.port.out.PasswordHasherPort;
import com.nexus.user.application.port.out.RoleRepositoryPort;
import com.nexus.user.application.port.out.UserRepositoryPort;
import com.nexus.user.domain.model.Role;
import com.nexus.user.domain.model.User;

import java.util.List;

public class LoginUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final RoleRepositoryPort roleRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginUseCase(UserRepositoryPort userRepositoryPort,
                         RoleRepositoryPort roleRepositoryPort,
                         PasswordHasherPort passwordHasherPort,
                         JwtTokenProvider jwtTokenProvider) {
        this.userRepositoryPort = userRepositoryPort;
        this.roleRepositoryPort = roleRepositoryPort;
        this.passwordHasherPort = passwordHasherPort;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public LoginResult login(LoginCommand command) {
        User user = userRepositoryPort.findByEmail(command.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordHasherPort.matches(command.rawPassword(), user.getHashedPassword())) {
            throw new InvalidCredentialsException();
        }

        Role role = roleRepositoryPort.findById(user.getRoleId().value())
                .orElseThrow(() -> new IllegalStateException("User references a non-existent role: " + user.getRoleId()));

        String token = jwtTokenProvider.generateToken(user.getId(), role.code(), List.copyOf(role.privilegeCodes()));
        return new LoginResult(token, user.getId());
    }
}
```

`services/user-service/src/main/java/com/nexus/user/api/dto/request/LoginRequest.java`:
```java
package com.nexus.user.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
```

`services/user-service/src/main/java/com/nexus/user/api/dto/response/AuthResponse.java`:
```java
package com.nexus.user.api.dto.response;

public record AuthResponse(String token) {
}
```

`services/user-service/src/main/java/com/nexus/user/api/AuthController.java`:
```java
package com.nexus.user.api;

import com.nexus.common.core.ApiResponse;
import com.nexus.user.api.dto.request.LoginRequest;
import com.nexus.user.api.dto.response.AuthResponse;
import com.nexus.user.application.usecase.LoginCommand;
import com.nexus.user.application.usecase.LoginResult;
import com.nexus.user.application.usecase.LoginUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;

    public AuthController(LoginUseCase loginUseCase) {
        this.loginUseCase = loginUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = loginUseCase.login(new LoginCommand(request.email(), request.password()));
        return ResponseEntity.ok(ApiResponse.ok(new AuthResponse(result.token())));
    }
}
```

Update `services/user-service/src/main/java/com/nexus/user/infrastructure/config/UseCaseConfig.java`
— add the `loginUseCase` bean (keep the existing `registerUserUseCase` bean as-is):
```java
    @Bean
    public LoginUseCase loginUseCase(UserRepositoryPort userRepositoryPort,
                                      RoleRepositoryPort roleRepositoryPort,
                                      PasswordHasherPort passwordHasherPort,
                                      JwtTokenProvider jwtTokenProvider) {
        return new LoginUseCase(userRepositoryPort, roleRepositoryPort, passwordHasherPort, jwtTokenProvider);
    }
```
(add the matching imports: `com.nexus.user.application.usecase.LoginUseCase` and
`com.nexus.common.security.JwtTokenProvider`)

Add to `services/user-service/src/main/resources/application.yml` (top level):
```yaml
nexus:
  security:
    jwt:
      secret: "dev-only-secret-key-change-in-production-min-256-bits-long!!"
      expiration-minutes: 60
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl services/user-service -am test`
Expected: PASS, all tests green.

- [ ] **Step 5: Commit**

```bash
git add services/user-service
git commit -m "feat(user-service): add LoginUseCase issuing JWTs with resolved privileges"
```

---

## Task 15: @RequiresPrivilege enforcement — annotation, aspect, and JWT filter wiring

**Files:**
- Create: `libs/common-security/src/main/java/com/nexus/common/security/RequiresPrivilege.java`
- Create: `libs/common-security/src/main/java/com/nexus/common/security/PrivilegeAuthorizationAspect.java`
- Modify: `libs/common-security/pom.xml` (add `spring-security-core`)
- Create: `services/user-service/src/main/java/com/nexus/user/infrastructure/security/JwtAuthenticationFilter.java`
- Modify: `services/user-service/src/main/java/com/nexus/user/infrastructure/config/SecurityConfig.java`
  (replace the temporary permit-all rule with the real one, and register the filter)
- Test: `libs/common-security/src/test/java/com/nexus/common/security/PrivilegeAuthorizationAspectTest.java`

**Interfaces:**
- Produces: `@RequiresPrivilege("PRIVILEGE.CODE")` (method-level annotation), enforced by
  `PrivilegeAuthorizationAspect` against `SecurityContextHolder`'s current `Authentication`
  authorities — throws `UnauthorizedException` (no authentication at all) or
  `ForbiddenException` (authenticated but missing the privilege), both from `common-core`
  and both already mapped by `GlobalExceptionHandler` (Task 3). `user-service`'s
  `JwtAuthenticationFilter` populates that `Authentication` from a valid Bearer token's
  `privileges` claim (Task 13) before any controller runs. Consumed by
  `ChangePasswordUseCase`'s endpoint (Task 16) and every future privilege-gated endpoint in
  every other service.

- [ ] **Step 1: Write the failing test**

`libs/common-security/src/test/java/com/nexus/common/security/PrivilegeAuthorizationAspectTest.java`:
```java
package com.nexus.common.security;

import com.nexus.common.core.exception.ForbiddenException;
import com.nexus.common.core.exception.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(PrivilegeAuthorizationAspectTest.TestConfig.class)
class PrivilegeAuthorizationAspectTest {

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {
        @Bean
        PrivilegeAuthorizationAspect aspect() { return new PrivilegeAuthorizationAspect(); }

        @Bean
        ProtectedService protectedService() { return new ProtectedService(); }
    }

    @Component
    static class ProtectedService {
        @RequiresPrivilege("PROFILE.CHANGE_PASSWORD")
        public String doSensitiveThing() { return "done"; }
    }

    @Autowired
    private ProtectedService protectedService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsCallWhenAuthorityPresent() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-1", null,
                        List.of(new SimpleGrantedAuthority("PROFILE.CHANGE_PASSWORD"))));

        assertThat(protectedService.doSensitiveThing()).isEqualTo("done");
    }

    @Test
    void rejectsCallWhenAuthorityMissing() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-1", null,
                        List.of(new SimpleGrantedAuthority("PROFILE.VIEW"))));

        assertThatThrownBy(() -> protectedService.doSensitiveThing()).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void rejectsCallWhenNotAuthenticated() {
        assertThatThrownBy(() -> protectedService.doSensitiveThing()).isInstanceOf(UnauthorizedException.class);
    }
}
```

Add to `libs/common-security/pom.xml`, inside `<dependencies>`:
```xml
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-core</artifactId>
    </dependency>
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl libs/common-security -am test`
Expected: FAIL — `RequiresPrivilege`, `PrivilegeAuthorizationAspect` do not exist.

- [ ] **Step 3: Write the implementation**

`libs/common-security/src/main/java/com/nexus/common/security/RequiresPrivilege.java`:
```java
package com.nexus.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPrivilege {
    String value();
}
```

`libs/common-security/src/main/java/com/nexus/common/security/PrivilegeAuthorizationAspect.java`:
```java
package com.nexus.common.security;

import com.nexus.common.core.exception.ForbiddenException;
import com.nexus.common.core.exception.UnauthorizedException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class PrivilegeAuthorizationAspect {

    @Around("@annotation(com.nexus.common.security.RequiresPrivilege)")
    public Object checkPrivilege(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String required = method.getAnnotation(RequiresPrivilege.class).value();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("UNAUTHENTICATED", "Authentication required");
        }

        boolean hasPrivilege = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(required));
        if (!hasPrivilege) {
            throw new ForbiddenException("PRIVILEGE_DENIED", "Missing required privilege: " + required);
        }

        return joinPoint.proceed();
    }
}
```

(Spring Boot's `AopAutoConfiguration` enables `@EnableAspectJAutoProxy` automatically once
`spring-aop` and `aspectjweaver` are on the classpath — already added to
`libs/common-security/pom.xml` in Task 13 — so `user-service` needs no extra AOP wiring
beyond component-scanning `com.nexus`, which it already does.)

`services/user-service/src/main/java/com/nexus/user/infrastructure/security/JwtAuthenticationFilter.java`:
```java
package com.nexus.user.infrastructure.security;

import com.nexus.common.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtTokenProvider.isValid(token)) {
                Claims claims = jwtTokenProvider.parseClaims(token);
                String userId = claims.getSubject();
                @SuppressWarnings("unchecked")
                List<String> privileges = claims.get("privileges", List.class);
                List<SimpleGrantedAuthority> authorities = (privileges == null ? List.<String>of() : privileges)
                        .stream().map(SimpleGrantedAuthority::new).toList();

                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, authorities));
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

Replace `services/user-service/src/main/java/com/nexus/user/infrastructure/config/SecurityConfig.java`
entirely with:
```java
package com.nexus.user.infrastructure.config;

import com.nexus.user.infrastructure.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/v1/users/register", "/api/v1/auth/login", "/actuator/**").permitAll()
                    .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl libs/common-security -am test`
Expected: PASS, all 3 aspect tests green.

Run: `mvn -q -pl services/user-service -am test`
Expected: PASS — `UserControllerTest`/`AuthControllerTest` still pass (register/login stay
public), since `@WebMvcTest` doesn't load the full `SecurityConfig` filter chain by default.

- [ ] **Step 5: Commit**

```bash
git add libs/common-security services/user-service
git commit -m "feat(security): enforce @RequiresPrivilege via AOP, backed by a JWT authentication filter"
```

---

## Task 16: ChangePasswordUseCase — first real @RequiresPrivilege-protected endpoint

**Files:**
- Modify: `services/user-service/src/main/java/com/nexus/user/domain/model/User.java`
  (add a `withHashedPassword(String newHashedPassword)` method returning a copy)
- Create: `services/user-service/src/main/java/com/nexus/user/application/usecase/ChangePasswordCommand.java`
- Create: `services/user-service/src/main/java/com/nexus/user/application/usecase/ChangePasswordUseCase.java`
- Modify: `services/user-service/src/main/java/com/nexus/user/api/UserController.java` (add the endpoint)
- Create: `services/user-service/src/main/java/com/nexus/user/api/dto/request/ChangePasswordRequest.java`
- Modify: `services/user-service/src/main/java/com/nexus/user/infrastructure/config/UseCaseConfig.java`
  (add `changePasswordUseCase` bean)
- Test: `services/user-service/src/test/java/com/nexus/user/application/usecase/ChangePasswordUseCaseTest.java`
- Test: `services/user-service/src/test/java/com/nexus/user/api/ChangePasswordEndpointTest.java`
  (full `@SpringBootTest` — the first test in this plan that exercises the real
  `SecurityConfig` filter chain end-to-end, proving Task 15's pieces work together)

**Interfaces:**
- Consumes: `@RequiresPrivilege` (Task 15), `PasswordPolicy` (Task 6).
- Produces: `PUT /api/v1/users/me/password` — request `{"oldPassword","newPassword"}`,
  200 on success; 401 if no/invalid token or wrong old password; 403 if the token's
  `privileges` claim lacks `PROFILE.CHANGE_PASSWORD`.

- [ ] **Step 1: Write the failing tests**

`services/user-service/src/test/java/com/nexus/user/application/usecase/ChangePasswordUseCaseTest.java`:
```java
package com.nexus.user.application.usecase;

import com.nexus.user.application.exception.InvalidCredentialsException;
import com.nexus.user.application.port.out.PasswordHasherPort;
import com.nexus.user.application.port.out.UserRepositoryPort;
import com.nexus.user.domain.model.RoleId;
import com.nexus.user.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChangePasswordUseCaseTest {

    private UserRepositoryPort userRepositoryPort;
    private PasswordHasherPort passwordHasherPort;
    private ChangePasswordUseCase useCase;
    private User existingUser;

    @BeforeEach
    void setUp() {
        userRepositoryPort = mock(UserRepositoryPort.class);
        passwordHasherPort = mock(PasswordHasherPort.class);
        useCase = new ChangePasswordUseCase(userRepositoryPort, passwordHasherPort);
        existingUser = User.reconstitute("user-1", "carol@example.com", "hashed-old", "Carol Le",
                new RoleId("role-buyer"), Instant.now());
        when(userRepositoryPort.findById("user-1")).thenReturn(Optional.of(existingUser));
    }

    @Test
    void changePassword_savesNewHashWhenOldPasswordMatches() {
        when(passwordHasherPort.matches("oldpw", "hashed-old")).thenReturn(true);
        when(passwordHasherPort.hash("newlongpassword")).thenReturn("hashed-new");
        when(userRepositoryPort.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.changePassword(new ChangePasswordCommand("user-1", "oldpw", "newlongpassword"));

        verify(userRepositoryPort).save(argThat(u -> u.getHashedPassword().equals("hashed-new")));
    }

    @Test
    void changePassword_rejectsWrongOldPassword() {
        when(passwordHasherPort.matches("wrong", "hashed-old")).thenReturn(false);

        assertThatThrownBy(() -> useCase.changePassword(new ChangePasswordCommand("user-1", "wrong", "newlongpassword")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepositoryPort, never()).save(any());
    }
}
```

`services/user-service/src/test/java/com/nexus/user/api/ChangePasswordEndpointTest.java`:
```java
package com.nexus.user.api;

import com.nexus.common.security.JwtTokenProvider;
import com.nexus.user.application.port.out.PasswordHasherPort;
import com.nexus.user.domain.model.Role;
import com.nexus.user.domain.model.RoleId;
import com.nexus.user.domain.model.User;
import com.nexus.user.infrastructure.persistence.RoleRepositoryAdapter;
import com.nexus.user.infrastructure.persistence.UserRepositoryAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ChangePasswordEndpointTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("user_db").withUsername("nexus").withPassword("nexus");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepositoryAdapter userRepositoryAdapter;
    @Autowired private RoleRepositoryAdapter roleRepositoryAdapter;
    @Autowired private PasswordHasherPort passwordHasherPort;

    private String userId;

    @BeforeEach
    void createUser() {
        Role buyerRole = roleRepositoryAdapter.findByCode("BUYER").orElseThrow();
        User user = User.register("carol@example.com", passwordHasherPort.hash("oldpassword"), "Carol Le",
                new RoleId(buyerRole.id()));
        userId = userRepositoryAdapter.save(user).getId();
    }

    @Test
    void changePassword_succeedsWithValidTokenAndCorrectOldPassword() throws Exception {
        String token = jwtTokenProvider.generateToken(userId, "BUYER", List.of("PROFILE.CHANGE_PASSWORD"));

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"oldpassword","newPassword":"newlongpassword"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_returns401WithoutToken() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"oldpassword","newPassword":"newlongpassword"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_returns403WhenTokenLacksPrivilege() throws Exception {
        String token = jwtTokenProvider.generateToken(userId, "BUYER", List.of("PROFILE.VIEW"));

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"oldpassword","newPassword":"newlongpassword"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void changePassword_returns401WhenOldPasswordWrong() throws Exception {
        String token = jwtTokenProvider.generateToken(userId, "BUYER", List.of("PROFILE.CHANGE_PASSWORD"));

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"wrongpassword","newPassword":"newlongpassword"}"""))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl services/user-service -am test`
Expected: FAIL — `ChangePasswordUseCase`, the new endpoint, do not exist.

- [ ] **Step 3: Write the implementation**

Add to `services/user-service/src/main/java/com/nexus/user/domain/model/User.java`,
inside the class body:
```java
    public User withHashedPassword(String newHashedPassword) {
        return new User(id, email, newHashedPassword, fullName, roleId, createdAt);
    }
```

`services/user-service/src/main/java/com/nexus/user/application/usecase/ChangePasswordCommand.java`:
```java
package com.nexus.user.application.usecase;

public record ChangePasswordCommand(String userId, String oldRawPassword, String newRawPassword) {
}
```

`services/user-service/src/main/java/com/nexus/user/application/usecase/ChangePasswordUseCase.java`:
```java
package com.nexus.user.application.usecase;

import com.nexus.common.core.exception.NotFoundException;
import com.nexus.user.application.exception.InvalidCredentialsException;
import com.nexus.user.application.port.out.PasswordHasherPort;
import com.nexus.user.application.port.out.UserRepositoryPort;
import com.nexus.user.domain.model.User;
import com.nexus.user.domain.service.PasswordPolicy;
import org.springframework.transaction.annotation.Transactional;

public class ChangePasswordUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;

    public ChangePasswordUseCase(UserRepositoryPort userRepositoryPort, PasswordHasherPort passwordHasherPort) {
        this.userRepositoryPort = userRepositoryPort;
        this.passwordHasherPort = passwordHasherPort;
    }

    @Transactional
    public void changePassword(ChangePasswordCommand command) {
        User user = userRepositoryPort.findById(command.userId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + command.userId()));

        if (!passwordHasherPort.matches(command.oldRawPassword(), user.getHashedPassword())) {
            throw new InvalidCredentialsException();
        }

        PasswordPolicy.validate(command.newRawPassword());
        String newHashedPassword = passwordHasherPort.hash(command.newRawPassword());
        userRepositoryPort.save(user.withHashedPassword(newHashedPassword));
    }
}
```

`services/user-service/src/main/java/com/nexus/user/api/dto/request/ChangePasswordRequest.java`:
```java
package com.nexus.user.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(@NotBlank String oldPassword, @NotBlank String newPassword) {
}
```

Add to `services/user-service/src/main/java/com/nexus/user/api/UserController.java`
(new imports: `com.nexus.common.security.RequiresPrivilege`,
`com.nexus.user.api.dto.request.ChangePasswordRequest`,
`com.nexus.user.application.usecase.ChangePasswordCommand`,
`com.nexus.user.application.usecase.ChangePasswordUseCase`,
`org.springframework.security.core.Authentication`; add a `ChangePasswordUseCase` field
set via the constructor alongside the existing `RegisterUserUseCase`/`UserApiMapper`):
```java
    private final ChangePasswordUseCase changePasswordUseCase;

    public UserController(RegisterUserUseCase registerUserUseCase, UserApiMapper mapper,
                           ChangePasswordUseCase changePasswordUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.mapper = mapper;
        this.changePasswordUseCase = changePasswordUseCase;
    }

    @RequiresPrivilege("PROFILE.CHANGE_PASSWORD")
    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(Authentication authentication,
                                                              @Valid @RequestBody ChangePasswordRequest request) {
        String userId = (String) authentication.getPrincipal();
        changePasswordUseCase.changePassword(
                new ChangePasswordCommand(userId, request.oldPassword(), request.newPassword()));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
```

Update `services/user-service/src/main/java/com/nexus/user/infrastructure/config/UseCaseConfig.java`
— add the `changePasswordUseCase` bean (import `ChangePasswordUseCase`):
```java
    @Bean
    public ChangePasswordUseCase changePasswordUseCase(UserRepositoryPort userRepositoryPort,
                                                         PasswordHasherPort passwordHasherPort) {
        return new ChangePasswordUseCase(userRepositoryPort, passwordHasherPort);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl services/user-service -am test`
Expected: PASS, all tests green — this is the first test in the plan proving JWT issuance
(Task 14), the authentication filter, and `@RequiresPrivilege` enforcement (both Task 15)
all work together through a real HTTP request.

- [ ] **Step 5: Commit**

```bash
git add services/user-service
git commit -m "feat(user-service): add ChangePasswordUseCase as the first privilege-protected endpoint"
```

---

## Task 17: api-gateway — routing and coarse JWT validation

**Files:**
- Create: `platform/api-gateway/pom.xml` (finalize)
- Create: `platform/api-gateway/src/main/java/com/nexus/gateway/ApiGatewayApplication.java`
- Create: `platform/api-gateway/src/main/java/com/nexus/gateway/filter/JwtValidationGlobalFilter.java`
- Create: `platform/api-gateway/src/main/resources/application.yml`
- Test: `platform/api-gateway/src/test/java/com/nexus/gateway/filter/JwtValidationGlobalFilterTest.java`
- Test: `platform/api-gateway/src/test/java/com/nexus/gateway/ApiGatewayApplicationTests.java`

**Interfaces:**
- Consumes: `JwtTokenProvider` (Task 13).
- Produces: a gateway on port 8080 routing `/api/v1/users/**` and `/api/v1/auth/**` to
  `lb://user-service` (resolved via Eureka), rejecting any non-public path with 401 when
  the `Authorization` header is missing or the token fails `JwtTokenProvider.isValid(...)`.
  Per the spec, the gateway checks signature/expiry only — it never inspects the
  `privileges` claim; that stays each service's job via `@RequiresPrivilege` (Task 15/16).

- [ ] **Step 1: Write the failing tests**

`platform/api-gateway/src/test/java/com/nexus/gateway/filter/JwtValidationGlobalFilterTest.java`:
```java
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
```

`platform/api-gateway/src/test/java/com/nexus/gateway/ApiGatewayApplicationTests.java`:
```java
package com.nexus.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTests {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Test
    void contextLoads() {
    }
}
```

`platform/api-gateway/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.nexus</groupId>
    <artifactId>project-nexus</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>
  <artifactId>api-gateway</artifactId>
  <packaging>jar</packaging>
  <dependencies>
    <dependency>
      <groupId>com.nexus</groupId>
      <artifactId>common-security</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-gateway</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -pl platform/api-gateway -am test`
Expected: FAIL — `JwtValidationGlobalFilter`, `ApiGatewayApplication` do not exist.

- [ ] **Step 3: Write the implementation**

`platform/api-gateway/src/main/java/com/nexus/gateway/filter/JwtValidationGlobalFilter.java`:
```java
package com.nexus.gateway.filter;

import com.nexus.common.security.JwtTokenProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtValidationGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/api/v1/users/register", "/api/v1/auth/login", "/actuator");

    private final JwtTokenProvider jwtTokenProvider;

    public JwtValidationGlobalFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String token = extractToken(exchange.getRequest());
        if (token == null || !jwtTokenProvider.isValid(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
```

`platform/api-gateway/src/main/java/com/nexus/gateway/ApiGatewayApplication.java`:
```java
package com.nexus.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.nexus")
@EnableDiscoveryClient
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

`platform/api-gateway/src/main/resources/application.yml`:
```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**,/api/v1/auth/**

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka
  instance:
    prefer-ip-address: true

# Must match user-service's nexus.security.jwt.secret exactly — the gateway verifies
# the same signature user-service signed with. In a real deployment this value comes
# from a shared secret store, not a hardcoded default; acceptable for local dev only.
nexus:
  security:
    jwt:
      secret: "dev-only-secret-key-change-in-production-min-256-bits-long!!"

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl platform/api-gateway -am test`
Expected: PASS, all 5 tests green.

- [ ] **Step 5: Commit**

```bash
git add platform/api-gateway
git commit -m "feat(api-gateway): route to user-service via Eureka with coarse JWT validation"
```

---

## Task 18: Dockerfiles, consolidated docker-compose.yml, and an end-to-end smoke test

This task has no red/green test cycle (it is infrastructure, not application logic) — its
"test" is the manual smoke test in Step 3.

**Files:**
- Create: `platform/discovery-server/Dockerfile`
- Create: `platform/api-gateway/Dockerfile`
- Create: `services/user-service/Dockerfile`
- Modify: `docker-compose.yml` (replace entirely with the consolidated version below —
  earlier tasks built it up incrementally; this is the final, complete version, and it
  also fixes each service's Docker build `context` to the repo root, required because
  Maven's multi-module reactor needs the parent `pom.xml` and every `libs/*` module, not
  just the one service's own folder)
- Create: `infra/scripts/smoke-test.sh`

**Interfaces:**
- Produces: `docker-compose up --build -d` starting the full stack (Postgres, Kafka,
  discovery-server, api-gateway, user-service) reachable at `http://localhost:8080`
  through the gateway; `infra/scripts/smoke-test.sh` exercises register → login →
  change-password end-to-end against it.

- [ ] **Step 1: Write the Dockerfiles**

Each service's Dockerfile is identical except for its module path — copy this template,
substituting `<MODULE_PATH>` and `<ARTIFACT_ID>`:

`platform/discovery-server/Dockerfile`:
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY libs libs
COPY platform platform
COPY services services
RUN mvn -q -pl platform/discovery-server -am -DskipTests package

FROM eclipse-temurin:21-jre
COPY --from=build /workspace/platform/discovery-server/target/discovery-server-0.1.0-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

`platform/api-gateway/Dockerfile`:
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY libs libs
COPY platform platform
COPY services services
RUN mvn -q -pl platform/api-gateway -am -DskipTests package

FROM eclipse-temurin:21-jre
COPY --from=build /workspace/platform/api-gateway/target/api-gateway-0.1.0-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

`services/user-service/Dockerfile`:
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY libs libs
COPY platform platform
COPY services services
RUN mvn -q -pl services/user-service -am -DskipTests package

FROM eclipse-temurin:21-jre
COPY --from=build /workspace/services/user-service/target/user-service-0.1.0-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

(Every Dockerfile copies the whole monorepo into its build stage regardless of which
service it builds — simpler and uniform across all three, at the cost of Docker layer
caching efficiency. Optimizing that is out of scope for this sub-project.)

- [ ] **Step 2: Replace `docker-compose.yml` with the consolidated version**

```yaml
services:
  postgres-user:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: user_db
      POSTGRES_USER: nexus
      POSTGRES_PASSWORD: nexus
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U nexus -d user_db"]
      interval: 5s
      timeout: 5s
      retries: 10

  kafka:
    image: apache/kafka:3.7.1
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

  discovery-server:
    build:
      context: .
      dockerfile: platform/discovery-server/Dockerfile
    ports:
      - "8761:8761"

  api-gateway:
    build:
      context: .
      dockerfile: platform/api-gateway/Dockerfile
    depends_on:
      discovery-server:
        condition: service_started
    environment:
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://discovery-server:8761/eureka
    ports:
      - "8080:8080"

  user-service:
    build:
      context: .
      dockerfile: services/user-service/Dockerfile
    depends_on:
      postgres-user:
        condition: service_healthy
      discovery-server:
        condition: service_started
      kafka:
        condition: service_started
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres-user:5432/user_db
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://discovery-server:8761/eureka
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    ports:
      - "8081:8081"
```

- [ ] **Step 3: Write and run the smoke test**

`infra/scripts/smoke-test.sh`:
```bash
#!/usr/bin/env bash
set -euo pipefail

BASE_URL="http://localhost:8080"

echo "Registering user..."
REGISTER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/users/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke@example.com","password":"longenough","fullName":"Smoke Test"}')
echo "$REGISTER_RESPONSE"

echo "Logging in..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke@example.com","password":"longenough"}')
echo "$LOGIN_RESPONSE"

TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

echo "Changing password using the token returned by login..."
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X PUT "$BASE_URL/api/v1/users/me/password" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"oldPassword":"longenough","newPassword":"evenlongerpassword"}'

echo "Smoke test completed: register -> login -> change-password all succeeded through api-gateway."
```

Run:
```bash
docker-compose up --build -d
# wait ~30-45s for all services to register with Eureka
chmod +x infra/scripts/smoke-test.sh
./infra/scripts/smoke-test.sh
```
Expected: the register and login calls each return a `"success":true` JSON body, and the
final `curl` prints `HTTP 200`. Then `docker-compose down` to stop everything.

- [ ] **Step 4: Commit**

```bash
git add platform/discovery-server/Dockerfile platform/api-gateway/Dockerfile \
        services/user-service/Dockerfile docker-compose.yml infra/scripts/smoke-test.sh
git commit -m "feat(infra): add Dockerfiles, consolidated docker-compose stack, and e2e smoke test"
```

---

## Task 19: Per-service CI pipelines with independent image versioning

No red/green cycle — this is pipeline configuration. Verification is a real push through
GitHub Actions (Step 2).

**Files:**
- Create: `.github/workflows/discovery-server-ci.yml`
- Create: `.github/workflows/api-gateway-ci.yml`
- Create: `.github/workflows/user-service-ci.yml`

**Interfaces:**
- Produces: three independent pipelines, each triggered only by changes under its own
  service path (plus `libs/**` and the root `pom.xml`, since every service depends on the
  shared libraries) — proving the monorepo's per-service independence claim from
  ADR-0002 (Task 20). Each publishes its own Docker image to
  `ghcr.io/<repo>/<service>:<git-sha>` on push to `main`/`master`, using the built-in
  `GITHUB_TOKEN` (no extra registry secret needed for a repo you own).

- [ ] **Step 1: Write the three workflow files**

`.github/workflows/user-service-ci.yml`:
```yaml
name: user-service CI

on:
  push:
    branches: [main, master]
    paths:
      - 'services/user-service/**'
      - 'libs/**'
      - 'pom.xml'
  pull_request:
    paths:
      - 'services/user-service/**'
      - 'libs/**'
      - 'pom.xml'

jobs:
  build-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Test
        run: mvn -B -pl services/user-service -am test

  build-push-image:
    needs: build-test
    if: github.event_name == 'push'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/build-push-action@v5
        with:
          context: .
          file: services/user-service/Dockerfile
          push: true
          tags: ghcr.io/${{ github.repository }}/user-service:${{ github.sha }}
```

`.github/workflows/api-gateway-ci.yml` (identical shape, substitute the module):
```yaml
name: api-gateway CI

on:
  push:
    branches: [main, master]
    paths:
      - 'platform/api-gateway/**'
      - 'libs/**'
      - 'pom.xml'
  pull_request:
    paths:
      - 'platform/api-gateway/**'
      - 'libs/**'
      - 'pom.xml'

jobs:
  build-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Test
        run: mvn -B -pl platform/api-gateway -am test

  build-push-image:
    needs: build-test
    if: github.event_name == 'push'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/build-push-action@v5
        with:
          context: .
          file: platform/api-gateway/Dockerfile
          push: true
          tags: ghcr.io/${{ github.repository }}/api-gateway:${{ github.sha }}
```

`.github/workflows/discovery-server-ci.yml` (identical shape; no `libs/**` dependency
since `discovery-server` depends on no shared library):
```yaml
name: discovery-server CI

on:
  push:
    branches: [main, master]
    paths:
      - 'platform/discovery-server/**'
      - 'pom.xml'
  pull_request:
    paths:
      - 'platform/discovery-server/**'
      - 'pom.xml'

jobs:
  build-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Test
        run: mvn -B -pl platform/discovery-server -am test

  build-push-image:
    needs: build-test
    if: github.event_name == 'push'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/build-push-action@v5
        with:
          context: .
          file: platform/discovery-server/Dockerfile
          push: true
          tags: ghcr.io/${{ github.repository }}/discovery-server:${{ github.sha }}
```

- [ ] **Step 2: Verify on GitHub**

Push a small, harmless change under `services/user-service/` (e.g. a comment) to a branch,
open a PR, and confirm only `user-service CI` runs in the Checks tab — not the other two
workflows. Merge to `main`/`master` and confirm `build-push-image` runs and the image
appears under the repo's GitHub Packages tab.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows
git commit -m "ci: add per-service GitHub Actions pipelines with independent image tags"
```

---

## Task 20: Record the architecture decisions as ADRs

No red/green cycle — these are documentation files.

**Files:**
- Create: `docs/adr/0001-microservices-vs-monolith.md`
- Create: `docs/adr/0002-monorepo-for-microservices.md`

**Interfaces:**
- Produces: the two ADRs referenced throughout this plan and the spec's "Key decisions"
  section, in standard ADR format (Status / Context / Decision / Consequences).

- [ ] **Step 1: Write the ADRs**

`docs/adr/0001-microservices-vs-monolith.md`:
```markdown
# ADR-0001: Full microservices for all six SRS domains

## Status
Accepted

## Context
The project's SRS describes six business domains (User, Notification, Catalog, Commerce,
Auction, Fulfillment). A mentor's reference capacity-planning workbook, sized for a
hypothetical 8-person/8-month team, recommends a modular monolith (five domains sharing
one deployable, separate schemas) with only Auction split out as its own service — citing
"N microservices with too few developers" as a high-severity operational risk.

This project is a solo/small-team learning and portfolio effort, not the mentor's
hypothetical team, with a different goal: demonstrating genuine service boundaries,
independent deployability, and inter-service messaging for a CV, and building deep
enough understanding to explain the architecture to a 4-person student team.

## Decision
Build all six domains as genuinely independent Spring Boot microservices, each with its
own database, each independently deployable, communicating over the network (REST via
the gateway, events via Kafka) rather than in-process module calls.

## Consequences
- More operational surface than a monolith: six services to build, deploy, and keep
  healthy instead of one deployable plus a separate Auction service.
- Requires the full platform investment this plan covers: service discovery, an API
  gateway, and a message broker, before any second business service can be added.
- Each service's implementation cost (its own persistence, its own CI pipeline, its own
  Docker image) is paid six times instead of once — accepted deliberately for the
  CV/learning goal, and mitigated by keeping each service's own scope thin at first
  (this plan's `user-service` implements only register/login/change-password, not the
  full SRS §3.1.1 feature set).
- The mentor's operational-overhead concern does not disappear — it is accepted as a
  known trade-off for a portfolio project rather than a production team under a hard
  deadline, where it would not be an acceptable trade-off.
```

`docs/adr/0002-monorepo-for-microservices.md`:
```markdown
# ADR-0002: Monorepo, not one repository per service

## Status
Accepted

## Context
ADR-0001 commits to six independently deployable services. The two common ways to
organize their source are one Git repository per service (polyrepo) or all of them in a
single repository (monorepo). This project is built by one person; cross-cutting changes
(a shared library used by every service, such as `common-core`'s exception hierarchy)
are far more common early on than changes isolated to one service.

## Decision
Use a single Git repository containing all services and shared libraries, organized as a
Maven multi-module build (see the design spec's "Repository structure"). Independence at
deployment time is proven by tooling, not by repository count: each service has its own
GitHub Actions workflow triggered only by changes under its own path (plus `libs/**` and
the root `pom.xml`), and each publishes its own independently versioned Docker image
(tagged by git SHA) to the container registry.

This mirrors how large organizations (Google, Uber, and others) run genuine
microservices out of a monorepo — the repository boundary and the deployment boundary
are independent decisions.

## Consequences
- A single commit can span multiple services' code, which is what makes solo
  cross-cutting changes fast — the reason this decision was made.
- Anyone inspecting only the repository count (not the CI configuration or the deployed
  images) could mistake this for a monolith; this is called out explicitly wherever the
  distinction matters (design spec, this ADR) so it can be explained rather than
  discovered as a surprise.
- If the project ever needed genuinely separate access control per service (e.g.
  different teams with different repo permissions), this decision would need revisiting
  — not a concern for a solo project.
```

- [ ] **Step 2: Commit**

```bash
git add docs/adr
git commit -m "docs: record ADR-0001 (full microservices) and ADR-0002 (monorepo)"
```

---

## Self-Review

**Spec coverage:**
- Repository structure (Maven multi-module, `libs/`/`platform/`/`services/`) → Task 1.
- `discovery-server` → Task 4. `api-gateway` (routing, JWT validation) → Task 17.
- `user-service` register/login/change-password → Tasks 6-9, 13-16.
- Outbox pattern → Tasks 11-12. Health checks → Actuator included in every service's POM
  (Tasks 4, 5, 17). Structured logging → default Spring Boot JSON-capable logging is
  present out of the box; no extra task needed since the spec only requires
  console/file logging, not a log aggregator (explicitly out of scope).
- Per-service CI + independent Docker versioning → Task 19. ADRs → Task 20.
- Authentication vs. authorization split → Tasks 13-16 (JwtTokenProvider, login,
  `@RequiresPrivilege`, JwtAuthenticationFilter).
- Error handling (standardized JSON, 503 on missing service, 409/400/401/403) →
  Task 3 (`GlobalExceptionHandler`) plus each use case's specific exceptions.
- Testing strategy (unit, Testcontainers integration, manual smoke test) → present in
  every task; consolidated end-to-end smoke test → Task 18.
- Explicitly out of scope per the spec (K8s manifests, OpenTelemetry/Prometheus/Grafana,
  config server, Kafka schema registry, the other 5 business services) → correctly not
  covered by any task here; left for later sub-projects.

**Placeholder scan:** no "TBD"/"TODO"/"add appropriate ___" phrasing anywhere above; every
step includes real code or an exact command.

**Type consistency check (traced across tasks):**
- `RegisterUserUseCase`'s constructor grows from 3 params (Task 8) to 4 (Task 11, adding
  `EventPublisherPort`) — both the class and its Spring `@Bean` factory in
  `UseCaseConfig`, and its unit test, are updated together in Task 11.
- `User` gains `withHashedPassword(...)` in Task 16 without changing any earlier
  signature — additive, no earlier task invalidated.
- `RoleRepositoryPort`/`UserRepositoryPort`/`PasswordHasherPort`/`EventPublisherPort`
  method names and return types are declared once (Tasks 7, 8, 11) and reused verbatim
  in every later task that consumes them (11, 14, 16) — no renames.
- `common-security`'s `JwtTokenProvider.generateToken(userId, role, privileges)` /
  `parseClaims` / `isValid` (Task 13) are called with matching signatures in
  `LoginUseCase` (Task 14), `JwtAuthenticationFilter` (Task 15), and
  `JwtValidationGlobalFilter` in `api-gateway` (Task 17).

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-23-platform-foundation-user-service.md`.
Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review
between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch
execution with checkpoints.

Which approach?
