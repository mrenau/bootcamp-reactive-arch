package com.example.reservation.impl

import java.util.UUID

import com.example.common
import com.example.reservation.api.ReservationService
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.{EventStreamElement, PersistentEntityRef, PersistentEntityRegistry}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

/**
  * Implementation of the reservation service
  */
class ReservationServiceImpl(persistentEntityRegistry: PersistentEntityRegistry,
  currentReservationsRepository: CurrentReservationsRepository)(implicit ec: ExecutionContext)
  extends ReservationService {

  /**
    * Convenience function to get a persistent entity ref for the given listing.
    */
  private def reservationEntity(listingId: UUID): PersistentEntityRef[ReservationCommand[_]] = {
    persistentEntityRegistry.refFor[ReservationEntity](listingId.toString)
  }

  /**
    * Reserve the given listing.
    */
  override def reserve(listingId: UUID) = ServiceCall { reservation =>
    // Get the entity ref
    reservationEntity(listingId)
      // Ask it to add a reservation
      .ask(AddReservation(reservation))
      // Map the result to the common ReservationAdded model
      .map { added =>
        common.ReservationAdded(added.listingId, added.reservationId, added.reservation)
      }
  }

  /**
    * Get the current reservations for the given listing.
    */
  override def getCurrentReservations(listingId: UUID) = ServiceCall { _ =>
    // Get the current reservations from the enitity
    currentReservationsRepository.getCurrentReservations(listingId)
  }

  /**
    * The stream of reservation events.
    */
  override def events =
    TopicProducer.taggedStreamWithOffset(immutable.Seq(ReservationEvent.Tag)) { (tag, offset) =>
      persistentEntityRegistry.eventStream(tag, offset).map {
        case EventStreamElement(_, ReservationAdded(listingId, reservationId, reservation), offset) =>
          common.ReservationAdded(listingId, reservationId, reservation) -> offset
      }
    }
}
