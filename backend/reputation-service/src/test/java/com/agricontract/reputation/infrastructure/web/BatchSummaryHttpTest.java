package com.agricontract.reputation.infrastructure.web;

import com.agricontract.reputation.application.dto.BatchSummaryQuery;
import com.agricontract.reputation.application.dto.BatchSummaryResult;
import com.agricontract.reputation.application.usecase.GetPublicReputationSummaries;
import com.agricontract.reputation.infrastructure.config.SecurityConfiguration;
import com.agricontract.reputation.infrastructure.contract.FrozenSchemas;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalPublicSummaryController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class,
        properties = {"gateway.internal-secret=gateway-test-secret", "service.internal-secret=service-test-secret"})
@Import({SecurityConfiguration.class, PublicSummaryExceptionHandler.class})
class BatchSummaryHttpTest {
    private static final String PATH = "/internal/v1/reputation/public-summaries";
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockBean private GetPublicReputationSummaries summaries;

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void acceptsLimitsWithOnlyServiceSecretAndMatchesFrozenSchema(int size) throws Exception {
        var ids = IntStream.range(0, size).mapToObj(i -> new UUID(0, i)).toList();
        when(summaries.execute(any())).thenReturn(new BatchSummaryResult(ids.stream()
                .map(id -> new BatchSummaryResult.Item(id, null, false)).toList()));
        var result = mvc.perform(internal(json.writeValueAsString(Map.of("userIds", ids))))
                .andExpect(status().isOk()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        FrozenSchemas.assertResponse("ReputationBatchSummaryResponse", body);
        assertThat(body.path("items")).hasSize(size);
        verify(summaries).execute(new BatchSummaryQuery(ids));
    }

    static Stream<String> invalidBodies() {
        String id = "\"" + ID + "\"";
        String tooMany = IntStream.range(0, 101).mapToObj(i -> "\"" + new UUID(0, i) + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return Stream.of("{}", "{\"userIds\":null}", "{\"userIds\":[]}",
                "{\"userIds\":[" + tooMany + "]}", "{\"userIds\":[" + id + "," + id + "]}",
                "{\"userIds\":[\"not-uuid\"]}", "{\"userIds\":[\"1-1-1-1-1\"]}",
                "{\"userIds\":[" + id + "],\"extra\":true}", "{\"userIds\":[",
                "{\"userIds\":[null]}", "{\"userIds\":[123]}", "{\"userIds\":" + id + "}",
                "[]", "null", "", "{\"userIds\":[" + id + ",\"bad\"]}",
                "{\"userIds\":[" + id + "]} {}", "{\"userIds\":[" + id + "]} garbage",
                "{\"userIds\":[" + id + "],\"userIds\":[" + id + "]}",
                "{\"userIds\":[\"AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA\",\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\"]}");
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalidInputFailsBeforeApplicationRead(String body) throws Exception {
        var result = mvc.perform(internal(body)).andExpect(status().isBadRequest()).andReturn();
        FrozenSchemas.assertResponse("ErrorEnvelope", json.readTree(result.getResponse().getContentAsString()));
        verifyNoInteractions(summaries);
    }

    @Test
    void missingWrongGatewayForgedAndRepeatedSecretsAreUnauthorized() throws Exception {
        String body = json.writeValueAsString(Map.of("userIds", List.of(ID)));
        for (var request : List.of(request(body), request(body).header("X-Internal-Secret", "wrong"),
                request(body).header("X-Gateway-Secret", "gateway-test-secret"),
                request(body).header("X-Gateway-Secret", "gateway-test-secret")
                        .header("X-User-Id", ID).header("X-User-Role", "ADMIN"),
                internal(body).header("X-Internal-Secret", "service-test-secret"),
                internal(body).header("X-Internal-Secret", "wrong"),
                request(body).header("X-Internal-Secret", "service-test-secret,service-test-secret"))) {
            var result = mvc.perform(request).andExpect(status().isUnauthorized()).andReturn();
            FrozenSchemas.assertResponse("ErrorEnvelope", json.readTree(result.getResponse().getContentAsString()));
        }
        verifyNoInteractions(summaries);
    }

    @Test
    void applicationFailureIsSanitizedAndNotMisclassifiedAsValidation() throws Exception {
        for (var failure : List.of(new DataAccessResourceFailureException("private SQL password"),
                new IllegalArgumentException("private SQL password"))) {
            doThrow(failure).when(summaries).execute(any());
            var result = mvc.perform(internal(json.writeValueAsString(Map.of("userIds", List.of(ID)))))
                    .andExpect(status().isInternalServerError()).andReturn();
            String body = result.getResponse().getContentAsString();
            FrozenSchemas.assertResponse("ErrorEnvelope", json.readTree(body));
            assertThat(body).doesNotContain("private", "SQL", "password");
        }
    }

    private static MockHttpServletRequestBuilder request(String body) {
        return post(PATH).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder internal(String body) {
        return request(body).header("X-Internal-Secret", "service-test-secret");
    }
}
