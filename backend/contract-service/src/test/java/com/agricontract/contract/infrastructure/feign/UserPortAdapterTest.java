package com.agricontract.contract.infrastructure.feign;

import com.agricontract.contract.application.exception.UserServiceUnavailableException;
import feign.FeignException;
import feign.RetryableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserPortAdapterTest {
    private final UserServiceClient client = mock(UserServiceClient.class);
    private final UserPortAdapter adapter = new UserPortAdapter(client);

    @Test
    void unknownUser_isEmpty() {
        when(client.getUser("u-1")).thenThrow(mock(FeignException.NotFound.class));

        assertThat(adapter.findUser("u-1")).isEmpty();
    }

    @Test
    void anyOtherFailure_isUnavailable_neverAPass() {
        when(client.getUser("u-1")).thenThrow(mock(FeignException.ServiceUnavailable.class));
        assertThatThrownBy(() -> adapter.findUser("u-1")).isInstanceOf(UserServiceUnavailableException.class);

        when(client.getUser("u-2")).thenThrow(mock(RetryableException.class));
        assertThatThrownBy(() -> adapter.findUser("u-2")).isInstanceOf(UserServiceUnavailableException.class);
    }
}
