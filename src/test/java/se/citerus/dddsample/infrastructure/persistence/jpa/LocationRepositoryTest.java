package se.citerus.dddsample.infrastructure.persistence.jpa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import se.citerus.dddsample.domain.model.location.Location;
import se.citerus.dddsample.domain.model.location.LocationRepository;
import se.citerus.dddsample.domain.model.location.UnLocode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class LocationRepositoryTest {
    @Autowired
    private LocationRepository locationRepository;

    @Test
    void testFind() {
        final UnLocode melbourne = new UnLocode("AUMEL");
        Location location = locationRepository.find(melbourne);
        assertThat(location).isNotNull();
        assertThat(location.unLocode()).isEqualTo(melbourne);

        assertThat(locationRepository.find(new UnLocode("NOLOC"))).isNull();
    }

    @Test
    void testFindAll() {
        List<Location> allLocations = locationRepository.getAll();

        assertThat(allLocations).isNotNull().hasSize(13);
    }

}
