// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
class CoverageSuitesTest : CoverageIntegrationBaseTest() {
  @Test
  fun `test external suite adding`() {
    assertNoSuites()
    val path = SIMPLE_IJ_REPORT_PATH
    val runner = CoverageRunner.getInstance(IDEACoverageRunner::class.java)
    val suite = manager.addExternalCoverageSuite(File(path), runner)

    manager.suites.run {
      Assert.assertEquals(1, size)
      Assert.assertSame(suite, this[0])
    }

    manager.unregisterCoverageSuite(suite)
    assertNoSuites()
  }

  @Test
  fun `test coverage is closed when a suite is deleted`(): Unit = runBlocking {
    assertNoSuites()

    val ijSuite = loadIJSuite().suites[0]
    registerSuite(ijSuite)

    manager.suites.run {
      Assert.assertEquals(1, size)
      Assert.assertTrue(ijSuite in this)
    }

    val bundle = CoverageSuitesBundle(ijSuite)

    openSuiteAndWait(bundle)
    Assert.assertSame(bundle, manager.currentSuitesBundle)

    manager.unregisterCoverageSuite(ijSuite)

    assertNoSuites()
  }

  @Test
  fun `test coverage reopen if one of the suites is deleted`(): Unit = runBlocking {
    assertNoSuites()
    val ijSuite = loadIJSuite().suites[0]
    val jacocoSuite = loadJaCoCoSuite().suites[0]

    registerSuite(ijSuite)
    registerSuite(jacocoSuite)

    manager.suites.run {
      Assert.assertEquals(2, size)
      Assert.assertTrue(ijSuite in this)
      Assert.assertTrue(jacocoSuite in this)
    }

    val bundle = CoverageSuitesBundle(arrayOf(ijSuite, jacocoSuite))

    openSuiteAndWait(bundle)
    Assert.assertSame(bundle, manager.currentSuitesBundle)

    waitSuiteProcessing {
      manager.unregisterCoverageSuite(jacocoSuite)
    }

    Assert.assertEquals(1, manager.suites.size)
    manager.currentSuitesBundle.also { currentBundle ->
      Assert.assertNotSame(bundle, currentBundle)
      Assert.assertEquals(1, currentBundle.suites.size)
      Assert.assertSame(ijSuite, currentBundle.suites[0])
      closeSuite(currentBundle)
    }

    Assert.assertNull(manager.currentSuitesBundle)
    Assert.assertEquals(1, manager.suites.size)
    manager.unregisterCoverageSuite(ijSuite)
    assertNoSuites()
  }

  @Test
  fun `test suite removal with deletion asks for approval from user`() {
    assertNoSuites()
    val suite = loadIJSuiteCopy().suites[0]
    val file = File(suite.coverageDataFileName)

    registerSuite(suite)
    Assert.assertTrue(file.exists())

    try {
      manager.removeCoverageSuite(suite)
      Assert.fail("Should ask for approval, which was supposed to lead to RuntimeException")
    }
    catch (e: RuntimeException) {
      Assert.assertTrue(e.message!!.contains(file.absolutePath))
    }
    manager.unregisterCoverageSuite(suite)
    assertNoSuites()
  }

  @Test
  fun `test suite is not opened if the report file does not exist`() {
    assertNoSuites()
    val suite = loadIJSuiteCopy()
    val file = File(suite.suites[0].coverageDataFileName)
    Assert.assertTrue(file.exists())
    file.delete()

    manager.chooseSuitesBundle(suite)
    assertNoSuites()
  }

  @Test
  fun `test opening several suites`(): Unit = runBlocking {
    assertNoSuites()
    val ijSuite = loadIJSuite()
    val xmlSuite = loadXMLSuite()

    openSuiteAndWait(ijSuite)
    openSuiteAndWait(xmlSuite)

    Assert.assertSame(xmlSuite, manager.currentSuitesBundle)
    manager.activeSuites().also { activeSuites ->
      Assert.assertEquals(2, activeSuites.count())
      Assert.assertTrue(ijSuite in activeSuites)
      Assert.assertTrue(xmlSuite in activeSuites)
    }

    closeSuite(xmlSuite)

    Assert.assertSame(ijSuite, manager.currentSuitesBundle)
    manager.activeSuites().also { activeSuites ->
      Assert.assertEquals(1, activeSuites.count())
      Assert.assertTrue(ijSuite in activeSuites)
    }

    closeSuite(ijSuite)
    assertNoSuites()
  }

  @Test
  fun `test hide coverage action closes all suites`(): Unit = runBlocking {
    assertNoSuites()
    val ijSuite = loadIJSuite()
    val xmlSuite = loadXMLSuite()

    val hideAction = ActionUtil.getAction("HideCoverage")!!
    val dataContext = SimpleDataContext.getProjectContext(myProject)

    run {
      val actionEvent = AnActionEvent.createFromDataContext(ActionPlaces.MAIN_TOOLBAR, null, dataContext)
      hideAction.update(actionEvent)
      Assert.assertFalse(actionEvent.presentation.isEnabled)
    }

    openSuiteAndWait(ijSuite)
    openSuiteAndWait(xmlSuite)

    run {
      val actionEvent = AnActionEvent.createFromDataContext(ActionPlaces.MAIN_TOOLBAR, null, dataContext)
      hideAction.update(actionEvent)
      Assert.assertTrue(actionEvent.presentation.isEnabled)
      hideAction.actionPerformed(actionEvent)
    }

    assertNoSuites()
  }

  @Test
  fun `test remote suite implementation`() {
    // Some coverage implementations load a coverage report from net,
    // so they use non-standard file provider implementation:
    val remoteProvider = object : CoverageFileProvider {
      override fun getCoverageDataFilePath() = null
      override fun ensureFileExists() = true
      override fun isValid() = true
    }

    val suite = object : BaseCoverageSuite("", myProject, null, DefaultCoverageFileProvider(""), 0) {
      override fun getCoverageEngine() = TODO()
      override fun getCoverageDataFileProvider(): CoverageFileProvider = remoteProvider
    }

    Assert.assertEquals("", suite.coverageDataFileName)
  }

  private fun registerSuite(suite: CoverageSuite) {
    // Use 'presentableName' parameter to avoid report file deletion
    manager.addCoverageSuite(suite, suite.presentableName)
  }

}