package com.example.common

import java.util.UUID

import play.api.libs.json.{Format, Json}

/**
  * Event signifying that a reservation has been added.
  *
  * @param listingId The id of the listing that the reservation was added to.
  * @param reservationId The id of the reservation.
  * @param reservation The reservation that was added.
  */
case class ReservationAdded(listingId: UUID, reservationId: UUID, reservation: Reservation)

object ReservationAdded {
  implicit val format: Format[ReservationAdded] = Json.format
}