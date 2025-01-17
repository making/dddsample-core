package lol.maki.cargotracker.infrastructure.routing;

import com.pathfinder.api.GraphTraversalService;
import com.pathfinder.api.TransitEdge;
import com.pathfinder.api.TransitPath;
import lol.maki.cargotracker.domain.model.cargo.Itinerary;
import lol.maki.cargotracker.domain.model.cargo.Leg;
import lol.maki.cargotracker.domain.model.cargo.RouteSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import lol.maki.cargotracker.domain.model.location.Location;
import lol.maki.cargotracker.domain.model.location.LocationRepository;
import lol.maki.cargotracker.domain.model.location.UnLocode;
import lol.maki.cargotracker.domain.model.voyage.VoyageNumber;
import lol.maki.cargotracker.domain.model.voyage.VoyageRepository;
import lol.maki.cargotracker.domain.service.RoutingService;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Our end of the routing service. This is basically a data model translation layer
 * between our domain model and the API put forward by the routing team, which operates in
 * a different context from us.
 */
@Service
public class ExternalRoutingService implements RoutingService {

	private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final GraphTraversalService graphTraversalService;

	private final LocationRepository locationRepository;

	private final VoyageRepository voyageRepository;

	public ExternalRoutingService(GraphTraversalService graphTraversalService, LocationRepository locationRepository,
			VoyageRepository voyageRepository) {
		this.graphTraversalService = graphTraversalService;
		this.locationRepository = locationRepository;
		this.voyageRepository = voyageRepository;
	}

	public List<Itinerary> fetchRoutesForSpecification(RouteSpecification routeSpecification) {
		/*
		 * The RouteSpecification is picked apart and adapted to the external API.
		 */
		final Location origin = routeSpecification.origin();
		final Location destination = routeSpecification.destination();

		final Properties limitations = new Properties();
		limitations.setProperty("DEADLINE", routeSpecification.arrivalDeadline().toString());

		final List<TransitPath> transitPaths;
		transitPaths = graphTraversalService.findShortestPath(origin.unLocode().idString(),
				destination.unLocode().idString(), limitations);

		/*
		 * The returned result is then translated back into our domain model.
		 */
		final List<Itinerary> itineraries = transitPaths.stream()
			.map(this::toItinerary)
			.filter(itinerary -> isSatisfyingRouteSpec(itinerary, routeSpecification))
			.collect(Collectors.toList());

		return itineraries;
	}

	private static boolean isSatisfyingRouteSpec(Itinerary itinerary, RouteSpecification routeSpecification) {
		if (routeSpecification.isSatisfiedBy(itinerary)) {
			return true;
		}
		else {
			logger.warn("Received itinerary that did not satisfy the route specification");
			return false;
		}
	}

	private Itinerary toItinerary(TransitPath transitPath) {
		List<Leg> legs = new ArrayList<>(transitPath.transitEdges().size());
		for (TransitEdge edge : transitPath.transitEdges()) {
			legs.add(toLeg(edge));
		}
		return new Itinerary(legs);
	}

	private Leg toLeg(TransitEdge edge) {
		return new Leg(voyageRepository.find(new VoyageNumber(edge.edge())),
				locationRepository.find(new UnLocode(edge.fromNode())),
				locationRepository.find(new UnLocode(edge.toNode())), edge.fromDate(), edge.toDate());
	}

}
