package com.agricontract.product.infrastructure.client;

import com.agricontract.product.common.exception.UserProfileNotFoundException;
import com.agricontract.product.common.exception.UserServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserServiceClientTest {

    private static final String URL = "http://user-service/internal/v1/users/seller-1";

    private MockRestServiceServer server;
    private UserServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://user-service")
                .defaultHeader("X-Internal-Secret", "internal-secret");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new UserServiceClient(builder.build());
    }

    @Test
    void getEmail_ok_returnsEmailAndSendsInternalSecret() {
        server.expect(requestTo(URL))
                .andExpect(header("X-Internal-Secret", "internal-secret"))
                .andRespond(withSuccess("""
                        {"userId":"seller-1","organizationName":"ABC",
                         "email":"seller@test.com","role":"SELLER","verificationStatus":"PENDING",
                         "authorizationExpiresAt":null,"lockedUntil":null}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getEmail("seller-1")).isEqualTo("seller@test.com");
        server.verify();
    }

    @Test
    void getEmail_notFound_throwsUserProfileNotFound() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.getEmail("seller-1"))
                .isInstanceOf(UserProfileNotFoundException.class);
    }

    @Test
    void getEmail_unauthorized_throwsUnavailable() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.getEmail("seller-1"))
                .isInstanceOf(UserServiceUnavailableException.class);
    }

    @Test
    void getEmail_serverError_throwsUnavailable() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.getEmail("seller-1"))
                .isInstanceOf(UserServiceUnavailableException.class);
    }

    @Test
    void getEmail_missingEmail_throwsUnavailable() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"userId\":\"seller-1\",\"email\":null}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getEmail("seller-1"))
                .isInstanceOf(UserServiceUnavailableException.class);
    }
}
