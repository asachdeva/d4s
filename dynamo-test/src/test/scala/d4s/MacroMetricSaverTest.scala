package d4s

import izumi.fundamentals.platform.language.Quirks._
import d4s.metrics.MacroMetricDynamoMeter
import net.playq.metrics.MacroMetricCounter
import net.playq.metrics.macrodefs.MacroMetricBase
import org.scalatest.wordspec.AnyWordSpec

final class MacroMetricSaverTest extends AnyWordSpec {
  private[this] val str = s"${1} x ${2}"
  str.discard()

  "print an error message mentioning discarder when used with a non-constant type" in {
    shapeless.test.illTyped("implicitly[MacroMetricCounter[str.type]]", ".*discarded.*")
  }

  "print an error message if there is no implicit for mentioned metric" in {
    shapeless.test.illTyped("implicitly[MacroMetricSaverTest.TestMetric.MetricBase[str.type, Nothing]]", "import Nothing.discarded._ to disable.*")
    shapeless.test.illTyped("implicitly[MacroMetricDynamoMeter[str.type]]", ".*import.*MacroMetricsDynamo.discarded._.*")
  }

}

object MacroMetricSaverTest {
  object TestMetric extends MacroMetricBase.Counter
}
