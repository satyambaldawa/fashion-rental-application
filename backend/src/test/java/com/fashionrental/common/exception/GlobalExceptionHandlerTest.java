package com.fashionrental.common.exception;

import com.fashionrental.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

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
}
