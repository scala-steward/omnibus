package omnibus.akka.persistence

import scala.annotation.tailrec
import scala.collection.immutable
import scala.concurrent.Future
import scala.util.Try
import akka.agent.Agent
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.persistence.journal.AsyncWriteJournal
import com.typesafe.config.Config
import journal._
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap

/**
  * Created by rolfsd on 1/24/17.
  */
class MemoryJournal extends AsyncWriteJournal {
  private val log = Logger[MemoryJournal]

  val expectedPids: Int = MemoryJournal expectedPidsFrom context.system.settings.config
  implicit val ec = context.dispatcher

  import com.github.ghik.silencer.silent

  @silent val journal: Agent[MemoryJournal.Adapter] = Agent(
    new MemoryJournal.Adapter( expectedPids )
  )

  override def asyncWriteMessages(
    messages: immutable.Seq[AtomicWrite]
  ): Future[immutable.Seq[Try[Unit]]] = {
    journal foreach { j =>
      for {
        w <- messages
        p <- w.payload
      } {
        j add p
        if (expectedPids < j.size) {
          log.warn(
            s"number of pids:[${j.size.toString}] exceed journal's " +
            s"expected:[${expectedPids.toString}] review and " +
            s"override ${MemoryJournal.ExpectedPIDsPath} in your application.conf"
          )
        }
      }
    }

    Future successful Nil
  }

  override def asyncReadHighestSequenceNr(
    persistenceId: String,
    fromSequenceNr: Long
  ): Future[Long] = {
    journal.future map { _.highestSequenceNr( persistenceId ) }
  }

  override def asyncReplayMessages(
    persistenceId: String,
    fromSequenceNr: Long,
    toSequenceNr: Long,
    max: Long
  )(
    recoveryCallback: ( PersistentRepr ) => Unit
  ): Future[Unit] = {
    if (max == 0L) Future successful { () } else {
      journal.future
        .map { j =>
          // val START = System.nanoTime()
          val highest = j.highestSequenceNr( persistenceId )
          if (highest != 0L) {
            j.read( persistenceId, fromSequenceNr, math.min( toSequenceNr, highest ), max ) foreach {
              recoveryCallback
            }
          }
          // val FINISH = System.nanoTime()
          // logger.warn( "asyncReplayMessages start:[{}] finish:[{}] duration:[{}]", START.toString, FINISH.toString, (FINISH - START).toString )
        }
    }
  }

  override def asyncDeleteMessagesTo( persistenceId: String, toSequenceNr: Long ): Future[Unit] = {
    if (0L < toSequenceNr) {
      journal send { j =>
        val toSeqNr = math.min( toSequenceNr, j.highestSequenceNr( persistenceId ) )
        j.deleteTo( persistenceId, toSeqNr )
        log.debug(
          s"[${self.path}] for pid:[${persistenceId}] deleted messages to sequenceNr:[${toSeqNr}]"
        )
        j
      }
    }

    Future successful { () }
  }
}

//todo optimize via Agent?
object MemoryJournal {
  private val log = Logger[MemoryJournal]
  val MemoryJournalPath = "omnibus.persistence.journal.memory"

  val ExpectedPIDsPath = MemoryJournalPath + ".expected-persistence-ids"

  def expectedPidsFrom( c: Config, default: => Int = 50000 ): Int = {
    if (c hasPath ExpectedPIDsPath) c getInt ExpectedPIDsPath else default
  }

  private[MemoryJournal] class Adapter( initialSize: Int ) {

    log.warn( s"MemoryJournal.Adapter expecting up to [${initialSize}] pids" )

    private val messages: Object2ObjectOpenHashMap[String, Vector[PersistentRepr]] = {
      new Object2ObjectOpenHashMap[String, Vector[PersistentRepr]]( initialSize )
    }

    def size: Int = messages.size

    def add( p: PersistentRepr ): Unit = {
      val values = {
        Option( messages get p.persistenceId )
          .map { vs =>
            val result = vs :+ p

            val sortedResult = for { last <- vs.lastOption if p.sequenceNr <= last.sequenceNr } yield {
              log.warn(
                s"resorting since added sequenceNr[${p.sequenceNr}] " +
                s"is less than previous last element:[${vs.last.sequenceNr}]"
              )
              result.sortBy { _.sequenceNr }
            }

            sortedResult getOrElse result
          }
          .getOrElse { Vector( p ) }
      }
      messages.put( p.persistenceId, values )
    }

    def update( pid: String, snr: Long )( f: PersistentRepr => PersistentRepr ): Unit = {
      Option( messages get pid )
        .map {
          _ map { sp =>
            if (sp.sequenceNr == snr) f( sp ) else sp
          }
        }
        .foreach { updated =>
          messages.put( pid, updated )
        }
    }

    def deleteTo( pid: String, toSequenceNr: Long ): Unit = {
      Option( messages get pid )
//      .map { _ dropWhile { _.sequenceNr <= toSequenceNr } }
        .map { ms =>
          val toPos = indexToSequenceNr( toSequenceNr, ms, start = 0, last = ms.size - 1 )
//        logger.warn( "deleteTo( pid:[{}] to:[{}] ) pos:[{}] posSequenceNr:[{}]", pid, toSequenceNr.toString, toPos.toString, ms(toPos).sequenceNr.toString )
          ms drop toPos + 1
        }
        .foreach { updated =>
          messages.put( pid, updated )
          log.debug(
            s"for pid:[${pid}] deleted toSequenceNr:[${toSequenceNr}] with updated size:[${updated.size}]"
          )
        }
    }

    @tailrec private def indexToSequenceNr(
      toSequenceNr: Long,
      ms: Vector[PersistentRepr],
      start: Int,
      last: Int
    ): Int = {
      if (last < start) start
      else if (ms( last ).sequenceNr <= toSequenceNr) last
      else {
        val middle = ((start + last) / 2).toInt
        ms( middle ).sequenceNr match {
          case m if m == toSequenceNr => middle
          case m if m < toSequenceNr =>
            indexToSequenceNr( toSequenceNr, ms, start = middle + 1, last )
          case m if m > toSequenceNr =>
            indexToSequenceNr( toSequenceNr, ms, start, last = middle - 1 )
        }
      }
    }

    def read(
      pid: String,
      fromSnr: Long,
      toSnr: Long,
      max: Long
    ): immutable.Seq[PersistentRepr] = {
      Option( messages get pid )
        .map {
          _.filter { m =>
            fromSnr <= m.sequenceNr && m.sequenceNr <= toSnr
          }.take( safeLongToInt( max ) )
        }
        .getOrElse { Nil }
    }

    def highestSequenceNr( pid: String ): Long = {
      val snro = {
        for {
          ms <- Option( messages get pid )
          m  <- ms.lastOption
        } yield m.sequenceNr
      }

      snro getOrElse { 0L }
    }

    private def safeLongToInt( l: Long ): Int = if (Int.MaxValue < l) Int.MaxValue else l.toInt
  }
}
