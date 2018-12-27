package com.example.reservation.impl

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

import akka.stream.scaladsl.Sink
import com.example.common.Reservation
import com.example.reservation.api.ReservationService
import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.{ServiceTest, TestTopicComponents}
import org.scalatest.{AsyncWordSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global

class ReservationServiceSpec extends AsyncWordSpec with Matchers {

  "The reservation service" should {
    "add and publish reservations" in ServiceTest.withServer(ServiceTest.defaultSetup.withCassandra(true)) { ctx =>
      new ReservationApplication(ctx) with LocalServiceLocator
        with TestTopicComponents
    } { server =>
      import server.materializer

      val client = server.serviceClient.implement[ReservationService]

      val listingId = UUID.randomUUID()

      val reservation = Reservation(LocalDate.now, LocalDate.now.plus(2, ChronoUnit.DAYS))

      // Create a reservation
      client.reserve(listingId).invoke(reservation).flatMap { added =>

        // Check that the response is correct
        added.listingId should ===(listingId)
        added.reservation should ===(reservation)

        // Subscribe to the topic to get the event
        client.events.subscribe.atMostOnceSource.runWith(Sink.head)
      }.flatMap { event =>

        // Check that the event is correct
        event.listingId should ===(listingId)
        event.reservation should ===(reservation)

        // Get the current reservations
        client.getCurrentReservations(listingId).invoke()
      }.map { reservations =>

        // Check that the reservations are correct
        reservations should contain only reservation
      }
    }
  }

}
