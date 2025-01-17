package lol.maki.cargotracker.interfaces.handling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lol.maki.cargotracker.Application;
import lol.maki.cargotracker.domain.model.cargo.TrackingId;
import lol.maki.cargotracker.domain.model.handling.HandlingEvent;
import lol.maki.cargotracker.domain.model.handling.HandlingEventRepository;
import lol.maki.cargotracker.domain.model.handling.HandlingHistory;
import lol.maki.cargotracker.infrastructure.sampledata.SampleLocations;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class HandlingReportIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private HandlingEventRepository repo;

	private final RestTemplate restTemplate = new RestTemplate();

	private final ObjectMapper mapper = new ObjectMapper();

	@Disabled // TODO investigate failure when not run in isolation
	@Transactional
	@Test
	void shouldReturn201ResponseWhenHandlingReportIsSubmitted() throws Exception {
		String body = mapper.writeValueAsString(
				ImmutableMap.of("completionTime", "2022-10-30T13:37:00", "trackingIds", List.of("ABC123"), "type",
						HandlingEvent.Type.CUSTOMS.name(), "unLocode", SampleLocations.DALLAS.unlocode));
		URI uri = new UriTemplate("http://localhost:{port}/handlingReport").expand(port);
		RequestEntity<String> request = RequestEntity.post(uri).contentType(MediaType.APPLICATION_JSON).body(body);

		ResponseEntity<String> response = restTemplate.exchange(request, String.class);
		assertThat(response.getStatusCodeValue()).isEqualTo(201);

		Thread.sleep(1000); // TODO replace with Awaitility

		HandlingHistory handlingHistory = repo.lookupHandlingHistoryOfCargo(new TrackingId("ABC123"));
		HandlingEvent handlingEvent = handlingHistory.mostRecentlyCompletedEvent();
		Assertions.assertThat(handlingEvent.cargo().trackingId().idString()).isEqualTo("ABC123");
		assertThat(handlingEvent).extracting("type", "location.unlocode")
			.containsExactly(HandlingEvent.Type.CUSTOMS, "USDAL");
	}

	@SuppressWarnings("unchecked")
	@Test
	void shouldReturnValidationErrorResponseWhenInvalidHandlingReportIsSubmitted() throws Exception {
		String body = mapper.writeValueAsString(ImmutableMap.of("completionTime", "invalid date", "trackingIds",
				List.of("ABC123"), "type", HandlingEvent.Type.CUSTOMS.name(), "unLocode",
				SampleLocations.STOCKHOLM.unlocode, "voyageNumber", "0101"));

		URI uri = new UriTemplate("http://localhost:{port}/handlingReport").expand(port);
		RequestEntity<String> request = RequestEntity.post(uri).contentType(MediaType.APPLICATION_JSON).body(body);
		try {
			restTemplate.exchange(request, String.class);
			fail("Did not throw HttpClientErrorException");
		}
		catch (HttpClientErrorException e) {
			Map<String, String> map = mapper.readValue(e.getResponseBodyAsString(), Map.class);
			assertThat(map.get("message")).contains(
					"JSON parse error: Cannot deserialize value of type `java.time.LocalDateTime` from String \"invalid date\"");
			assertThat(map.get("message")).contains("Text 'invalid date' could not be parsed at index 0");
		}
	}

}
