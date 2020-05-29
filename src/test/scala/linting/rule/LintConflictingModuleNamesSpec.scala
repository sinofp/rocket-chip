// See LICENSE for license details.

package freechips.rocketchip.linting.rule

import firrtl._
import firrtl.ir.Port
import firrtl.annotations._
import firrtl.testutils.{FirrtlMatchers, FirrtlPropSpec}

case class StableNameAnnotation(target: IsModule) extends SingleTargetAnnotation[IsModule] {
  def duplicate(newTarget: IsModule): StableNameAnnotation = this.copy(target = newTarget)
}

case class UnstableNameAnnotation(target: IsModule) extends SingleTargetAnnotation[IsModule] {
  def duplicate(newTarget: IsModule): UnstableNameAnnotation = this.copy(target = newTarget)
}

class RenameDesiredNamesSpec extends FirrtlPropSpec with FirrtlMatchers {
  val transform = new RenameDesiredNames

  case class TestCase(
    input: String,
    annos: Seq[Annotation]
  )

  def stabilizeNames(testCase: TestCase): CircuitState = {
    stabilizeNames(CircuitState(Parser.parse(testCase.input), UnknownForm, testCase.annos))
  }

  def stabilizeNames(state: CircuitState): CircuitState = {
    transform.runTransform(state)
  }

  def test(testCases: TestCase *): Unit = {
    val firstState = stabilizeNames(testCases.head)
    val firstUnstableNames = firstState.annotations.collect {
      case a: UnstableNameAnnotation => a
    }
    val firstStableNames = firstState.annotations.collect {
      case a: StableNameAnnotation => a
    }

    testCases.tail.foldLeft(firstStableNames, firstUnstableNames) {
      case ((stable, unstable), testCase) =>
        val state = stabilizeNames(testCase)
        val currUnstableNames = state.annotations.collect {
          case a: UnstableNameAnnotation => a
        }
        val currStableNames = state.annotations.collect {
          case a: StableNameAnnotation => a
        }

        currStableNames should be (stable)
        currUnstableNames should not be (unstable)
        (currStableNames, currUnstableNames)
    }
  }

  property("It should rename modules to desired names if only one module") {
    val top = CircuitTarget("Foo")
    test(
      TestCase(
        """|circuit Foo:
           |  module Bar_1:
           |    output in1: UInt<1>
           |  module Bar_2:
           |    output in1: UInt<1>
           |    output in2: UInt<1>
           |  module Bar_3:
           |    output in1: UInt<1>
           |    output in2: UInt<1>
           |    output in3: UInt<1>
           |  module Foo:
           |    inst bar_1 of Bar_1
           |    inst bar_2 of Bar_2
           |    inst bar_3 of Bar_3
           |""".stripMargin,
        Seq(
          UnstableNameAnnotation(top.module("Bar_1")),
          StableNameAnnotation(top.module("Bar_2")),
          StableNameAnnotation(top.module("Bar_3")),
          OverrideDesiredNameAnnotation("Bar_2", top.module("Bar_2")),
          OverrideDesiredNameAnnotation("Bar_3", top.module("Bar_3"))
        )
      ),
      TestCase(
        """|circuit Foo:
           |  module Bar_1:
           |    output in1: UInt<2>
           |  module Bar_5:
           |    output in1: UInt<1>
           |    output in2: UInt<1>
           |  module Bar_6:
           |    output in1: UInt<1>
           |    output in2: UInt<1>
           |    output in3: UInt<1>
           |  module Foo:
           |    inst bar_1 of Bar_1
           |    inst bar_5 of Bar_5
           |    inst bar_6 of Bar_6
           |""".stripMargin,
        Seq(
          UnstableNameAnnotation(top.module("Bar_1")),
          StableNameAnnotation(top.module("Bar_5")),
          StableNameAnnotation(top.module("Bar_6")),
          OverrideDesiredNameAnnotation("Bar_4", top.module("Bar_1")),
          OverrideDesiredNameAnnotation("Bar_2", top.module("Bar_5")),
          OverrideDesiredNameAnnotation("Bar_3", top.module("Bar_6"))
        )
      )
    )
  }

  property("It should error if renaming to an already existing module name") {
    val top = CircuitTarget("Foo")
    val testCase = TestCase(
      """|circuit Foo:
         |  module Bar_1:
         |    output in1: UInt<1>
         |  module Bar_2:
         |    output in1: UInt<1>
         |    output in2: UInt<1>
         |  module Bar_3:
         |    output in1: UInt<1>
         |    output in2: UInt<1>
         |    output in3: UInt<1>
         |  module Foo:
         |    inst bar_1 of Bar_1
         |    inst bar_2 of Bar_2
         |    inst bar_3 of Bar_3
         |""".stripMargin,
      Seq(OverrideDesiredNameAnnotation("Bar_1", top.module("Bar_2")))
    )
    an [Exception] should be thrownBy stabilizeNames(testCase)
  }
}
