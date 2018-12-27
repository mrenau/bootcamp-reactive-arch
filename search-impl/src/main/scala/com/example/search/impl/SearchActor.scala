package com.example.search.impl

import java.io.{File, FileInputStream, FileOutputStream}
import java.time.LocalDate
import java.util.UUID

import akka.Done
import akka.actor.{Actor, Status}
import com.example.common.ReservationAdded
import com.example.search.api.ListingSearchResult
import com.lightbend.lagom.scaladsl.api.transport.NotFound
import play.api.libs.json.{Format, Json}

private[impl] class SearchActor extends Actor {
  import SearchActor._

  val repo = new SearchRepository

  override def receive = {

    case reservation: ReservationAdded =>
      sender() ! repo.add(reservation)

    case Search(checkin, checkout) =>
      sender() ! repo.search(checkin, checkout)

    case ListingName(listingId) =>
      repo.name(listingId) match {
        case Some(name) => sender() ! name
        case None => sender() ! Status.Failure(NotFound(s"Listing $listingId not found"))
      }
  }
}

private[impl] object SearchActor {
  case class Search(checkin: LocalDate, checkout: LocalDate)
  case class ListingName(listingId: UUID)
}


/**
  * Not at all an efficient index, but this is a demo and this code isn't the subject of the demo
  */
private class SearchRepository {
  private val reservationFile = new File("./target/search-index.json")

  private var reservations: Map[UUID, ListingIndex] = if (reservationFile.exists()) {
    val is = new FileInputStream(reservationFile)
    try {
      val raw = Json.parse(is).as[Map[String, ListingIndex]]
      raw.map {
        case (id, index) => UUID.fromString(id) -> index
      }
    } finally {
      is.close()
    }
  } else {
    Seq(
      ListingSearchResult(UUID.randomUUID(), "Beach house with wonderful views", "beachhouse.jpeg", 280),
      ListingSearchResult(UUID.randomUUID(), "Villa by the water", "villa.jpeg", 350),
      ListingSearchResult(UUID.randomUUID(), "Budget hotel convenient to town centre", "hotel.jpeg", 120),
      ListingSearchResult(UUID.randomUUID(), "Quaint country B&B", "bnb.jpeg", 180)
    ).map { listing =>
      listing.listingId -> ListingIndex(listing, Set.empty)
    }.toMap
  }

  if (!reservationFile.exists()) {
    writeOut()
  }

  private def writeOut(): Unit = {
    val json = Json.stringify(Json.toJson(reservations.map {
      case (id, index) => id.toString -> index
    }))
    val os = new FileOutputStream(reservationFile)
    try {
      os.write(json.getBytes("utf-8"))
      os.flush()
    } finally {
      os.close()
    }
  }

  def add(reservation: ReservationAdded): Done = {
    reservations.get(reservation.listingId) match {
      case Some(ListingIndex(listing, res)) =>
        if (res.forall(_.reservationId != reservation.reservationId)) {
          reservations += (listing.listingId -> ListingIndex(listing, res + reservation))
          writeOut()
        }
        Done
      case None =>
        // Ignore
        Done
    }
  }

  def search(checkin: LocalDate, checkout: LocalDate): List[ListingSearchResult] = {
    reservations.values.collect {
      case ListingIndex(listing, res) if res.forall(reservationDoesNotConflict(checkin, checkout)) => listing
    }.toList
  }

  def name(listingId: UUID): Option[String] = {
    reservations.get(listingId).map(_.listing.listingName)
  }

  private def reservationDoesNotConflict(checkin: LocalDate, checkout: LocalDate)(reservationAdded: ReservationAdded): Boolean = {
    val rCheckin = reservationAdded.reservation.checkin
    val rCheckout = reservationAdded.reservation.checkout

    if (checkout.isBefore(rCheckin) || checkout == rCheckin) {
      true
    } else if (checkin.isAfter(rCheckout) || checkin == rCheckout) {
      true
    } else {
      false
    }
  }
}

private case class ListingIndex(listing: ListingSearchResult, reservations: Set[ReservationAdded])
private object ListingIndex {
  implicit val format: Format[ListingIndex] = Json.format
}