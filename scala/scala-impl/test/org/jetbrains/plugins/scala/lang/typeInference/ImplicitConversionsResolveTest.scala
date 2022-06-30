package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class ImplicitConversionsResolveTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL17570(): Unit = checkTextHasNoErrors(
    s"""
       |object A {
       | val l: Long = 1
       | val l2: java.lang.Long = 1
       |}
       |""".stripMargin
  )

  def testSCL20378(): Unit = checkTextHasNoErrors(
    """
      |import Conversions._
      |
      |object Test {
      |  def main(args: Array[String]): Unit = {
      |    println {
      |      1.convert[String](10.0)
      |    }
      |  }
      |}
      |
      |object Conversions {
      |
      |  implicit class GenericConversion2[A, B](x: A) {
      |    def convert[R](y: B)(implicit f: (A, B) => R): R = f(x, y)
      |  }
      |
      |  implicit val intToStringM: (Int, Double) => String = (x, y) => {
      |      (y + x).toString
      |    }
      |}
      |""".stripMargin
  )
}
