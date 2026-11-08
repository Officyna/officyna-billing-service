package br.com.officyna.billing.infrastructure.messaging.resources;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratePaymentEventTest {

	private static final JsonMapper MAPPER = new JsonMapper();

	@Test
	@DisplayName("Aceita os nomes de campo que o os-service realmente manda (laborPrice/id/totalPrice), via @JsonAlias")
	void deveAceitarNomesDeCampoReaisDoOsService() {
		String json = """
				{
				  "serviceOrderId": "6af0ac790be5150d141cafc8",
				  "payerEmail": "contato@campanholi.com",
				  "customerDocument": "45539730810",
				  "labors": [
				    { "laborId": "6af09b5fc1377e537030fb69", "name": "Diagnostico", "description": "Leitura de erros",
				      "laborPrice": 150, "situation": "PENDENTE" }
				  ],
				  "supplies": [
				    { "id": "6af09b6dc1377e537030fb6b", "name": "Oleo Motor 5W30", "description": "Oleo sintetico",
				      "quantity": 10, "unitPrice": 59.67, "totalPrice": 596.70 }
				  ]
				}
				""";

		GeneratePaymentEvent event = MAPPER.readValue(json, GeneratePaymentEvent.class);

		assertThat(event.labors()).hasSize(1);
		assertThat(event.labors().get(0).amount()).isEqualByComparingTo(new BigDecimal("150"));

		assertThat(event.supplies()).hasSize(1);
		assertThat(event.supplies().get(0).supplyId()).isEqualTo("6af09b6dc1377e537030fb6b");
		assertThat(event.supplies().get(0).amount()).isEqualByComparingTo(new BigDecimal("596.70"));
	}

}
