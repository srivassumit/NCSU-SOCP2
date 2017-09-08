package controllers;

import java.util.List;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;

import actors.AirlineActor;
import actors.AirlineActorProtocol.Confirm;
import actors.AirlineActorProtocol.Hold;
import actors.BookingActor;
import actors.BookingActorProtocol.BookFlight;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import scala.compat.java8.FutureConverters;
import services.DatabaseService;

/**
 * @author srivassumit
 *
 */
@Singleton
public class BookingController extends Controller {

	final ActorSystem system;
	final DatabaseService databaseService;
	final ActorRef bookingActor;
	final ActorRef aaActor, baActor, caActor;

	@Inject
	public BookingController(ActorSystem system, DatabaseService databaseService) {
		this.system = system;
		this.databaseService = databaseService;
		aaActor = system.actorOf(AirlineActor.getProps("AA"));
		baActor = system.actorOf(AirlineActor.getProps("BA"));
		caActor = system.actorOf(AirlineActor.getProps("CA"));
		bookingActor = system.actorOf(BookingActor.getProps(aaActor, baActor, caActor));
		databaseService.initializeDatabase();
	}

	// /**
	// *
	// * Use static import for this: import static akka.pattern.Patterns.ask;
	// *
	// * @param name
	// * @return
	// */
	// public CompletionStage<Result> sayHello(String name) {
	// return FutureConverters.toJava(ask(airlineActor, new Airline(name),
	// 1000)).thenApply(response -> ok((String) response));
	// }

	public CompletionStage<Result> useActor(String message) {
		Object messageType = null;
		if ("hold".equalsIgnoreCase(message)) {
			messageType = new Hold();
		} else if ("confirm".equalsIgnoreCase(message)) {
			messageType = new Confirm();
		}
		return FutureConverters.toJava(Patterns.ask(aaActor, messageType, 1000))
				.thenApply(response -> ok((String) response));
	}

	/**
	 * Get a list of trips booked.
	 * 
	 * @return Result
	 */
	public Result getTrips() {
		List<String> tripIds = databaseService.fetchTrips();
		ObjectNode result = Json.newObject();
		result.put("status", "success");
		result.put("trips", new Gson().toJson(tripIds));
		return ok(result);
	}

	/**
	 * Get a list of segments of a trip. A segment is represented by its flight.
	 * 
	 * @param tripID
	 *            the Trip ID
	 * @return Result
	 */
	public Result getSegments(String tripID) {
		String segments = databaseService.fetchSegments(tripID);
		if (segments == null) {
			ObjectNode result = Json.newObject();
			result.put("status", "error");
			result.put("message", "No segments found for Trip ID: " + tripID);
			return ok(result);
		} else {
			String[] segmentArray = segments.split("-");
			ObjectNode result = Json.newObject();
			result.put("status", "success");
			result.put("segments", new Gson().toJson(segmentArray));
			return ok(result);
		}
	}

	/**
	 * Get a list of airline operators
	 * 
	 * @return
	 */
	public Result getOperators() {
		List<String> operators = databaseService.fetchOperators();
		ObjectNode result = Json.newObject();
		result.put("status", "success");
		result.put("operators", new Gson().toJson(operators));
		return ok(result);
	}

	/**
	 * Get a list of flights operated by an airline operator
	 * 
	 * @param operator
	 * @return
	 */
	public Result getFlights(String operator) {
		List<String> operatorFlights = databaseService.fetchOperatorFlights(operator);
		if (operatorFlights.size() == 0) {
			ObjectNode result = Json.newObject();
			result.put("status", "error");
			result.put("message", "No Flights operated by operator: " + operator);
			return ok(result);
		} else {
			ObjectNode result = Json.newObject();
			result.put("status", "success");
			result.put("flights", new Gson().toJson(operatorFlights));
			return ok(result);
		}
	}

	/**
	 * Get the number of available seats on a flight.
	 * 
	 * @param operator
	 *            the airline operator
	 * @param flight
	 *            the flight
	 * @return Result
	 */
	public Result getSeats(String operator, String flight) {
		int availableSeats = databaseService.fetchAvailableSeats(operator, flight);
		if (availableSeats == 0) {
			ObjectNode result = Json.newObject();
			result.put("status", "error");
			result.put("message", "No seats available on Flight: " + flight + ", operated by operator: " + operator);
			return ok(result);
		} else {
			ObjectNode result = Json.newObject();
			result.put("status", "success");
			result.put("seats", availableSeats);
			return ok(result);
		}
	}

	/**
	 * Book a trip. Currently, the $from and $to should always be X and Y. If not,
	 * return an error
	 * 
	 * @param from
	 * @param to
	 * @return
	 */
	public CompletionStage<Result> bookTrip(String from, String to) {
		if (!"X".equals(from) || !"Y".equals(to)) {
			return badRequest("From and To should be 'X' and 'Y' respectively");
		} else {
			// call booking actor
			// return bookingActor.tell(new BookFlight(from, to), ActorRef.noSender());
			FutureConverters.toJava(ask(bookingActor, new BookFlight(from, to), 1000))
					.thenApply(response -> ok((String) response));
		}
		// return ok("from: " + from + ", to: " + to);
	}

	/**
	 * After this request is posted, corresponding airline actor will reply fail to
	 * subsequent Confirm requests without actual processing
	 * 
	 * @param airline
	 *            the airline
	 * @return
	 */
	public Result confirmFail(String airline) {
		return ok("airline: " + airline);
	}

	/**
	 * After this request is posted, corresponding airline actor will not reply to
	 * subsequent Confirm requests without actual processing
	 * 
	 * @param airline
	 *            the airline
	 * @return
	 */
	public Result confirmNoResponse(String airline) {
		return ok("airline: " + airline);
	}

	/**
	 * After this request is posted, the actor will reset to normal
	 * 
	 * @param airline
	 *            the airline
	 * @return
	 */
	public Result reset(String airline) {
		return ok("airline: " + airline);
	}

}