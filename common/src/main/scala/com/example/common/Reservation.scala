package com.example.common

import java.time.LocalDate

import play.api.libs.json.{Format, Json}

/**
  * A reservation.
  *
  * A reservation has a checkin and a checkout date.
  */
case class Reservation(checkin: LocalDate, checkout: LocalDate) {

  /**
    * Check whether this reservation conflicts with another reservation.
    */
  def conflictsWith(other: Reservation): Boolean = {
    if (checkout.isBefore(other.checkin) || checkout == other.checkin) {
      false
    } else if (checkin.isAfter(other.checkout) || checkin == other.checkout) {
      false
    } else {
      true
    }

  }
}

object Reservation {
  implicit val format: Format[Reservation] = Json.format
}





