package org.jetbrains.plugins.scala.codeInspection.internal

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.{LocalInspectionTool, NonExtendableApiUsageInspection}
import com.intellij.openapi.roots.{ModifiableRootModel, ModuleRootModificationUtil}
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.PathUtil
import org.jetbrains.plugins.scala.codeInspection.internal.NonExtendableApiUsageInspectionTest.HighlightMessage
import org.jetbrains.plugins.scala.codeInspection.{ScalaHighlightsTestBase, ScalaInspectionTestBase}
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals

import java.util

/**
 * Test UAST-based inspection from platform: [[com.intellij.codeInspection.NonExtendableApiUsageInspection]]
 *
 * Some parts copied from com.intellij.codeInspection.tests.kotlin.NonExtendableApiUsageInspectionTest
 */
class NonExtendableApiUsageInspectionTest extends ScalaInspectionTestBase {

  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[NonExtendableApiUsageInspection]

  override protected val description: String = ""

  //Example of message: "must not be extended/implemented/overridden"
  override protected def descriptionMatches(s: String): Boolean =
    s != null && s.contains("must not be")

  override def getTestDataPath =
    super.getTestDataPath + "inspections/internal/NonExtendableApiUsageInspection/"

  override def setUpLibraries(implicit module: com.intellij.openapi.module.Module): Unit = {
    super.setUpLibraries(module)

    ModuleRootModificationUtil.updateModel(module, (model: ModifiableRootModel) => {
      PsiTestUtil.addProjectLibrary(model, "annotations", util.Arrays.asList(PathUtil.getJarPathForClass(classOf[org.jetbrains.annotations.ApiStatus.NonExtendable])))
      PsiTestUtil.addProjectLibrary(model, "library", util.Arrays.asList(getTestDataPath + "library_root"))
    })
  }

  protected def doTest(text: String): Unit = {
    checkTextHasError(text)
  }

  //TODO: this might fail when IDEA-294924 is fixed
  //  simply add new expected highlighting
  def testAllInOne(): Unit = {
    val text =
      """import library.JavaClass
        |import library.JavaInterface
        |import library.JavaMethodOwner
        |import library.JavaNestedClassOwner
        |import library.JavaNonExtendableNestedOwner
        |import library.KotlinClass
        |import library.KotlinInterface
        |import library.KotlinMethodOwner
        |import library.KotlinNestedClassOwner
        |import library.KotlinNonExtendableNestedOwner
        |
        |//////////////////////////////////////
        |//Extensions of Java classes
        |//////////////////////////////////////
        |class ScalaClass_Extends_JavaClass extends JavaClass
        |class ScalaClass_Extends_JavaInterface extends JavaInterface
        |trait ScalaTrait_Extends_JavaInterface extends JavaInterface
        |object ScalaObject_Extends_JavaClass extends JavaClass
        |class ScalaClass_ExtendsNestedJavaClass extends JavaNonExtendableNestedOwner.NonExtendableNested
        |class ScalaClass_Extends_JavaClass_ByFqn extends library.JavaClass
        |
        |class Scala_Overrides_JavaMethod_NoExplicitReturnType extends JavaMethodOwner {
        |  override def doNotOverride() = ()
        |}
        |class Scala_Overrides_JavaMethod_ExplicitUnitReturnType extends JavaMethodOwner {
        |  override def doNotOverride(): Unit = ()
        |}
        |
        |//no warning
        |class ScalaClass_Extend_ExtendableJavaNestedClass extends JavaNestedClassOwner.NestedClass {}
        |
        |//////////////////////////////////////
        |//Extensions of Kotlin classes
        |//////////////////////////////////////
        |class ScalaClass_Extends_KotlinClass extends KotlinClass
        |class ScalaClass_Extends_KotlinInterface extends KotlinInterface
        |trait ScalaTrait_Extends_KotlinInterface extends KotlinInterface
        |object ScalaObject_Extends_KotlinClass extends KotlinClass
        |class ScalaClass_ExtendsNestedKotlinClass extends KotlinNonExtendableNestedOwner.NonExtendableNested
        |class ScalaClass_Extends_KotlinClass_ByFqn extends library.KotlinClass
        |
        |class Scala_Overrides_KotlinMethod_NoExplicitReturnType extends KotlinMethodOwner {
        |  override def doNotOverride() = ()
        |}
        |class Scala_Overrides_KotlinMethod_ExplicitUnitReturnType extends KotlinMethodOwner {
        |  override def doNotOverride(): Unit = ()
        |}
        |
        |//no warning
        |class ScalaClass_Extend_ExtendableKotlinNestedClass extends KotlinNestedClassOwner.NestedClass {}
        |
        |class AnonymousClasses {
        |  new JavaClass() {}
        |  new JavaInterface() {}
        |  new library.JavaClass() {}
        |  new JavaNonExtendableNestedOwner.NonExtendableNested() {}
        |
        |  new KotlinClass() {}
        |  new KotlinInterface() {}
        |  new KotlinNonExtendableNestedOwner.NonExtendableNested() {}
        |
        |  new JavaMethodOwner() { override def doNotOverride() = () }
        |  new KotlinMethodOwner() { override def doNotOverride() = () }
        |
        |  new JavaMethodOwner() { override def doNotOverride(): Unit = () }
        |  new KotlinMethodOwner() { override def doNotOverride(): Unit = () }
        |
        |  //No warnings
        |  new JavaNestedClassOwner.NestedClass() {}
        |  new KotlinNestedClassOwner.NestedClass() {}
        |}
        |""".stripMargin

    //NOTE: here I use `configureByText` not as it was originally intended by configureByText
    // this is because I want to test each inspection message more fine-grainy
    val ScalaHighlightsTestBase.TestPrepareResult(_, _, actualHighlights) = configureByText(text)

    val actualHighlightMessages: Seq[HighlightMessage] = actualHighlights.map(HighlightMessage.apply)
    assertCollectionEquals(
      Seq[HighlightMessage](
        HighlightMessage((491, 500), "Class 'library.JavaClass' must not be extended"),
        HighlightMessage((548, 561), "Interface 'library.JavaInterface' must not be implemented"),
        HighlightMessage((609, 622), "Interface 'library.JavaInterface' must not be extended"),
        HighlightMessage((668, 677), "Class 'library.JavaClass' must not be extended"),
        HighlightMessage((938, 951), "Method 'doNotOverride()' must not be overridden"),
        HighlightMessage((1058, 1071), "Method 'doNotOverride()' must not be overridden"),

        HighlightMessage((1350, 1361), "Class 'library.KotlinClass' must not be extended"),
        HighlightMessage((1411, 1426), "Interface 'library.KotlinInterface' must not be implemented"),
        HighlightMessage((1476, 1491), "Interface 'library.KotlinInterface' must not be extended"),
        HighlightMessage((1539, 1550), "Class 'library.KotlinClass' must not be extended"),
        HighlightMessage((1823, 1836), "Method 'doNotOverride()' must not be overridden"),
        HighlightMessage((1947, 1960), "Method 'doNotOverride()' must not be overridden"),

        //in AnonymousClasses
        HighlightMessage((2120, 2129), "Class 'library.JavaClass' must not be extended"),
        HighlightMessage((2141, 2154), "Interface 'library.JavaInterface' must not be implemented"),
        HighlightMessage((2256, 2267), "Class 'library.KotlinClass' must not be extended"),
        HighlightMessage((2279, 2294), "Interface 'library.KotlinInterface' must not be implemented"),

        HighlightMessage((2402, 2415), "Method 'doNotOverride()' must not be overridden"),
        HighlightMessage((2466, 2479), "Method 'doNotOverride()' must not be overridden"),
        HighlightMessage((2529, 2542), "Method 'doNotOverride()' must not be overridden"),
        HighlightMessage((2599, 2612), "Method 'doNotOverride()' must not be overridden"),
      ),
      actualHighlightMessages
    )
  }
}

object NonExtendableApiUsageInspectionTest {
  case class HighlightMessage(range: TextRange, description: String)
  object HighlightMessage {
    def apply(range: (Int, Int), description: String): HighlightMessage =
      new HighlightMessage(TextRange.create(range._1, range._2), description)

    def apply(info: HighlightInfo): HighlightMessage = {
      val range = TextRange.create(info.getStartOffset, info.getEndOffset)
      HighlightMessage(range, info.getDescription)
    }
  }
}
