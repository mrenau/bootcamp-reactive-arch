package com.example.reservation.impl

import java.time.LocalDate
import java.time.chrono.ChronoLocalDate
import java.util.UUID

import com.example.common.Reservation
import com.lightbend.lagom.scaladsl.api.transport.BadRequest
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, PersistentEntity}
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import play.api.libs.json._

import scala.collection.immutable

/**
  * Trait for all reservation events.
  */
sealed trait ReservationEvent extends AggregateEvent[ReservationEvent] {

  /**
    * The tag for each event.
    *
    * Tags allow creating a read side stream of all events with the given tag.
    */
  def aggregateTag = ReservationEvent.Tag
}

object ReservationEvent {

  /**
    * Each event has the same tag.
    */
  val Tag = AggregateEventTag[ReservationEvent]
}

/**
  * The reservation added event.
  *
  * @param listingId The listing the reservation was added to.
  * @param reservationId The id of the reservation.
  * @param reservation The reservation dates.
  */
case class ReservationAdded(
  listingId: UUID,
  reservationId: UUID,
  reservation: Reservation
) extends ReservationEvent

object ReservationAdded {

  /**
    * JSON format for the reservation added.
    *
    * This will be used to serialize and deserialize the event when it is
    * persisted to the event log.
    */
  implicit val format: Format[ReservationAdded] = Json.format
}

/**
  * Trait for all reservation commands.
  */
sealed trait ReservationCommand[R] extends ReplyType[R]

/**
  * Command to add a reservation.
  *
  * @param reservation The reservation to add.
  */
case class AddReservation(
  reservation: Reservation
) extends ReservationCommand[ReservationAdded]

object AddReservation {

  /**
    * JSON format for the add reservation command.
    *
    * Commands may be sent over the wire to entities because entites are
    * sharded across your Lagom cluster. This will be used to define
    * how the command gets serialized and deserialized.
    */
  implicit val format: Format[AddReservation] = Json.format
}

/**
  * Command to get the current reservations.
  */
case object GetCurrentReservations extends ReservationCommand[Seq[Reservation]] {

  /**
    * JSON format for the get current reservations command.
    */
  implicit val format: Format[GetCurrentReservations.type] =
    Format(Reads(_ => JsSuccess(GetCurrentReservations)), Writes(_ => JsString("get")))
}

/**
  * The state of the reservation listing.
  *
  * This is used to accumlate all the reservation events into a current state.
  *
  * @param reservations The reservations for this listing.
  */
case class ReservationState(reservations: Seq[Reservation])

object ReservationState {

  /**
    * JSON format for the reservation state.
    *
    * When reservation entities are snapshotted, the state is stored to the
    * snapshot store. This will be used to define how it gets serialized
    * and deserialized in that store.
    */
  implicit val format: Format[ReservationState] = Json.format

  /**
    * The empty state, no reservations.
    */
  val empty = ReservationState(Nil)
}

/**
  * Serializer registry for all reservation objects.
  *
  * This tells Akka remoting where to find the serializers for the various
  * objects we're using.
  */
object ReservationSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: immutable.Seq[JsonSerializer[_]] = immutable.Seq(
    JsonSerializer[ReservationAdded],
    JsonSerializer[AddReservation],
    JsonSerializer[GetCurrentReservations.type],
    JsonSerializer[ReservationState]
  )
}

/**
  * Reservation persistent entity.
  */
class ReservationEntity extends PersistentEntity {

  // The types of the command, event and state
  override type Command = ReservationCommand[_]
  override type Event = ReservationEvent
  override type State = ReservationState

  /**
    * Convenient shortcut that parsies the listing UUID from the
    * entity id.
    */
  private lazy val listingId = UUID.fromString(entityId)

  /**
    * When a command arrives for an entity that has no events, this state
    * will be used.
    */
  override def initialState: ReservationState = ReservationState.empty

  /**
    * The behaviour for this entity.
    */
  override def behavior: Behavior = {

    // The behaviour can be defined like a state machine. In this case,
    // we only really have one state, so we just match on that.
    case ReservationState(reservations) =>

      // Handle the add reservation command.
      Actions().onCommand[AddReservation, ReservationAdded] {
        case (AddReservation(reservation), ctx, _) =>

          // Validate start/end dates
          val now = LocalDate.now()
          if (reservation.checkout.isBefore(reservation.checkin) || reservation.checkout == reservation.checkin) {
            ctx.commandFailed(BadRequest("Checkout date must be after checkin date"))
            ctx.done
          } else if (reservation.checkin.isBefore(now)) {
            ctx.commandFailed(BadRequest("Cannot make a reservation for the past"))
            ctx.done

          // Check that it doesn't overlap with any existing reservations
          } else if (reservations.exists(_.conflictsWith(reservation))) {
            ctx.invalidCommand("Listing is already booked for those dates")
            ctx.done
          } else {

            // If all the validation passes, persist a reservation added event
            ctx.thenPersist(ReservationAdded(listingId, UUID.randomUUID(), reservation))(ctx.reply)
          }

      // Handle the get current reservations command
      }.onReadOnlyCommand[GetCurrentReservations.type, Seq[Reservation]] {
        case (GetCurrentReservations, ctx, _) =>
          val now = LocalDate.now()
          // Reply with all the reservations that aren't before now
          ctx.reply(reservations.dropWhile(_.checkout.isBefore(now)))

      // Handle the reservation added event
      }.onEvent {
        case (ReservationAdded(_, _, reservation), state) =>
          // Create a new state with the reservation added, sorted by
          // checkin date.
          ReservationState((reservations :+ reservation)
            .sortBy(_.checkin.asInstanceOf[ChronoLocalDate]))
      }

  }
}

