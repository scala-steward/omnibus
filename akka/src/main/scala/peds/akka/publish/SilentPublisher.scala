package peds.akka.publish

import akka.actor.{ Actor, ActorLogging }
import peds.akka.envelope._


trait SilentPublisher extends EventPublisher { 
  override def publish(
    event: Any
  )(
    implicit workId: WorkId = WorkId(),
    messageNumber: MessageNumber = MessageNumber( -1 )
  ): Unit = { log.debug( s"silent publish by ${self.path}: $event" ) }
}
