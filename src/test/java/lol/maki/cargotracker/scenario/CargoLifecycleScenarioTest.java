package lol.maki.cargotracker.scenario;

import lol.maki.cargotracker.domain.model.cargo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import lol.maki.cargotracker.application.ApplicationEvents;
import lol.maki.cargotracker.application.BookingService;
import lol.maki.cargotracker.application.CargoInspectionService;
import lol.maki.cargotracker.application.HandlingEventService;
import lol.maki.cargotracker.domain.model.handling.CannotCreateHandlingEventException;
import lol.maki.cargotracker.domain.model.handling.HandlingEventFactory;
import lol.maki.cargotracker.domain.model.handling.HandlingEventRepository;
import lol.maki.cargotracker.domain.model.location.Location;
import lol.maki.cargotracker.domain.model.location.LocationRepository;
import lol.maki.cargotracker.domain.model.location.UnLocode;
import lol.maki.cargotracker.domain.model.voyage.VoyageNumber;
import lol.maki.cargotracker.domain.model.voyage.VoyageRepository;
import lol.maki.cargotracker.domain.service.RoutingService;
import lol.maki.cargotracker.infrastructure.messaging.stub.SynchronousApplicationEventsStub;
import lol.maki.cargotracker.infrastructure.persistence.inmemory.CargoRepositoryInMem;
import lol.maki.cargotracker.infrastructure.persistence.inmemory.HandlingEventRepositoryInMem;
import lol.maki.cargotracker.infrastructure.persistence.inmemory.LocationRepositoryInMem;
import lol.maki.cargotracker.infrastructure.persistence.inmemory.VoyageRepositoryInMem;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static lol.maki.cargotracker.application.util.DateUtils.toDate;
import static lol.maki.cargotracker.domain.model.handling.HandlingEvent.Type.*;
import static lol.maki.cargotracker.domain.model.voyage.Voyage.NONE;
import static lol.maki.cargotracker.infrastructure.sampledata.SampleLocations.*;
import static lol.maki.cargotracker.infrastructure.sampledata.SampleVoyages.*;

class CargoLifecycleScenarioTest {

	/**
	 * Repository implementations are part of the infrastructure layer, which in this test
	 * is stubbed out by in-memory replacements.
	 */
	HandlingEventRepository handlingEventRepository;

	CargoRepository cargoRepository;

	LocationRepository locationRepository;

	VoyageRepository voyageRepository;

	/**
	 * This interface is part of the application layer, and defines a number of events
	 * that occur during aplication execution. It is used for message-driving and is
	 * implemented using JMS.
	 * <p>
	 * In this test it is stubbed with synchronous calls.
	 */
	ApplicationEvents applicationEvents;

	/**
	 * These three components all belong to the application layer, and map against use
	 * cases of the application. The "real" implementations are used in this lifecycle
	 * test, but wired with stubbed infrastructure.
	 */
	BookingService bookingService;

	HandlingEventService handlingEventService;

	CargoInspectionService cargoInspectionService;

	/**
	 * This factory is part of the handling aggregate and belongs to the domain layer.
	 * Similar to the application layer components, the "real" implementation is used here
	 * too, wired with stubbed infrastructure.
	 */
	HandlingEventFactory handlingEventFactory;

	/**
	 * This is a domain service interface, whose implementation is part of the
	 * infrastructure layer (remote call to external system).
	 * <p>
	 * It is stubbed in this test.
	 */
	RoutingService routingService;

	@Test
	void testCargoFromHongkongToStockholm() throws Exception {
		/*
		 * Test setup: A cargo should be shipped from Hongkong to Stockholm, and it should
		 * arrive in no more than two weeks.
		 */
		Location origin = HONGKONG;
		Location destination = STOCKHOLM;
		Instant arrivalDeadline = toDate("2009-03-18");

		/*
		 * Use case 1: booking
		 *
		 * A new cargo is booked, and the unique tracking id is assigned to the cargo.
		 */
		TrackingId trackingId = bookingService.bookNewCargo(origin.unLocode(), destination.unLocode(), arrivalDeadline);

		/*
		 * The tracking id can be used to lookup the cargo in the repository.
		 *
		 * Important: The cargo, and thus the domain model, is responsible for determining
		 * the status of the cargo, whether it is on the right track or not and so on.
		 * This is core domain logic.
		 *
		 * Tracking the cargo basically amounts to presenting information extracted from
		 * the cargo aggregate in a suitable way.
		 */
		Cargo cargo = cargoRepository.find(trackingId);
		assertThat(cargo).isNotNull();
		assertThat(cargo.delivery().transportStatus()).isEqualTo(TransportStatus.NOT_RECEIVED);
		assertThat(cargo.delivery().routingStatus()).isEqualTo(RoutingStatus.NOT_ROUTED);
		assertThat(cargo.delivery().isMisdirected()).isFalse();
		assertThat(cargo.delivery().estimatedTimeOfArrival()).isNull();
		assertThat(cargo.delivery().nextExpectedActivity()).isNull();

		/*
		 * Use case 2: routing
		 *
		 * A number of possible routes for this cargo is requested and may be presented to
		 * the customer in some way for him/her to choose from. Selection could be
		 * affected by things like price and time of delivery, but this test simply uses
		 * an arbitrary selection to mimic that process.
		 *
		 * The cargo is then assigned to the selected route, described by an itinerary.
		 */
		List<Itinerary> itineraries = bookingService.requestPossibleRoutesForCargo(trackingId);
		Itinerary itinerary = selectPreferedItinerary(itineraries);
		cargo.assignToRoute(itinerary);

		assertThat(cargo.delivery().transportStatus()).isEqualTo(TransportStatus.NOT_RECEIVED);
		assertThat(cargo.delivery().routingStatus()).isEqualTo(RoutingStatus.ROUTED);
		assertThat(cargo.delivery().estimatedTimeOfArrival()).isNotNull();
		assertThat(cargo.delivery().nextExpectedActivity()).isEqualTo(new HandlingActivity(RECEIVE, HONGKONG));

		/*
		 * Use case 3: handling
		 *
		 * A handling event registration attempt will be formed from parsing the data
		 * coming in as a handling report either via the web service interface or as an
		 * uploaded CSV file.
		 *
		 * The handling event factory tries to create a HandlingEvent from the attempt,
		 * and if the factory decides that this is a plausible handling event, it is
		 * stored. If the attempt is invalid, for example if no cargo exists for the
		 * specfied tracking id, the attempt is rejected.
		 *
		 * Handling begins: cargo is received in Hongkong.
		 */
		handlingEventService.registerHandlingEvent(toDate("2009-03-01"), trackingId, null, HONGKONG.unLocode(),
				RECEIVE);

		assertThat(cargo.delivery().transportStatus()).isEqualTo(TransportStatus.IN_PORT);
		assertThat(cargo.delivery().lastKnownLocation()).isEqualTo(HONGKONG);

		// Next event: Load onto voyage CM003 in Hongkong
		handlingEventService.registerHandlingEvent(toDate("2009-03-03"), trackingId, v100.voyageNumber(),
				HONGKONG.unLocode(), LOAD);

		// Check current state - should be ok
		assertThat(cargo.delivery().currentVoyage()).isEqualTo(v100);
		assertThat(cargo.delivery().lastKnownLocation()).isEqualTo(HONGKONG);
		assertThat(cargo.delivery().transportStatus()).isEqualTo(TransportStatus.ONBOARD_CARRIER);
		assertThat(cargo.delivery().isMisdirected()).isFalse();
		assertThat(cargo.delivery().nextExpectedActivity()).isEqualTo(new HandlingActivity(UNLOAD, NEWYORK, v100));

		/*
		 * Here's an attempt to register a handling event that's not valid because there
		 * is no voyage with the specified voyage number, and there's no location with the
		 * specified UN Locode either.
		 *
		 * This attempt will be rejected and will not affect the cargo delivery in any
		 * way.
		 */
		final VoyageNumber noSuchVoyageNumber = new VoyageNumber("XX000");
		final UnLocode noSuchUnLocode = new UnLocode("ZZZZZ");
		try {
			handlingEventService.registerHandlingEvent(toDate("2009-03-05"), trackingId, noSuchVoyageNumber,
					noSuchUnLocode, LOAD);
			fail("Should not be able to register a handling event with invalid location and voyage");
		}
		catch (CannotCreateHandlingEventException expected) {
		}

		// Cargo is now (incorrectly) unloaded in Tokyo
		handlingEventService.registerHandlingEvent(toDate("2009-03-05"), trackingId, v100.voyageNumber(),
				TOKYO.unLocode(), UNLOAD);

		// Check current state - cargo is misdirected!
		assertThat(cargo.delivery().currentVoyage()).isEqualTo(NONE);
		assertThat(cargo.delivery().lastKnownLocation()).isEqualTo(TOKYO);
		assertThat(cargo.delivery().transportStatus()).isEqualTo(TransportStatus.IN_PORT);
		assertThat(cargo.delivery().isMisdirected()).isTrue();
		assertThat(cargo.delivery().nextExpectedActivity()).isNull();

		// -- Cargo needs to be rerouted --

		// TODO cleaner reroute from "earliest location from where the new route
		// originates"

		// Specify a new route, this time from Tokyo (where it was incorrectly unloaded)
		// to Stockholm
		RouteSpecification fromTokyo = new RouteSpecification(TOKYO, STOCKHOLM, arrivalDeadline);
		cargo.specifyNewRoute(fromTokyo);

		// The old itinerary does not satisfy the new specification
		assertThat(cargo.delivery().routingStatus()).isEqualTo(RoutingStatus.MISROUTED);
		assertThat(cargo.delivery().nextExpectedActivity()).isNull();

		// Repeat procedure of selecting one out of a number of possible routes satisfying
		// the route spec
		List<Itinerary> newItineraries = bookingService.requestPossibleRoutesForCargo(cargo.trackingId());
		Itinerary newItinerary = selectPreferedItinerary(newItineraries);
		cargo.assignToRoute(newItinerary);

		// New itinerary should satisfy new route
		assertThat(cargo.delivery().routingStatus()).isEqualTo(RoutingStatus.ROUTED);

		// TODO we can't handle the face that after a reroute, the cargo isn't misdirected
		// anymore
		// assertThat(cargo.isMisdirected()).isFalse();
		// assertThat(, cargo.nextExpectedActivity()).isEqualTo(new HandlingActivity(LOAD,
		// TOKYO));

		// -- Cargo has been rerouted, shipping continues --

		// Load in Tokyo
		handlingEventService.registerHandlingEvent(toDate("2009-03-08"), trackingId, v300.voyageNumber(),
				TOKYO.unLocode(), LOAD);

		// Check current state - should be ok
		assertThat(cargo.delivery().currentVoyage()).isEqualTo(v300);
		assertThat(cargo.delivery().lastKnownLocation()).isEqualTo(TOKYO);
		assertThat(cargo.delivery().transportStatus()).isEqualTo(TransportStatus.ONBOARD_CARRIER);
		assertThat(cargo.delivery().isMisdirected()).isFalse();
		assertThat(cargo.delivery().nextExpectedActivity()).isEqualTo(new HandlingActivity(UNLOAD, HAMBURG, v300));

		// Unload in Hamburg
		handlingEventService.registerHandlingEvent(toDate("2009-03-12"), trackingId, v300.voyageNumber(),
				HAMBURG.unLocode(), UNLOAD);

		// Check current state - should be ok
		assertThat(cargo.delivery().currentVoyage()).isEqualTo(NONE);
		assertThat(cargo.delivery().lastKnownLocation()).isEqualTo(HAMBURG);
		assertThat(cargo.delivery().transportStatus()).isEqualTo(TransportStatus.IN_PORT);
		assertThat(cargo.delivery().isMisdirected()).isFalse();
		assertThat(cargo.delivery().nextExpectedActivity()).isEqualTo(new HandlingActivity(LOAD, HAMBURG, v400));

		// Load in Hamburg
		handlingEventService.registerHandlingEvent(toDate("2009-03-14"), trackingId, v400.voyageNumber(),
				HAMBURG.unLocode(), LOAD);

		// Check current state - should be ok
		assertThat(cargo.delivery().currentVoyage()).isEqualTo(v400);
		assertThat(cargo.delivery().lastKnownLocation()).isEqualTo(HAMBURG);
		assertThat(cargo.delivery().transportStatus()).isEqualTo(TransportStatus.ONBOARD_CARRIER);
		assertThat(cargo.delivery().isMisdirected()).isFalse();
		assertThat(cargo.delivery().nextExpectedActivity()).isEqualTo(new HandlingActivity(UNLOAD, STOCKHOLM, v400));

		// Unload in Stockholm
		handlingEventService.registerHandlingEvent(toDate("2009-03-15"), trackingId, v400.voyageNumber(),
				STOCKHOLM.unLocode(), UNLOAD);

		// Check current state - should be ok
		assertThat(cargo.delivery().currentVoyage()).isEqualTo(NONE);
		assertThat(cargo.delivery().lastKnownLocation()).isEqualTo(STOCKHOLM);
		assertThat(cargo.delivery().transportStatus()).isEqualTo(TransportStatus.IN_PORT);
		assertThat(cargo.delivery().isMisdirected()).isFalse();
		assertThat(cargo.delivery().nextExpectedActivity()).isEqualTo(new HandlingActivity(CLAIM, STOCKHOLM));

		// Finally, cargo is claimed in Stockholm. This ends the cargo lifecycle from our
		// perspective.
		handlingEventService.registerHandlingEvent(toDate("2009-03-16"), trackingId, null, STOCKHOLM.unLocode(), CLAIM);

		// Check current state - should be ok
		assertThat(cargo.delivery().currentVoyage()).isEqualTo(NONE);
		assertThat(cargo.delivery().lastKnownLocation()).isEqualTo(STOCKHOLM);
		assertThat(cargo.delivery().transportStatus()).isEqualTo(TransportStatus.CLAIMED);
		assertThat(cargo.delivery().isMisdirected()).isFalse();
		assertThat(cargo.delivery().nextExpectedActivity()).isNull();
	}

	/*
	 * Utility stubs below.
	 */

	private Itinerary selectPreferedItinerary(List<Itinerary> itineraries) {
		return itineraries.get(0);
	}

	@BeforeEach
	void setUp() {
		routingService = new RoutingService() {
			public List<Itinerary> fetchRoutesForSpecification(RouteSpecification routeSpecification) {
				if (routeSpecification.origin().equals(HONGKONG)) {
					// Hongkong - NYC - Chicago - Stockholm, initial routing
					return List.of(new Itinerary(
							List.of(new Leg(v100, HONGKONG, NEWYORK, toDate("2009-03-03"), toDate("2009-03-09")),
									new Leg(v200, NEWYORK, CHICAGO, toDate("2009-03-10"), toDate("2009-03-14")),
									new Leg(v200, CHICAGO, STOCKHOLM, toDate("2009-03-07"), toDate("2009-03-11")))));
				}
				else {
					// Tokyo - Hamburg - Stockholm, rerouting misdirected cargo from Tokyo
					return List.of(new Itinerary(
							List.of(new Leg(v300, TOKYO, HAMBURG, toDate("2009-03-08"), toDate("2009-03-12")),
									new Leg(v400, HAMBURG, STOCKHOLM, toDate("2009-03-14"), toDate("2009-03-15")))));
				}
			}
		};

		applicationEvents = new SynchronousApplicationEventsStub();

		// In-memory implementations of the repositories
		handlingEventRepository = new HandlingEventRepositoryInMem();
		cargoRepository = new CargoRepositoryInMem();
		locationRepository = new LocationRepositoryInMem();
		voyageRepository = new VoyageRepositoryInMem();

		// Actual factories and application services, wired with stubbed or in-memory
		// infrastructure
		handlingEventFactory = new HandlingEventFactory(cargoRepository, voyageRepository, locationRepository);

		cargoInspectionService = new CargoInspectionService(applicationEvents, cargoRepository,
				handlingEventRepository);
		handlingEventService = new HandlingEventService(handlingEventRepository, applicationEvents,
				handlingEventFactory);
		CargoFactory cargoFactory = new CargoFactory(locationRepository, cargoRepository);
		bookingService = new BookingService(cargoRepository, locationRepository, routingService, cargoFactory);

		// Circular dependency when doing synchrounous calls
		((SynchronousApplicationEventsStub) applicationEvents).setCargoInspectionService(cargoInspectionService);
	}

}
