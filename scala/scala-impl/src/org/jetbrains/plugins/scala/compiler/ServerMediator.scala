package org.jetbrains.plugins.scala
package compiler

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.{CompileTask, CompilerManager}
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{CompilerModuleExtension, ModuleRootManager}
import com.intellij.openapi.ui.Messages
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project._

/**
 * Pavel Fatin
 */

class ServerMediator(project: Project) extends ProjectComponent {

  private def isScalaProject = project.hasScala
  private val settings = ScalaCompileServerSettings.getInstance

  override def projectOpened(): Unit = {
    addBeforeTask(_ => checkSettings())
    addBeforeTask(_ => startCompileServer())
  }

  private def checkSettings(): Boolean =
    if (isScalaProject) {
      if (!checkCompilationSettings()) false
      else true
    }
    else true

  private def startCompileServer(): Boolean = {
    if (settings.COMPILE_SERVER_ENABLED && isScalaProject) {
      invokeAndWait {
        CompileServerManager.configureWidget(project)
      }

      if (CompileServerLauncher.needRestart(project)) {
        CompileServerLauncher.stop()
      }

      if (!CompileServerLauncher.running) {
        invokeAndWait {
          CompileServerLauncher.tryToStart(project)
        }
      }
    }

    true
  }

  private def addBeforeTask(compileTask: CompileTask): Unit =
    CompilerManager.getInstance(project).addBeforeTask(compileTask)

  private def checkCompilationSettings(): Boolean = {
    def hasClashes(module: Module) = module.hasScala && {
      val extension = CompilerModuleExtension.getInstance(module)
      val production = extension.getCompilerOutputUrl
      val test = extension.getCompilerOutputUrlForTests
      production == test
    }

    def maySplitSilently(module: Module): Boolean = {
      val rootManager = ModuleRootManager.getInstance(module)
      val sourceRoots = rootManager.getSourceRoots(JavaSourceRootType.SOURCE)
      val testRoots = rootManager.getSourceRoots(JavaSourceRootType.TEST_SOURCE)
      sourceRoots.isEmpty || testRoots.isEmpty
    }

    def splitOutputs(module: Module): Unit = {
      val model = module.modifiableModel
      val extension = model.getModuleExtension(classOf[CompilerModuleExtension])

      val outputUrlParts = extension.getCompilerOutputUrl match {
        case null => Seq.empty
        case url => url.split("/").toSeq
      }
      val nameForTests = if (outputUrlParts.lastOption.contains("classes")) "test-classes" else "test"

      extension.inheritCompilerOutputPath(false)
      extension.setCompilerOutputPathForTests((outputUrlParts.dropRight(1) :+ nameForTests).mkString("/"))

      model.commit()
    }

    def showSplitDialog(modulesWithClashes: Seq[Module]) = {
      Messages.showYesNoDialog(project,
        "Production and test output paths are shared in: " + modulesWithClashes.map(_.getName).mkString(" "),
        "Shared compile output paths in Scala module(s)",
        "Split output path(s) automatically", "Cancel compilation", Messages.getErrorIcon)
    }

    val modulesWithClashes = ModuleManager.getInstance(project).getModules.toSeq.filter(hasClashes)

    var mayProceedWithCompilation = true

    if (modulesWithClashes.nonEmpty) {
      val splitSilently = ApplicationManager.getApplication.isUnitTestMode ||
        modulesWithClashes.forall(maySplitSilently)

      invokeAndWait {
        val splitAutomatically = splitSilently || showSplitDialog(modulesWithClashes) == Messages.YES

        if (splitAutomatically) {
          inWriteAction {
            modulesWithClashes.foreach(splitOutputs)
            project.save()
          }
        }

        mayProceedWithCompilation = splitAutomatically
      }
    }

    mayProceedWithCompilation
  }

  override def getComponentName: String = getClass.getSimpleName
}
