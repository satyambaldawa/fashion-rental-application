package com.fashionrental.common.exception;

import com.fashionrental.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void should_return_400_with_error_envelope_when_upload_size_exceeded() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(15_000_000L);

        ResponseEntity<ApiResponse<Void>> response = handler.handleMaxUploadSizeExceeded(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().error()).isEqualTo("Total upload size must be less than 15MB");
    }

    @Test
    void should_return_409_with_stale_message_when_hibernate_rejects_a_stale_write() {
        var ex = new ObjectOptimisticLockingFailureException(String.class, "some-id");

        ResponseEntity<ApiResponse<Void>> response = handler.handleOptimisticLock(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error()).isEqualTo(StaleUpdateException.MESSAGE);
    }

    @Test
    void should_return_409_with_stale_message_when_a_stale_update_exception_is_thrown() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleConflict(new StaleUpdateException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error()).isEqualTo(StaleUpdateException.MESSAGE);
    }

    @Test
    void should_return_409_with_retry_message_when_a_lock_cannot_be_acquired() {
        var ex = new CannotAcquireLockException("deadlock detected");

        ResponseEntity<ApiResponse<Void>> response = handler.handleLockFailure(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error()).isEqualTo(GlobalExceptionHandler.LOCK_FAILURE_MESSAGE);
    }

    @Test
    void should_keep_the_stale_and_lock_failure_messages_distinct() {
        assertThat(GlobalExceptionHandler.LOCK_FAILURE_MESSAGE).isNotEqualTo(StaleUpdateException.MESSAGE);
    }

    @Test
    void should_not_let_the_catch_all_handler_swallow_concurrency_failures() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConcurrencyFailureController())
                .setControllerAdvice(handler)
                .build();

        mockMvc.perform(get("/optimistic")).andExpect(status().isConflict());
        mockMvc.perform(get("/pessimistic")).andExpect(status().isConflict());
    }

    @RestController
    static class ConcurrencyFailureController {

        @GetMapping("/optimistic")
        String optimistic() {
            throw new ObjectOptimisticLockingFailureException(String.class, "some-id");
        }

        @GetMapping("/pessimistic")
        String pessimistic() {
            throw new CannotAcquireLockException("deadlock detected");
        }
    }
}
