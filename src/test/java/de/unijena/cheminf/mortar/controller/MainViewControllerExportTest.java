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
import de.unijena.cheminf.mortar.model.data.FragmentDataModel;
import de.unijena.cheminf.mortar.model.data.MoleculeDataModel;
import de.unijena.cheminf.mortar.model.io.Exporter;
import de.unijena.cheminf.mortar.model.settings.SettingsContainer;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Headless unit tests for the export seams of {@link MainViewController} that Phase 16 Plan 1 lifted out of the
 * {@code exportFile} method into package-private members on the controller (COV-01): the export precondition guard
 * {@code areExportPreconditionsMet} (E1), the per-type export dispatch {@code buildExportResult} (E2), the export
 * {@code Task} wiring and its success/cancel/failure callbacks {@code launchExportTask} (E3), plus the pure
 * {@code getStatusMessageByThreadType} switch and the {@link MainViewController.ThreadType} reverse lookup /
 * {@code getThreadName}.
 * <p>
 * Every branch is driven headlessly with an ALREADY-RESOLVED temporary {@link File} (or directory); the native
 * {@code Exporter.openFileChooserForExportFileOrDir} is NEVER invoked and neither is {@code exportFile} past its guard,
 * so no OS dialog is opened and the {@code System.exit} tail of {@code closeApplication} is never reached (the test JVM
 * fork survives). The controller's constructor ends in a NON-blocking {@code primaryStage.show()}, so it is constructed
 * with a plain {@link AbstractFxTestCase#runAndWait(Runnable)} over the shared
 * {@link FxTestUtil#newMainViewController(Stage, String)} seam and the caller-owned {@link Stage} is always hidden in a
 * {@code finally} ({@code Stage.hide()} does not fire the window close-request handler). Every {@link MockedStatic} over
 * {@link GuiUtil} is opened INSIDE the FX-thread body because a Mockito static mock is thread-confined and the driven
 * code runs on the JavaFX Application Thread. Assertions are behavioral invariants (return value, alert routing,
 * non-null dispatch result), never exact CDK-derived strings, since CDK 2.12 is a moving snapshot.
 *
 * @author Felix Baensch
 * @version 1.0.0.0
 */
public class MainViewControllerExportTest extends AbstractFxTestCase {
    //<editor-fold desc="Private static final class constants" defaultstate="collapsed">
    /**
     * A minimal, valid single-molecule SMILES line (benzene) written to a temporary {@code .smi} file so the real
     * importer produces exactly one molecule without depending on any committed test resource.
     */
    private static final String BENZENE_SMILES_LINE = "c1ccccc1 benzene\n";
    /**
     * The fragmentation name used as the result-tab title suffix and fragment-map key throughout these tests.
     */
    private static final String FRAGMENTATION_NAME = "TestFragmentation";
    /**
     * Bounded wait (in milliseconds) applied when joining a background thread, mirroring the harness's own 10-second
     * bound so a stuck operation fails fast instead of hanging the CI build.
     */
    private static final long JOIN_TIMEOUT_MILLIS = 10_000L;
    //</editor-fold>
    //
    //<editor-fold desc="Constructor" defaultstate="collapsed">
    /**
     * Default no-argument constructor; all headless setup (toolkit boot, en-GB locale, {@code user.home} isolation) is
     * inherited from {@link AbstractFxTestCase}.
     */
    public MainViewControllerExportTest() {
    }
    //</editor-fold>
    //
    //<editor-fold desc="E1 areExportPreconditionsMet test methods" defaultstate="collapsed">
    /**
     * E1: with the molecules tab selected after a real import, {@code areExportPreconditionsMet} raises the
     * molecules-tab-selected confirmation alert and returns {@code false} for both a fragment and an item export type.
     *
     * @param aTempDir per-test temporary directory for the SMILES fixture
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void areExportPreconditionsMetMoleculesTabSelectedAbortsTest(@TempDir Path aTempDir) throws Exception {
        File tmpSmilesFile = Files.writeString(aTempDir.resolve("in.smi"),
                MainViewControllerExportTest.BENZENE_SMILES_LINE).toFile();
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            this.importFileAndDrain(tmpController, tmpSmilesFile);
            AbstractFxTestCase.runAndWait(() -> {
                try (MockedStatic<GuiUtil> tmpGuiUtilMock = FxTestUtil.mockGuiAlerts()) {
                    Assertions.assertFalse(
                            tmpController.areExportPreconditionsMet(Exporter.ExportTypes.FRAGMENT_CSV_FILE),
                            "with the molecules tab selected the export precondition must not be met");
                    Assertions.assertFalse(
                            tmpController.areExportPreconditionsMet(Exporter.ExportTypes.ITEM_CSV_FILE),
                            "with the molecules tab selected the item export precondition must not be met");
                    tmpGuiUtilMock.verify(() -> GuiUtil.guiConfirmationAlert(
                            ArgumentMatchers.eq(Message.get("Exporter.confirmationAlert.moleculesTabSelected.title")),
                            ArgumentMatchers.anyString(),
                            ArgumentMatchers.anyString()), org.mockito.Mockito.atLeast(2));
                }
            });
        } finally {
            MainViewControllerExportTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * E1: with a fragments result tab selected that holds an EMPTY fragment list, {@code areExportPreconditionsMet}
     * raises the no-data information alert and returns {@code false} for a fragment export type.
     *
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void areExportPreconditionsMetFragmentNoDataAbortsTest() throws Exception {
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            AbstractFxTestCase.runAndWait(() -> {
                try (MockedStatic<GuiUtil> tmpGuiUtilMock = FxTestUtil.mockGuiAlerts()) {
                    MainViewControllerExportTest.getFragmentMap(tmpController)
                            .put("EmptyFragmentation", FXCollections.observableArrayList());
                    tmpController.addFragmentationResultTabs("EmptyFragmentation");
                    Assertions.assertFalse(
                            tmpController.areExportPreconditionsMet(Exporter.ExportTypes.FRAGMENT_CSV_FILE),
                            "an empty fragment list must not meet the fragment export precondition");
                    tmpGuiUtilMock.verify(() -> GuiUtil.guiMessageAlert(
                            ArgumentMatchers.eq(Alert.AlertType.INFORMATION),
                            ArgumentMatchers.eq(Message.get("Exporter.MessageAlert.NoDataAvailable.title")),
                            ArgumentMatchers.anyString(),
                            ArgumentMatchers.isNull()), org.mockito.Mockito.atLeastOnce());
                }
            });
        } finally {
            MainViewControllerExportTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * E1: with a populated fragments result tab selected but an empty molecule data model list (no import),
     * {@code areExportPreconditionsMet} raises the no-data information alert and returns {@code false} for an item
     * export type.
     *
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void areExportPreconditionsMetItemNoDataAbortsTest() throws Exception {
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            AbstractFxTestCase.runAndWait(() -> {
                try (MockedStatic<GuiUtil> tmpGuiUtilMock = FxTestUtil.mockGuiAlerts()) {
                    MainViewControllerExportTest.setUpSelectedFragmentsTab(tmpController,
                            MainViewControllerExportTest.FRAGMENTATION_NAME);
                    Assertions.assertFalse(
                            tmpController.areExportPreconditionsMet(Exporter.ExportTypes.ITEM_CSV_FILE),
                            "an empty molecule list must not meet the item export precondition");
                    tmpGuiUtilMock.verify(() -> GuiUtil.guiMessageAlert(
                            ArgumentMatchers.eq(Alert.AlertType.INFORMATION),
                            ArgumentMatchers.eq(Message.get("Exporter.MessageAlert.NoDataAvailable.title")),
                            ArgumentMatchers.anyString(),
                            ArgumentMatchers.isNull()), org.mockito.Mockito.atLeastOnce());
                }
            });
        } finally {
            MainViewControllerExportTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * E1: with a fully populated fragments/itemization state (a molecule that underwent the fragmentation, a non-empty
     * fragment list and a fragments tab selected), {@code areExportPreconditionsMet} returns {@code true} for both a
     * fragment and an item export type (the pass-through branches).
     *
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void areExportPreconditionsMetPopulatedPassesTest() throws Exception {
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            AbstractFxTestCase.runAndWait(() -> {
                MainViewControllerExportTest.setUpPopulatedFragmentsAndItems(tmpController,
                        MainViewControllerExportTest.FRAGMENTATION_NAME);
                Assertions.assertTrue(
                        tmpController.areExportPreconditionsMet(Exporter.ExportTypes.FRAGMENT_CSV_FILE),
                        "a populated fragments tab must meet the fragment export precondition");
                Assertions.assertTrue(
                        tmpController.areExportPreconditionsMet(Exporter.ExportTypes.ITEM_CSV_FILE),
                        "a populated itemization state must meet the item export precondition");
            });
        } finally {
            MainViewControllerExportTest.hideStage(tmpStageReference);
        }
    }
    //</editor-fold>
    //
    //<editor-fold desc="E2 buildExportResult test methods" defaultstate="collapsed">
    /**
     * E2: for every resolvable (non-dialog) export type, {@code buildExportResult} dispatches an already-resolved
     * temporary target file/directory to the correct {@link Exporter} method and returns a non-null list of failed
     * fragment names. No native chooser is invoked. Directory-based chemical exports (PDB, multiple SD) receive a temp
     * directory; the remaining types receive a temp file.
     *
     * @param aTempDir per-test temporary directory for the export targets
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void buildExportResultDispatchesEachResolvableTypeTest(@TempDir Path aTempDir) throws Exception {
        File tmpCsvFile = aTempDir.resolve("fragments.csv").toFile();
        File tmpPdfFile = aTempDir.resolve("fragments.pdf").toFile();
        File tmpSingleSdFile = aTempDir.resolve("fragments.sdf").toFile();
        File tmpItemCsvFile = aTempDir.resolve("items.csv").toFile();
        File tmpItemPdfFile = aTempDir.resolve("items.pdf").toFile();
        File tmpPdbDir = Files.createDirectory(aTempDir.resolve("pdb")).toFile();
        File tmpMultipleSdDir = Files.createDirectory(aTempDir.resolve("sdf")).toFile();
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            AbstractFxTestCase.runAndWait(() -> {
                try (MockedStatic<GuiUtil> tmpGuiUtilMock = FxTestUtil.mockGuiAlerts()) {
                    MainViewControllerExportTest.setUpPopulatedFragmentsAndItems(tmpController,
                            MainViewControllerExportTest.FRAGMENTATION_NAME);
                    MainViewControllerExportTest.setField(tmpController, "importedFileName", "TestInput.smi");
                    Exporter tmpExporter = new Exporter(MainViewControllerExportTest.getSettingsContainer(tmpController));
                    Assertions.assertNotNull(tmpController.buildExportResult(
                            tmpExporter, Exporter.ExportTypes.FRAGMENT_CSV_FILE, tmpCsvFile, false),
                            "FRAGMENT_CSV_FILE dispatch must return a non-null list");
                    Assertions.assertNotNull(tmpController.buildExportResult(
                            tmpExporter, Exporter.ExportTypes.FRAGMENT_PDB_FILE, tmpPdbDir, false),
                            "FRAGMENT_PDB_FILE dispatch must return a non-null list");
                    Assertions.assertNotNull(tmpController.buildExportResult(
                            tmpExporter, Exporter.ExportTypes.FRAGMENT_PDF_FILE, tmpPdfFile, false),
                            "FRAGMENT_PDF_FILE dispatch must return a non-null list");
                    Assertions.assertNotNull(tmpController.buildExportResult(
                            tmpExporter, Exporter.ExportTypes.FRAGMENT_SINGLE_SD_FILE, tmpSingleSdFile, false),
                            "FRAGMENT_SINGLE_SD_FILE dispatch must return a non-null list");
                    Assertions.assertNotNull(tmpController.buildExportResult(
                            tmpExporter, Exporter.ExportTypes.FRAGMENT_MULTIPLE_SD_FILES, tmpMultipleSdDir, false),
                            "FRAGMENT_MULTIPLE_SD_FILES dispatch must return a non-null list");
                    Assertions.assertNotNull(tmpController.buildExportResult(
                            tmpExporter, Exporter.ExportTypes.ITEM_CSV_FILE, tmpItemCsvFile, false),
                            "ITEM_CSV_FILE dispatch must return a non-null list");
                    Assertions.assertNotNull(tmpController.buildExportResult(
                            tmpExporter, Exporter.ExportTypes.ITEM_PDF_FILE, tmpItemPdfFile, false),
                            "ITEM_PDF_FILE dispatch must return a non-null list");
                } catch (Exception anException) {
                    throw new RuntimeException(anException);
                }
            });
        } finally {
            MainViewControllerExportTest.hideStage(tmpStageReference);
        }
    }
    //</editor-fold>
    //
    //<editor-fold desc="E3 launchExportTask test methods" defaultstate="collapsed">
    /**
     * E3: drives {@code launchExportTask} end to end and covers all callback branches. First a real export task is
     * launched with a resolved temp file (its background thread joined and the nested {@code Platform.runLater} success
     * callback drained: the clean, empty-failed-list branch, plus the task/thread wiring). Then the captured
     * success/cancel/failure handlers are re-invoked with swapped-in stand-in tasks to deterministically cover the
     * remaining branches: a null result raises the WARNING message alert; a non-empty failed-fragments result raises the
     * expandable alert; the cancel handler updates the status bar; and a failed task (carrying an exception) raises the
     * failure WARNING message alert. The native chooser is never invoked.
     *
     * @param aTempDir per-test temporary directory for the CSV export target
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void launchExportTaskCallbacksAllBranchesTest(@TempDir Path aTempDir) throws Exception {
        File tmpCsvFile = aTempDir.resolve("fragments.csv").toFile();
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            //real launch (clean branch): populated fragments tab + resolved temp file, then join + drain
            AbstractFxTestCase.runAndWait(() -> {
                try (MockedStatic<GuiUtil> tmpGuiUtilMock = FxTestUtil.mockGuiAlerts()) {
                    MainViewControllerExportTest.setUpSelectedFragmentsTab(tmpController,
                            MainViewControllerExportTest.FRAGMENTATION_NAME);
                    Exporter tmpExporter = new Exporter(MainViewControllerExportTest.getSettingsContainer(tmpController));
                    tmpController.launchExportTask(tmpExporter, Exporter.ExportTypes.FRAGMENT_CSV_FILE, tmpCsvFile, false);
                }
            });
            this.joinThreadField(tmpController, "exporterThread");
            AbstractFxTestCase.waitForFxEvents();
            AbstractFxTestCase.waitForFxEvents();
            AbstractFxTestCase.runAndWait(() -> { });
            //prepare stand-in tasks on the test thread so their value/exception properties are settled
            Task<List<String>> tmpNullResultTask = new Task<>() {
                @Override
                protected List<String> call() {
                    return null;
                }
            };
            Task<List<String>> tmpFailedFragmentsTask = MainViewControllerExportTest.runTaskToCompletion(
                    List.of("FailedFragmentA", "FailedFragmentB"));
            Task<List<String>> tmpExceptionTask = MainViewControllerExportTest.runTaskToFailure();
            AbstractFxTestCase.waitForFxEvents();
            //re-invoke the captured callbacks with swapped tasks to cover the remaining branches deterministically
            AbstractFxTestCase.runAndWait(() -> {
                try (MockedStatic<GuiUtil> tmpGuiUtilMock = FxTestUtil.mockGuiAlerts()) {
                    Task<?> tmpRealTask = (Task<?>) MainViewControllerExportTest.getField(tmpController, "exportTask");
                    EventHandler<WorkerStateEvent> tmpOnSucceeded = tmpRealTask.getOnSucceeded();
                    EventHandler<WorkerStateEvent> tmpOnCancelled = tmpRealTask.getOnCancelled();
                    EventHandler<WorkerStateEvent> tmpOnFailed = tmpRealTask.getOnFailed();
                    //null-result branch -> WARNING message alert
                    MainViewControllerExportTest.setField(tmpController, "exportTask", tmpNullResultTask);
                    tmpOnSucceeded.handle(new WorkerStateEvent(tmpNullResultTask,
                            WorkerStateEvent.WORKER_STATE_SUCCEEDED));
                    //non-empty failed-fragments branch -> expandable alert
                    MainViewControllerExportTest.setField(tmpController, "exportTask", tmpFailedFragmentsTask);
                    tmpOnSucceeded.handle(new WorkerStateEvent(tmpFailedFragmentsTask,
                            WorkerStateEvent.WORKER_STATE_SUCCEEDED));
                    //cancel branch -> status bar update, no alert
                    tmpOnCancelled.handle(new WorkerStateEvent(tmpNullResultTask,
                            WorkerStateEvent.WORKER_STATE_CANCELLED));
                    //failure branch -> WARNING message alert (reads the exception off the event source)
                    tmpOnFailed.handle(new WorkerStateEvent(tmpExceptionTask,
                            WorkerStateEvent.WORKER_STATE_FAILED));
                    tmpGuiUtilMock.verify(() -> GuiUtil.guiExpandableAlert(
                            ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                            ArgumentMatchers.anyString(), ArgumentMatchers.anyString()),
                            org.mockito.Mockito.atLeastOnce());
                    tmpGuiUtilMock.verify(() -> GuiUtil.guiMessageAlert(
                            ArgumentMatchers.eq(Alert.AlertType.WARNING),
                            ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.isNull()),
                            org.mockito.Mockito.times(2));
                }
            });
        } finally {
            MainViewControllerExportTest.hideStage(tmpStageReference);
        }
    }
    //</editor-fold>
    //
    //<editor-fold desc="Pure status / thread-type test methods" defaultstate="collapsed">
    /**
     * Pure {@code getStatusMessageByThreadType} switch: asserts the exact locale-pinned (en-GB) message for every
     * {@link MainViewController.ThreadType} case.
     *
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void getStatusMessageByThreadTypeAllCasesTest() throws Exception {
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            AbstractFxTestCase.runAndWait(() -> {
                Assertions.assertEquals(Message.get("Status.running"),
                        tmpController.getStatusMessageByThreadType(MainViewController.ThreadType.FRAGMENTATION_THREAD));
                Assertions.assertEquals(Message.get("Status.importing"),
                        tmpController.getStatusMessageByThreadType(MainViewController.ThreadType.IMPORT_THREAD));
                Assertions.assertEquals(Message.get("Status.exporting"),
                        tmpController.getStatusMessageByThreadType(MainViewController.ThreadType.EXPORT_THREAD));
            });
        } finally {
            MainViewControllerExportTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * {@link MainViewController.ThreadType}: {@code getThreadName} returns the registered name for every constant and
     * the {@code get(String)} reverse lookup maps every registered name back to its constant and returns {@code null}
     * for an unknown name.
     */
    @Test
    public void threadTypeGetAndGetThreadNameTest() {
        for (MainViewController.ThreadType tmpType : MainViewController.ThreadType.values()) {
            Assertions.assertEquals(tmpType, MainViewController.ThreadType.get(tmpType.getThreadName()),
                    "the reverse lookup must map a registered thread name back to its constant");
        }
        Assertions.assertNull(MainViewController.ThreadType.get("no-such-thread-name"),
                "an unknown thread name must map to null");
    }
    //</editor-fold>
    //
    //<editor-fold desc="Private helper methods" defaultstate="collapsed">
    /**
     * Constructs a {@link MainViewController} on the JavaFX Application Thread over a fresh, caller-owned {@link Stage}
     * (stored into the given reference so the caller can hide it in a {@code finally} block) via the shared
     * {@link FxTestUtil#newMainViewController(Stage, String)} seam. The application directory is the per-test isolated
     * {@code user.home}.
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
     * Drives {@code importMoleculeFile(File)} for the given file on the FX thread (alerts mocked), joins the background
     * importer thread for determinism, then drains the nested {@code Platform.runLater} success/failure callbacks.
     *
     * @param aController the controller under test
     * @param aFile the molecule file to import
     * @throws Exception if anything goes wrong on the FX thread
     */
    private void importFileAndDrain(MainViewController aController, File aFile) throws Exception {
        AbstractFxTestCase.runAndWait(() -> {
            try (MockedStatic<GuiUtil> tmpGuiUtilMock = FxTestUtil.mockGuiAlerts()) {
                aController.importMoleculeFile(aFile);
            }
        });
        this.joinThreadField(aController, "importerThread");
        AbstractFxTestCase.waitForFxEvents();
        AbstractFxTestCase.waitForFxEvents();
        AbstractFxTestCase.runAndWait(() -> { });
    }
    //
    /**
     * Reflectively joins a named background thread field of the controller (bounded), so a started operation is complete
     * before the FX-thread success callback is drained.
     *
     * @param aController the controller under test
     * @param aFieldName the name of the {@link Thread} field to join
     * @throws Exception if the join is interrupted
     */
    private void joinThreadField(MainViewController aController, String aFieldName) throws Exception {
        Thread tmpThread = (Thread) MainViewControllerExportTest.getField(aController, aFieldName);
        if (tmpThread != null) {
            tmpThread.join(MainViewControllerExportTest.JOIN_TIMEOUT_MILLIS);
        }
    }
    //
    /**
     * Runs a stand-in {@link Task} that returns the given value to completion on a background thread and returns it, so
     * the caller can read a settled {@code getValue()} (after draining FX events). Used to drive the export success
     * callback's non-empty-failed-fragments branch deterministically.
     *
     * @param aValue the value the task should return
     * @return the completed task
     * @throws Exception if the background join is interrupted
     */
    private static Task<List<String>> runTaskToCompletion(List<String> aValue) throws Exception {
        Task<List<String>> tmpTask = new Task<>() {
            @Override
            protected List<String> call() {
                return aValue;
            }
        };
        Thread tmpThread = new Thread(tmpTask);
        tmpThread.setDaemon(true);
        tmpThread.start();
        tmpThread.join(MainViewControllerExportTest.JOIN_TIMEOUT_MILLIS);
        return tmpTask;
    }
    //
    /**
     * Runs a stand-in {@link Task} that throws to completion on a background thread and returns it, so the caller can
     * read a settled {@code getException()} (after draining FX events). Used to drive the export failure callback.
     *
     * @return the failed task, carrying an exception
     * @throws Exception if the background join is interrupted
     */
    private static Task<List<String>> runTaskToFailure() throws Exception {
        Task<List<String>> tmpTask = new Task<>() {
            @Override
            protected List<String> call() throws IOException {
                throw new IOException("Simulated export failure for the failure-callback branch");
            }
        };
        Thread tmpThread = new Thread(tmpTask);
        tmpThread.setDaemon(true);
        tmpThread.start();
        tmpThread.join(MainViewControllerExportTest.JOIN_TIMEOUT_MILLIS);
        return tmpTask;
    }
    //
    /**
     * Populates the controller's fragment map with a single real fragment (carrying a parent molecule, which the
     * fragments-tab width listener dereferences) under the given fragmentation name and builds/selects the fragments and
     * itemization tabs via {@code addFragmentationResultTabs}. Must be called on the JavaFX Application Thread.
     *
     * @param aController the controller to set up
     * @param aFragmentationName the fragmentation name used as the tab title suffix and map key
     */
    private static void setUpSelectedFragmentsTab(MainViewController aController, String aFragmentationName) {
        FragmentDataModel tmpFragment = new FragmentDataModel("c1ccccc1", "Benzene", new HashMap<>());
        tmpFragment.getParentMolecules().add(new MoleculeDataModel("c1ccccc1", "BenzeneParent", new HashMap<>()));
        ObservableList<FragmentDataModel> tmpFragmentList = FXCollections.observableArrayList(tmpFragment);
        MainViewControllerExportTest.getFragmentMap(aController).put(aFragmentationName, tmpFragmentList);
        aController.addFragmentationResultTabs(aFragmentationName);
    }
    //
    /**
     * Sets up a fully populated fragments/itemization state: a molecule that has undergone the given fragmentation is
     * added to the molecule data model list and a non-empty fragment list to the fragment map BEFORE the result tabs are
     * built, so the itemization tab is populated (its items are the molecules filtered by
     * {@code hasMoleculeUndergoneSpecificFragmentation}). The fragments tab is selected. Must be called on the JavaFX
     * Application Thread. This provides the pass-through state the E1 true branches and the E2 dispatch read.
     *
     * @param aController the controller to set up
     * @param aFragmentationName the fragmentation name used as the tab title suffix and map key
     */
    private static void setUpPopulatedFragmentsAndItems(MainViewController aController, String aFragmentationName) {
        FragmentDataModel tmpFragment = new FragmentDataModel("c1ccccc1", "Benzene", new HashMap<>());
        MoleculeDataModel tmpParentMolecule = new MoleculeDataModel("c1ccccc1", "BenzeneParent", new HashMap<>());
        tmpFragment.getParentMolecules().add(tmpParentMolecule);
        ObservableList<FragmentDataModel> tmpFragmentList = FXCollections.observableArrayList(tmpFragment);
        //a molecule that underwent the fragmentation so the itemization tab is populated; both the fragment list and
        //the fragment-frequency map (keyed by unique SMILES) are needed for the itemization exports
        MoleculeDataModel tmpMolecule = new MoleculeDataModel("c1ccccc1", "Benzene", new HashMap<>());
        tmpMolecule.getAllFragments().put(aFragmentationName, new ArrayList<>(tmpFragmentList));
        Map<String, Integer> tmpFragmentFrequencies = new HashMap<>();
        tmpFragmentFrequencies.put(tmpFragment.getUniqueSmiles(), 1);
        tmpMolecule.getFragmentFrequencies().put(aFragmentationName, tmpFragmentFrequencies);
        MainViewControllerExportTest.getMoleculeList(aController).add(tmpMolecule);
        MainViewControllerExportTest.getFragmentMap(aController).put(aFragmentationName, tmpFragmentList);
        aController.addFragmentationResultTabs(aFragmentationName);
    }
    //
    /**
     * Reflectively reads the controller's private {@code moleculeDataModelList} field (no production code is widened).
     *
     * @param aController the controller instance
     * @return the controller's observable molecule data model list
     */
    @SuppressWarnings("unchecked")
    private static List<MoleculeDataModel> getMoleculeList(MainViewController aController) {
        return (List<MoleculeDataModel>) MainViewControllerExportTest.getField(aController, "moleculeDataModelList");
    }
    //
    /**
     * Reflectively reads the controller's private {@code mapOfFragmentDataModelLists} field (no production code is
     * widened).
     *
     * @param aController the controller instance
     * @return the controller's map of fragmentation-name to fragment list
     */
    @SuppressWarnings("unchecked")
    private static Map<String, ObservableList<FragmentDataModel>> getFragmentMap(MainViewController aController) {
        return (Map<String, ObservableList<FragmentDataModel>>)
                MainViewControllerExportTest.getField(aController, "mapOfFragmentDataModelLists");
    }
    //
    /**
     * Reflectively reads the controller's private {@code settingsContainer} field (no production code is widened), so a
     * test can construct an {@link Exporter} exactly as the controller does.
     *
     * @param aController the controller instance
     * @return the controller's settings container
     */
    private static SettingsContainer getSettingsContainer(MainViewController aController) {
        return (SettingsContainer) MainViewControllerExportTest.getField(aController, "settingsContainer");
    }
    //
    /**
     * Reflectively reads the value of a private field of the given controller (no production code is widened).
     *
     * @param aController the controller under test
     * @param aFieldName the name of the field to read
     * @return the current field value
     */
    private static Object getField(MainViewController aController, String aFieldName) {
        try {
            Field tmpField = MainViewController.class.getDeclaredField(aFieldName);
            tmpField.setAccessible(true);
            return tmpField.get(aController);
        } catch (ReflectiveOperationException anException) {
            throw new RuntimeException("Could not read field " + aFieldName + " via reflection", anException);
        }
    }
    //
    /**
     * Reflectively sets the value of a private field of the given controller (no production code is widened), so the
     * export callbacks can be driven with prepared stand-in state.
     *
     * @param aController the controller under test
     * @param aFieldName the name of the field to write
     * @param aValue the value to set
     */
    private static void setField(MainViewController aController, String aFieldName, Object aValue) {
        try {
            Field tmpField = MainViewController.class.getDeclaredField(aFieldName);
            tmpField.setAccessible(true);
            tmpField.set(aController, aValue);
        } catch (ReflectiveOperationException anException) {
            throw new RuntimeException("Could not write field " + aFieldName + " via reflection", anException);
        }
    }
    //
    /**
     * Hides the primary stage held by the given reference on the JavaFX Application Thread. {@code Stage.hide()} does NOT
     * fire the window close-request handler, so the controller's {@code closeApplication}/{@code System.exit} path is
     * never reached.
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
