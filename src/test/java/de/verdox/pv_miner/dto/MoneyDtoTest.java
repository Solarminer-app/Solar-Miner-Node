package de.verdox.pv_miner.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.pv_miner.util.Money;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MoneyDtoTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesOneStableMoneyContractToBothFrontends() {
        MoneyDto dto = MoneyDto.from(new Money(12.5, CustomCurrency.getInstance("EUR")));

        JsonNode json = objectMapper.valueToTree(dto);

        assertEquals(12.5, json.get("amount").asDouble());
        assertEquals("EUR", json.get("currency").asText());
        assertFalse(json.has("rawMoneyAmount"));
        assertFalse(json.has("formatted"));
    }
}
