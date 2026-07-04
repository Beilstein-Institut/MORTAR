/*
 * MORTAR - MOlecule fRagmenTAtion fRamework
 * Copyright (C) 2026  Felix Baensch, Jonas Schaub (felix.j.baensch@gmail.com, jonas.schaub@uni-jena.de)
 *
 * Source code is available at <https://github.com/FelixBaensch/MORTAR>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.unijena.cheminf.mortar.controller;

import de.unijena.cheminf.mortar.gui.util.GuiUtil;
import de.unijena.cheminf.mortar.message.Message;
import de.unijena.cheminf.mortar.model.io.Exporter;
import de.unijena.cheminf.mortar.model.util.FileUtil;

import javafx.stage.Stage;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Headless unit and characterization tests for {@link MainViewController} (COV-01). Unlike the Phase 15 settings
 * controllers, this root controller's constructor ends in a NON-blocking {@code primaryStage.show()} (not
 * {@code showAndWait}), so the controller is constructed with a plain {@link AbstractFxTestCase#runAndWait(Runnable)}
 * over the shared {@link FxTestUtil#newMainViewController(Stage, String)} seam (reused by the sibling
 * {@code MainViewController} test classes of plans 16-02 and 16-03) rather than
 * {@link FxTestUtil#runAndDriveModal(java.util.concurrent.Callable, java.util.function.Consumer)}. The passed real
 * {@link Stage} is always hidden in a {@code finally} block; because {@code Stage.hide()} does not fire the window
 * close-request handler, the controller's {@code closeApplication}/{@code System.exit} path is never reached and the
 * test JVM fork survives.
 * <p>
 * This class pins the behavior of the {@code exportFile} and {@code closeApplication} seams that Phase 16 Plan 1 lifts
 * into package-private methods on the controller (the export precondition guard {@code areExportPreconditionsMet}, the
 * export dispatch {@code buildExportResult}, and the close-persist tail {@code persistSettingsAndStopTasks}). The
 * export characterization test drives a real one-molecule SMILES import (joining the background importer thread so the
 * assertions are deterministic, per the async-callback pitfall) and then fires {@code exportFile} with the molecules
 * tab selected, verifying — via a thread-confined {@link MockedStatic} over {@link GuiUtil} opened on the JavaFX
 * Application Thread — that the molecules-tab-selected confirmation alert is raised. This behavior MUST hold identically
 * before AND after the extraction, proving it behavior-preserving. Assertions are behavioral invariants (list
 * non-empty, alert invoked, dispatch result non-null), never exact CDK-derived strings, because the CDK 2.12 snapshot
 * is a moving target.
 *
 * @author Felix Baensch
 * @version 1.0.0.0
 */
public class MainViewControllerTest extends AbstractFxTestCase {
    //<editor-fold desc="Private static final class constants" defaultstate="collapsed">
    /**
     * A minimal, valid single-molecule SMILES line (benzene) written to a temporary {@code .smi} file so the real
     * importer produces exactly one molecule without depending on any committed test resource.
     */
    private static final String BENZENE_SMILES_LINE = "c1ccccc1 benzene\n";
    //</editor-fold>
    //
    //<editor-fold desc="Constructor" defaultstate="collapsed">
    /**
     * Default no-argument constructor; all headless setup (toolkit boot, en-GB locale, {@code user.home} isolation) is
     * inherited from {@link AbstractFxTestCase}.
     */
    public MainViewControllerTest() {
    }
    //</editor-fold>
    //
    //<editor-fold desc="Test methods" defaultstate="collapsed">
    /**
     * Characterization pin for the {@code exportFile} molecules-tab-selected guard (extraction seam E1): imports a
     * single-molecule SMILES file (leaving the molecules tab selected) and then fires
     * {@code exportFile(FRAGMENT_CSV_FILE)}, asserting the molecule list is non-empty and that the molecules-tab
     * confirmation alert was raised (the observable behavior of the guard) while no export task is started. This
     * behavior must be identical before and after the E1/E2/E3 extraction.
     *
     * @param aTempDir per-test temporary directory for the SMILES fixture
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void exportFileWithMoleculesTabSelectedRaisesConfirmationAlertTest(@TempDir Path aTempDir) throws Exception {
        File tmpSmilesFile = Files.writeString(aTempDir.resolve("in.smi"), MainViewControllerTest.BENZENE_SMILES_LINE).toFile();
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = MainViewControllerTestSupport.constructController(tmpStageReference);
            //drive a real import; joining the background importer thread makes the assertions deterministic
            AbstractFxTestCase.runAndWait(() -> {
                try (MockedStatic<GuiUtil> tmpGuiUtilMock = FxTestUtil.mockGuiAlerts()) {
                    tmpController.importMoleculeFile(tmpSmilesFile);
                }
            });
            MainViewControllerTestSupport.joinThreadField(tmpController, "importerThread");
            //drain the setOnSucceeded callback (itself nesting a Platform.runLater that opens the molecules tab)
            AbstractFxTestCase.waitForFxEvents();
            AbstractFxTestCase.waitForFxEvents();
            AbstractFxTestCase.runAndWait(() -> { });
            Assertions.assertFalse(MainViewControllerTestSupport.getMoleculeList(tmpController).isEmpty());
            //fire export with the molecules tab selected; verify the guard's confirmation alert (E1 behavior)
            AbstractFxTestCase.runAndWait(() -> {
                try (MockedStatic<GuiUtil> tmpGuiUtilMock = FxTestUtil.mockGuiAlerts()) {
                    tmpController.exportFile(Exporter.ExportTypes.FRAGMENT_CSV_FILE);
                    tmpGuiUtilMock.verify(() -> GuiUtil.guiConfirmationAlert(
                            ArgumentMatchers.eq(Message.get("Exporter.confirmationAlert.moleculesTabSelected.title")),
                            ArgumentMatchers.anyString(),
                            ArgumentMatchers.anyString()));
                }
            });
        } finally {
            MainViewControllerTestSupport.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * Unit test for the extracted export dispatch (seam E2): builds and selects a fragments tab holding one real
     * fragment, then calls {@code buildExportResult(FRAGMENT_CSV_FILE, ...)} with an already-resolved temporary CSV
     * target file (no native chooser) and asserts the returned list of failed-export fragment names is non-null. This
     * pins the dispatch invariant without depending on exact CDK-derived output.
     *
     * @param aTempDir per-test temporary directory for the CSV export target
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void buildExportResultForFragmentCsvReturnsNonNullTest(@TempDir Path aTempDir) throws Exception {
        File tmpCsvFile = aTempDir.resolve("out.csv").toFile();
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = MainViewControllerTestSupport.constructController(tmpStageReference);
            AtomicReference<List<String>> tmpResultReference = new AtomicReference<>();
            AbstractFxTestCase.runAndWait(() -> {
                try (MockedStatic<GuiUtil> tmpGuiUtilMock = FxTestUtil.mockGuiAlerts()) {
                    MainViewControllerTestSupport.setUpSelectedFragmentsTab(tmpController, "TestFragmentation");
                    Exporter tmpExporter = new Exporter(MainViewControllerTestSupport.getSettingsContainer(tmpController));
                    tmpResultReference.set(tmpController.buildExportResult(
                            tmpExporter, Exporter.ExportTypes.FRAGMENT_CSV_FILE, tmpCsvFile, false));
                } catch (Exception anException) {
                    throw new RuntimeException(anException);
                }
            });
            Assertions.assertNotNull(tmpResultReference.get());
        } finally {
            MainViewControllerTestSupport.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * Unit test for the extracted close-persist tail (seam E4): calls {@code persistSettingsAndStopTasks()} directly
     * (never {@code closeApplication}, which would reach {@code System.exit} and kill the fork) and asserts it completes
     * without throwing and produces the observable settings-directory side effect under the isolated temporary
     * {@code user.home}. The whole test class still runs to completion, proving no {@code System.exit} was reached.
     *
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void persistSettingsAndStopTasksPersistsWithoutReachingSystemExitTest() throws Exception {
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = MainViewControllerTestSupport.constructController(tmpStageReference);
            AbstractFxTestCase.runAndWait(() -> {
                try (MockedStatic<GuiUtil> tmpGuiUtilMock = FxTestUtil.mockGuiAlerts()) {
                    tmpController.persistSettingsAndStopTasks();
                }
            });
            //observable side effect: preserveSettings created the settings directory under the isolated user.home
            Assertions.assertTrue(new File(FileUtil.getSettingsDirPath()).isDirectory());
        } finally {
            MainViewControllerTestSupport.hideStage(tmpStageReference);
        }
    }
    //</editor-fold>
}
