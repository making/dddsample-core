package lol.maki.cargotracker.domain.model.voyage;

import lol.maki.cargotracker.infrastructure.sampledata.SampleLocations;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class CarrierMovementTest {

	@Test
	void testConstructor() {
		try {
			new CarrierMovement(null, null, Instant.now(), Instant.now());
			fail("Should not accept null constructor arguments");
		}
		catch (IllegalArgumentException expected) {
		}

		try {
			new CarrierMovement(SampleLocations.STOCKHOLM, null, Instant.now(), Instant.now());
			fail("Should not accept null constructor arguments");
		}
		catch (IllegalArgumentException expected) {
		}

		// Legal
		new CarrierMovement(SampleLocations.STOCKHOLM, SampleLocations.HAMBURG, Instant.now(), Instant.now());
	}

	@Test
	void testSameValueAsEqualsHashCode() {
		long referenceTime = System.currentTimeMillis();

		// One could, in theory, use the same Date(referenceTime) for all of these
		// movements
		// However, in practice, carrier movements will be initialized by different
		// processes
		// so we might have different Date that reference the same time, and we want to be
		// certain that sameValueAs does the right thing in that case.
		CarrierMovement cm1 = new CarrierMovement(SampleLocations.STOCKHOLM, SampleLocations.HAMBURG,
				Instant.ofEpochMilli(referenceTime), Instant.ofEpochMilli(referenceTime));
		CarrierMovement cm2 = new CarrierMovement(SampleLocations.STOCKHOLM, SampleLocations.HAMBURG,
				Instant.ofEpochMilli(referenceTime), Instant.ofEpochMilli(referenceTime));
		CarrierMovement cm3 = new CarrierMovement(SampleLocations.HAMBURG, SampleLocations.STOCKHOLM,
				Instant.ofEpochMilli(referenceTime), Instant.ofEpochMilli(referenceTime));
		CarrierMovement cm4 = new CarrierMovement(SampleLocations.HAMBURG, SampleLocations.STOCKHOLM,
				Instant.ofEpochMilli(referenceTime), Instant.ofEpochMilli(referenceTime));

		assertThat(cm1.sameValueAs(cm2)).isTrue();
		assertThat(cm2.sameValueAs(cm3)).isFalse();
		assertThat(cm3.sameValueAs(cm4)).isTrue();

		assertThat(cm1.equals(cm2)).isTrue();
		assertThat(cm2.equals(cm3)).isFalse();
		assertThat(cm3.equals(cm4)).isTrue();

		assertThat(cm1.hashCode() == cm2.hashCode()).isTrue();
		assertThat(cm2.hashCode() == cm3.hashCode()).isFalse();
		assertThat(cm3.hashCode() == cm4.hashCode()).isTrue();
	}

}
