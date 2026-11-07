package com.agricontract.contract.infrastructure.terms;

import com.agricontract.contract.domain.model.terms.CoffeeGoodsTerms;
import com.agricontract.contract.domain.model.terms.ContractTemplateProfile;
import com.agricontract.contract.domain.model.terms.ContractTermsInput;
import com.agricontract.contract.domain.model.terms.ContractTermsSnapshot;
import com.agricontract.contract.domain.model.terms.TermsCanonicalizer;
import com.agricontract.contract.domain.model.terms.VndAmount;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Jackson builds the ContractTermsSnapshot wire shape; the JCS library alone defines the canonical bytes.
 * Uses its own mapper so a change to the application-wide ObjectMapper can never change signed hashes.
 * Null fields keep their key (hash-chain design: "null giữ nguyên key").
 */
@Component
public class JcsTermsCanonicalizer implements TermsCanonicalizer {
    private static final List<String> TEMPLATE_FIELDS = List.of(
            "contractTemplateCode", "contractTemplateVersion", "contractTemplateHash", "coffeeQualityProfileVersion");

    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new SimpleModule().addSerializer(VndAmount.class, new VndAmountSerializer())
                    .addDeserializer(VndAmount.class, new VndAmountDeserializer()))
            .serializationInclusion(JsonInclude.Include.ALWAYS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    @Override
    public String canonicalJson(ContractTermsSnapshot snapshot) {
        ObjectNode wire = mapper.valueToTree(snapshot.negotiated());
        wire.setAll((ObjectNode) mapper.valueToTree(snapshot.template()));
        wire.set("goodsTerms", mapper.valueToTree(snapshot.goodsTerms()));
        wire.put("totalContractValue", snapshot.totalContractValue().wire());
        try {
            return new String(new JsonCanonicalizer(mapper.writeValueAsString(wire)).getEncodedUTF8(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Contract terms could not be canonicalized", e);
        }
    }

    @Override
    public ContractTermsSnapshot parse(String canonicalJson) {
        try {
            ObjectNode wire = (ObjectNode) mapper.readTree(canonicalJson);
            ContractTemplateProfile template = new ContractTemplateProfile(
                    text(wire, "contractTemplateCode"), text(wire, "contractTemplateVersion"),
                    text(wire, "contractTemplateHash"), text(wire, "coffeeQualityProfileVersion"));
            TEMPLATE_FIELDS.forEach(wire::remove);
            CoffeeGoodsTerms goods = mapper.treeToValue(wire.remove("goodsTerms"), CoffeeGoodsTerms.class);
            VndAmount total = VndAmount.parse(text(wire, "totalContractValue"));
            wire.remove("totalContractValue");
            ContractTermsInput negotiated = mapper.treeToValue(wire, ContractTermsInput.class);
            return new ContractTermsSnapshot(negotiated, template, goods, total);
        } catch (JsonProcessingException | ClassCastException e) {
            throw new IllegalStateException("Stored contract terms are not a valid ContractTermsSnapshot", e);
        }
    }

    private static String text(ObjectNode wire, String field) {
        JsonNode node = wire.get(field);
        if (node == null || !node.isTextual()) {
            throw new IllegalStateException("Stored contract terms miss " + field);
        }
        return node.textValue();
    }

    private static final class VndAmountSerializer extends JsonSerializer<VndAmount> {
        @Override
        public void serialize(VndAmount value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.wire());
        }
    }

    private static final class VndAmountDeserializer extends JsonDeserializer<VndAmount> {
        @Override
        public VndAmount deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return VndAmount.parse(p.getValueAsString());
        }
    }
}
