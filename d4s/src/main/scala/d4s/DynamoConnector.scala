package d4s

import cats.~>
import d4s.health.DynamoDBHealthChecker
import d4s.metrics.{MacroMetricDynamoMeter, MacroMetricDynamoTimer}
import d4s.models.DynamoException.DynamoQueryException
import d4s.models.ExecutionStrategy.StrategyInput
import d4s.models.query.DynamoRequest
import d4s.models.{DynamoException, DynamoExecution}
import fs2.Stream
import izumi.functional.bio.{BIOTemporal, F}
import izumi.fundamentals.platform.language.unused
import logstage.LogBIO
import net.playq.metrics.Metrics

trait DynamoConnector[F[+_, +_]] {
  def runUnrecorded[DR <: DynamoRequest, A](q: DynamoExecution[DR, _, A]): F[DynamoException, A]
  def runUnrecorded[DR <: DynamoRequest, A](q: DynamoExecution.Streamed[DR, _, A]): Stream[F[Throwable, ?], A]

  def run[DR <: DynamoRequest, Dec, A](label: String)(q: DynamoExecution[DR, Dec, A])(implicit
                                                                                      macroTimeSaver: MacroMetricDynamoTimer[label.type],
                                                                                      macroMeterSaver: MacroMetricDynamoMeter[label.type]): F[DynamoException, A]

  def runStreamed[DR <: DynamoRequest, Dec, A](label: String)(q: DynamoExecution.Streamed[DR, Dec, A])(
    implicit
    macroTimeSaver: MacroMetricDynamoTimer[label.type],
    macroMeterSaver: MacroMetricDynamoMeter[label.type]
  ): Stream[F[DynamoException, ?], A]
}

object DynamoConnector {
  final class Impl[F[+_, +_]: BIOTemporal](
    interpreter: DynamoInterpreter[F],
    @unused dynamoDBHealthChecker: DynamoDBHealthChecker[F],
    @unused dynamoDDLService: DynamoDDLService[F],
    metrics: Metrics[F],
    log: LogBIO[F]
  ) extends DynamoConnector[F] {

    override def runUnrecorded[DR <: DynamoRequest, A](q: DynamoExecution[DR, _, A]): F[DynamoException, A] =
      runUnrecordedImpl(q).leftMap(DynamoQueryException(_))

    override def runUnrecorded[DR <: DynamoRequest, A](q: DynamoExecution.Streamed[DR, _, A]): Stream[F[Throwable, ?], A] =
      runUnrecordedImpl(q)

    private[this] def runUnrecordedImpl[DR <: DynamoRequest, Dec, Out[_[_, _]]](q: DynamoExecution.Dependent[DR, Dec, Out]): Out[F] = {
      q.executionStrategy(StrategyInput(q.dynamoQuery, F, interpreter))
    }

    override def run[DR <: DynamoRequest, Dec, A](label: String)(q: DynamoExecution[DR, Dec, A])(
      implicit
      macroTimeSaver: MacroMetricDynamoTimer[label.type],
      macroMeterSaver: MacroMetricDynamoMeter[label.type]
    ): F[DynamoException, A] = {
      recordMetrics(label) {
        runUnrecorded(q)
      }.leftMap(DynamoQueryException(label, _))
    }

    override def runStreamed[DR <: DynamoRequest, Dec, A](label: String)(q: DynamoExecution.Streamed[DR, Dec, A])(
      implicit
      macroTimeSaver: MacroMetricDynamoTimer[label.type],
      macroMeterSaver: MacroMetricDynamoMeter[label.type]
    ): Stream[F[DynamoException, ?], A] = {
      val recordStreamPage = Lambda[F[Throwable, ?] ~> F[Throwable, ?]] {
        recordMetrics(label)(_)
      }

      q.executionStrategy(StrategyInput(q.dynamoQuery, F, interpreter, recordStreamPage))
        .translate(Lambda[F[Throwable, ?] ~> F[DynamoException, ?]] {
          _.leftMap(DynamoQueryException(label, _))
        })
    }

    private[this] def recordMetrics[A](label: String)(f: F[Throwable, A])(
      implicit
      macroTimeSaver: MacroMetricDynamoTimer[label.type],
      macroMeterSaver: MacroMetricDynamoMeter[label.type]
    ): F[Throwable, A] = {
      metrics.withTimer(label) {
        f.tapError {
          exception =>
            metrics.mark(label) *>
            metrics.mark("dynamo:query-exception") *>
            log.error(s"Uncaught DynamoDB Exception $exception")
        }
      }
    }

  }

}
