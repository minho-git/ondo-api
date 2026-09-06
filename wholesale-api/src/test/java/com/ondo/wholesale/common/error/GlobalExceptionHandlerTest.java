package com.ondo.wholesale.common.error;

import com.ondo.wholesale.common.trace.TraceIdFilter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Set;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 전역 예외 핸들러(요구 4·5) 검증. DB 비의존 standalone MockMvc.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(new TraceIdFilter())
                .build();
    }

    @Test
    void ResourceNotFoundException은_404_공통포맷() throws Exception {
        mvc.perform(get("/boom/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("없는 회원"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors", hasSize(0)))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 검증실패는_400_VALIDATION_FAILED_필드매핑() throws Exception {
        mvc.perform(post("/boom/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].reason").value(not(org.hamcrest.Matchers.emptyOrNullString())))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void 필수_헤더가_없으면_400_VALIDATION_FAILED_헤더명매핑() throws Exception {
        mvc.perform(get("/boom/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"))
                .andExpect(jsonPath("$.errors[0].data").doesNotExist())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void 알수없는_예외는_500_INTERNAL_ERROR() throws Exception {
        mvc.perform(get("/boom/unknown"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(0)))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void ConstraintViolationException은_400_VALIDATION_FAILED_필드매핑() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<SampleRequest>> violations = validator.validate(new SampleRequest(""));

        ResponseEntity<ErrorResponse> res = new GlobalExceptionHandler()
                .handleConstraint(new ConstraintViolationException(violations));

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        ErrorResponse body = res.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.errors()).extracting(ErrorResponse.FieldError::field).contains("name");
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/boom/not-found")
        void notFound() {
            throw new ResourceNotFoundException("없는 회원");
        }

        @PostMapping("/boom/validate")
        void validate(@Valid @RequestBody SampleRequest request) {
            // @Valid 위반 시 MethodArgumentNotValidException
        }

        @GetMapping("/boom/unknown")
        void unknown() {
            throw new IllegalStateException("예상 못한 오류");
        }

        @GetMapping("/boom/header")
        void header(@RequestHeader("Idempotency-Key") String key) {
            // 헤더 누락 시 MissingRequestHeaderException
        }
    }

    record SampleRequest(@NotBlank String name) {}
}
