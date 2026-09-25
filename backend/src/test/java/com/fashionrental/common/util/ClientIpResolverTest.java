package com.fashionrental.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static final int TRUNCATED_LENGTH = 45;

    @Test
    void should_use_remote_addr_when_no_forwarded_header_is_present() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void should_use_first_hop_when_forwarded_header_lists_several_proxies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 70.41.3.18, 150.172.238.178");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void should_fall_back_to_remote_addr_when_forwarded_header_is_blank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "   ");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void should_truncate_to_the_column_width_when_a_header_is_absurdly_long() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "x".repeat(200));

        assertThat(ClientIpResolver.resolve(request)).hasSize(TRUNCATED_LENGTH);
    }

    @Test
    void should_fall_back_to_remote_addr_when_the_first_forwarded_hop_is_empty() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", ",203.0.113.7");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("10.0.0.1");
    }
}
