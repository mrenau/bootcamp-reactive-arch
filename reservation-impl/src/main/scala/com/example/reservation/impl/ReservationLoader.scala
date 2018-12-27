package com.example.reservation.impl

import com.example.reservation.api.ReservationService
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.server.{LagomApplication, LagomApplicationContext, LagomApplicationLoader}
import play.api.LoggerConfigurator
import play.api.libs.ws.ahc.AhcWSComponents
import com.softwaremill.macwire._

/**
  * The application loader. Loads a Lagom application.
  */
class ReservationLoader extends LagomApplicationLoader {

  /**
    * Load the application in prod mode.
    *
    * For now, just use no service locator.
    */
  override def load(context: LagomApplicationContext) = new ReservationApplication(context)
    with LagomKafkaComponents {
      override def serviceLocator: ServiceLocator = NoServiceLocator
  }

  /**
    * Load the application in dev mode.
    *
    * Mix in the dev mode components to get the dev mode service locator.
    */
  override def loadDevMode(context: LagomApplicationContext) =
    new ReservationApplication(context)
      with LagomKafkaComponents
      with LagomDevModeComponents
}

/**
  * Our reservation application cake.
  */
abstract class ReservationApplication(context: LagomApplicationContext)
  // Includes the lagom application components
  extends LagomApplication(context)
  // And we use the async-http-client WS client implementation
  with AhcWSComponents
  // And mix in the Cassandra persistence components
  with CassandraPersistenceComponents {

  // Initialise logging
  LoggerConfigurator(environment.classLoader).foreach(_.configure(environment))

  /**
    * Provide a servire for the reservation service.
    */
  override def lagomServer = serverFor[ReservationService](wire[ReservationServiceImpl])

  /**
    * Register the reservation entity with the persistent entity registry
    */
  persistentEntityRegistry.register(wire[ReservationEntity])

  /**
    * Set the JSON serializer registry that Akka uses to be the reservation
    * serializer registry.
    */
  override def jsonSerializerRegistry = ReservationSerializerRegistry

  /**
    * The current reservations repository
    */
  lazy val currentReservationsRepository = wire[CurrentReservationsRepository]

  /**
    * Register the reservation read side
    */
  readSide.register(wire[ReservationEventProcessor])
}

