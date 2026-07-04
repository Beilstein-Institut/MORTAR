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
import de.unijena.cheminf.mortar.model.data.MoleculeDataModel;
import de.unijena.cheminf.mortar.model.io.Exporter;

import javafx.stage.Stage;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
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
    /**
     * Bounded wait (in milliseconds) applied when joining the background importer thread, mirroring the harness's own
     * 10-second bound so a stuck import fails fast instead of hanging the CI build.
     */
    private static final long IMPORT_JOIN_TIMEOUT_MILLIS = 10_000L;
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
            MainViewController tmpController = this.constructController(tmpStageReference);
            //drive a real import; joining the background importer thread makes the assertions deterministic
            AbstractFxTestCase.runAndWait(() -> {
                try (MockedStatic<GuiUtil> tmpGuiUtilMock = FxTestUtil.mockGuiAlerts()) {
                    tmpController.importMoleculeFile(tmpSmilesFile);
                }
            });
            this.joinImporterThread(tmpController);
            //drain the setOnSucceeded callback (itself nesting a Platform.runLater that opens the molecules tab)
            AbstractFxTestCase.waitForFxEvents();
            AbstractFxTestCase.waitForFxEvents();
            AbstractFxTestCase.runAndWait(() -> { });
            Assertions.assertFalse(MainViewControllerTest.getMoleculeList(tmpController).isEmpty());
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
            MainViewControllerTest.hideStage(tmpStageReference);
        }
    }
    //</editor-fold>
    //
    //<editor-fold desc="Private helper methods" defaultstate="collapsed">
    /**
     * Constructs a {@link MainViewController} on the JavaFX Application Thread over a fresh, caller-owned {@link Stage}
     * (stored into the given reference so the caller can hide it in a {@code finally} block) via the shared
     * {@link FxTestUtil#newMainViewController(Stage, String)} seam. The application directory is the per-test isolated
     * {@code user.home} (redirected to a {@code @TempDir} by {@link AbstractFxTestCase}), which is guaranteed to exist.
     *
     * @param aStageReference sink that receives the created primary stage so it can be hidden after the test
     * @return the constructed root controller
     * @throws Exception if construction fails on the FX thread
     */
    private MainViewController constructController(AtomicReference<Stage> aStageReference) throws Exception {
        AtomicReference<MainViewController> tmpControllerReference = new AtomicReference<>();
        AbstractFxTestCase.runAndWait(() -> {
            Stage tmpPrimaryStage = new Stage();
            aStageReference.set(tmpPrimaryStage);
            try {
                tmpControllerReference.set(FxTestUtil.newMainViewController(tmpPrimaryStage, System.getProperty("user.home")));
            } catch (IOException anException) {
                throw new RuntimeException(anException);
            }
        });
        return tmpControllerReference.get();
    }
    //
    /**
     * Reflectively obtains the controller's background importer thread and joins it (bounded), so the import work is
     * complete before the FX-thread success callback is drained. This removes the race between the real importer thread
     * and {@link AbstractFxTestCase#waitForFxEvents()}, which only drains the FX event queue.
     *
     * @param aController the controller whose importer thread should be joined
     * @throws Exception if the field cannot be accessed or the join is interrupted
     */
    private void joinImporterThread(MainViewController aController) throws Exception {
        Field tmpField = MainViewController.class.getDeclaredField("importerThread");
        tmpField.setAccessible(true);
        Thread tmpImporterThread = (Thread) tmpField.get(aController);
        if (tmpImporterThread != null) {
            tmpImporterThread.join(MainViewControllerTest.IMPORT_JOIN_TIMEOUT_MILLIS);
        }
    }
    //
    /**
     * Reflectively reads the controller's private {@code moleculeDataModelList} field (no production code is widened),
     * so a test can assert the imported-molecule invariant.
     *
     * @param aController the controller instance
     * @return the controller's observable molecule data model list
     * @throws Exception if the field cannot be accessed
     */
    @SuppressWarnings("unchecked")
    private static List<MoleculeDataModel> getMoleculeList(MainViewController aController) throws Exception {
        Field tmpField = MainViewController.class.getDeclaredField("moleculeDataModelList");
        tmpField.setAccessible(true);
        return (List<MoleculeDataModel>) tmpField.get(aController);
    }
    //
    /**
     * Hides the primary stage held by the given reference on the JavaFX Application Thread. {@code Stage.hide()} does
     * NOT fire the window close-request handler, so the controller's {@code closeApplication}/{@code System.exit} path
     * is never reached.
     *
     * @param aStageReference reference to the primary stage to hide (may hold null if construction failed)
     * @throws Exception if hiding fails on the FX thread
     */
    private static void hideStage(AtomicReference<Stage> aStageReference) throws Exception {
        AbstractFxTestCase.runAndWait(() -> {
            Stage tmpStage = aStageReference.get();
            if (tmpStage != null) {
                tmpStage.hide();
            }
        });
    }
    //</editor-fold>
}
