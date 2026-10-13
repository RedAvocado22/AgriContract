package com.agricontract.user.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderAuthenticationFilterTest {

    private static final String GATEWAY_SECRET = "gateway-secret";
    private static final String INTERNAL_SECRET = "internal-secret";

    private final HeaderAuthenticationFilter filter = new HeaderAuthenticationFilter(GATEWAY_SECRET, INTERNAL_SECRET);

    @Test
    void internalPath_withInternalSecret_passes() throws Exception {
        MockHttpServletRequest request = request("/internal/v1/users/user-1");
        request.addHeader("X-Internal-Secret", INTERNAL_SECRET);

        assertPassed(request);
    }

    @Test
    void internalPath_withGatewaySecretOnly_isForbidden() throws Exception {
        MockHttpServletRequest request = request("/internal/v1/users/user-1");
        request.addHeader("X-Gateway-Secret", GATEWAY_SECRET);

        assertForbidden(request);
    }

    @Test
    void internalPath_withoutSecret_isForbidden() throws Exception {
        assertForbidden(request("/internal/v1/users/user-1"));
    }

    @Test
    void internalPath_withWrongInternalSecret_isForbidden() throws Exception {
        MockHttpServletRequest request = request("/internal/v1/users/user-1");
        request.addHeader("X-Internal-Secret", "wrong");

        assertForbidden(request);
    }

    @Test
    void publicPath_withGatewaySecret_stillPasses() throws Exception {
        MockHttpServletRequest request = request("/api/v1/users/me");
        request.addHeader("X-Gateway-Secret", GATEWAY_SECRET);

        assertPassed(request);
    }

    @Test
    void publicPath_withoutSecret_isForbidden() throws Exception {
        assertForbidden(request("/api/v1/users/me"));
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    private void assertPassed(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private void assertForbidden(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
    }
}
