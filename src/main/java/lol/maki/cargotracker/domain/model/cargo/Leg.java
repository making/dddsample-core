package lol.maki.cargotracker.domain.model.cargo;

import jakarta.persistence.*;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import lol.maki.cargotracker.domain.model.location.Location;
import lol.maki.cargotracker.domain.model.voyage.Voyage;
import lol.maki.cargotracker.domain.shared.ValueObject;

import java.time.Instant;

/**
 * An itinerary consists of one or more legs.
 */
@Entity(name = "Leg")
@Table(name = "Leg")
public class Leg implements ValueObject<Leg> {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	public long id;

	@ManyToOne
	@JoinColumn(name = "voyage_id")
	public Voyage voyage;

	@ManyToOne
	@JoinColumn(name = "load_location_id")
	public Location loadLocation;

	@Column(name = "load_time")
	public Instant loadTime;

	@ManyToOne
	@JoinColumn(name = "unload_location_id")
	public Location unloadLocation;

	@Column(name = "unload_time")
	public Instant unloadTime;

	public Leg(Voyage voyage, Location loadLocation, Location unloadLocation, Instant loadTime, Instant unloadTime) {
		Validate.noNullElements(new Object[] { voyage, loadLocation, unloadLocation, loadTime, unloadTime });

		this.voyage = voyage;
		this.loadLocation = loadLocation;
		this.unloadLocation = unloadLocation;
		this.loadTime = loadTime;
		this.unloadTime = unloadTime;
	}

	public Voyage voyage() {
		return voyage;
	}

	public Location loadLocation() {
		return loadLocation;
	}

	public Location unloadLocation() {
		return unloadLocation;
	}

	public Instant loadTime() {
		return loadTime;
	}

	public Instant unloadTime() {
		return unloadTime;
	}

	@Override
	public boolean sameValueAs(final Leg other) {
		return other != null && new EqualsBuilder().append(this.voyage, other.voyage)
			.append(this.loadLocation, other.loadLocation)
			.append(this.unloadLocation, other.unloadLocation)
			.append(this.loadTime, other.loadTime)
			.append(this.unloadTime, other.unloadTime)
			.isEquals();
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		Leg leg = (Leg) o;

		return sameValueAs(leg);
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder().append(voyage)
			.append(loadLocation)
			.append(unloadLocation)
			.append(loadTime)
			.append(unloadTime)
			.toHashCode();
	}

	Leg() {
		// Needed by Hibernate
	}

}
