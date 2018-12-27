package com.example.reservation.api

import java.util.UUID

import akka.NotUsed
import com.example.common.{Reservation, ReservationAdded}
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.broker.kafka.{KafkaProperties, PartitionKeyStrategy}
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}

/**
  * The reservation service.
  */
trait ReservationService extends Service {

  /**
    * Reserve the given listing.
    */
  def reserve(listingId: UUID): ServiceCall[Reservation, ReservationAdded]

  /**
    * Get the current reservations for the given listing.
    */
  def getCurrentReservations(listingId: UUID): ServiceCall[NotUsed, Seq[Reservation]]

  /**
    * The stream of reservation events.
    */
  def events: Topic[ReservationAdded]

  /**
    * The descriptor.
    */
  override def descriptor = {
    import Service._

    // We create a service named reservation.
    named("reservation").withCalls(

      // Map the REST endpoint for the reserve service call
      restCall(Method.POST, "/api/listing/:id/reservations", reserve _),

      // Map the REST endpoint for the getCurrentReservations service call
      restCall(Method.GET, "/api/listing/:id/reservations", getCurrentReservations _)

    ).withTopics(

      // Map the reservations Kafka topic to the events stream
      topic("reservations", events).addProperty(
        // Add the partition key strategy, to ensure that all events with the same
        // listing id end up in the same Kafka partition, and so will be consumed
        // in order.
        KafkaProperties.partitionKeyStrategy, PartitionKeyStrategy[ReservationAdded] { added =>
          added.listingId.toString
        }
      )

    // Turn on auto ACL so that we can access our defined REST endpoints through
    // the service gateway.
    ).withAutoAcl(true)
  }
}
