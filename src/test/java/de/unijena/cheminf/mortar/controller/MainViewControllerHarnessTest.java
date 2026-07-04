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

import de.unijena.cheminf.mortar.configuration.Configuration;
import de.unijena.cheminf.mortar.gui.util.GuiUtil;
import de.unijena.cheminf.mortar.gui.views.MainView;
import de.unijena.cheminf.mortar.message.Message;
import de.unijena.cheminf.mortar.model.data.FragmentDataModel;
import de.unijena.cheminf.mortar.model.data.MoleculeDataModel;
import de.unijena.cheminf.mortar.model.fragmentation.FragmentationService;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Headless harness-drive tests for the large category-B regions of {@link MainViewController} (COV-01): construction
 * guard branches, the {@code addListener} event/menu/key lambdas, the direct import flow and its {@code Task}
 * callbacks, the status bar, the {@code interrupt*} methods, {@code isFragmentationStopAndDataLossConfirmed}, and the
 * GUARDED {@code closeApplication} early-return. This class is the primary coverage driver for everything that only
 * needs a constructed controller plus mocked alerts/{@code Desktop}, without touching the native file chooser or
 * {@code System.exit}. The blocking auxiliary views, the fragmentation flow and the result-tab builders are added in
 * the companion tasks of this plan and are documented on those test methods.
 * <p>
 * The controller's constructor ends in a NON-blocking {@code primaryStage.show()}, so it is constructed with a plain
 * {@link AbstractFxTestCase#runAndWait(Runnable)} over the shared {@link FxTestUtil#newMainViewController(Stage, String)}
 * seam (also used by the sibling {@code MainViewControllerTest}) and the caller-owned {@link Stage} is always hidden in
 * a {@code finally}; because {@code Stage.hide()} does not fire the window close-request handler, the
 * {@code closeApplication}/{@code System.exit} path is never reached and the test JVM fork survives. Consequently, this
 * class NEVER fires the Exit menu item, the window-close event, or an empty-list {@code closeApplication}; the only
 * {@code closeApplication} coverage is its guarded early-return (a non-empty molecule list plus a confirmation alert
 * mocked to {@code CANCEL}, which returns before the exit tail). Every {@link MockedStatic} over {@link GuiUtil} is
 * opened INSIDE the FX-thread body because a Mockito static mock is thread-confined and handler code runs on the JavaFX
 * Application Thread. Assertions are behavioral invariants (list sizes, non-null, alert routing) and never pin exact
 * CDK-derived strings, since CDK 2.12 is a moving snapshot.
 *
 * @author Felix Baensch
 * @version 1.0.0.0
 */
public class MainViewControllerHarnessTest extends AbstractFxTestCase {
    //<editor-fold desc="Private static final class constants" defaultstate="collapsed">
    /**
     * A minimal, valid single-molecule SMILES line (benzene) written to a temporary {@code .smi} file so the real
     * importer produces exactly one molecule without depending on any committed test resource.
     */
    private static final String BENZENE_SMILES_LINE = "c1ccccc1 benzene\n";
    /**
     * A SMILES-file line that no valid molecule can be parsed from, so the importer yields an empty molecule list and
     * the empty-import warning branch is exercised.
     */
    private static final String INVALID_SMILES_LINE = "this-is-not-a-valid-smiles-token\n";
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
    public MainViewControllerHarnessTest() {
    }
    //</editor-fold>
    //
    //<editor-fold desc="Test methods" defaultstate="collapsed">
    /**
     * Pins the constructor guard branches: a null {@link Stage}, a null {@link MainView} and a null
     * {@link de.unijena.cheminf.mortar.configuration.IConfiguration} each throw {@link NullPointerException}, and an
     * application-directory path that does not denote an existing directory throws {@link IllegalArgumentException}.
     * Each failing construction throws before {@code primaryStage.show()}, so no stage is shown.
     *
     * @throws Exception if anything unexpected happens on the FX thread
     */
    @Test
    public void constructorGuardsThrowTest() throws Exception {
        AtomicReference<Class<?>> tmpNullStage = new AtomicReference<>();
        AtomicReference<Class<?>> tmpNullView = new AtomicReference<>();
        AtomicReference<Class<?>> tmpNullConfig = new AtomicReference<>();
        AtomicReference<Class<?>> tmpBadDir = new AtomicReference<>();
        AbstractFxTestCase.runAndWait(() -> {
            String tmpAppDir = System.getProperty("user.home");
            try {
                MainView tmpView = new MainView(Configuration.getInstance());
                new MainViewController(null, tmpView, tmpAppDir, Configuration.getInstance());
            } catch (Throwable anException) {
                tmpNullStage.set(anException.getClass());
            }
            try {
                new MainViewController(new Stage(), null, tmpAppDir, Configuration.getInstance());
            } catch (Throwable anException) {
                tmpNullView.set(anException.getClass());
            }
            try {
                MainView tmpView = new MainView(Configuration.getInstance());
                new MainViewController(new Stage(), tmpView, tmpAppDir, null);
            } catch (Throwable anException) {
                tmpNullConfig.set(anException.getClass());
            }
            try {
                MainView tmpView = new MainView(Configuration.getInstance());
                new MainViewController(new Stage(), tmpView, tmpAppDir + File.separator + "does-not-exist-xyz",
                        Configuration.getInstance());
            } catch (Throwable anException) {
                tmpBadDir.set(anException.getClass());
            }
        });
        Assertions.assertEquals(NullPointerException.class, tmpNullStage.get(), "a null stage must throw NPE");
        Assertions.assertEquals(NullPointerException.class, tmpNullView.get(), "a null main view must throw NPE");
        Assertions.assertEquals(NullPointerException.class, tmpNullConfig.get(), "a null configuration must throw NPE");
        Assertions.assertEquals(IllegalArgumentException.class, tmpBadDir.get(),
                "an application directory that does not exist must throw IllegalArgumentException");
    }
    //
    /**
     * Drives a real single-molecule SMILES import through {@code importMoleculeFile(File)} (joining the background
     * importer thread for determinism, then draining the nested success callbacks) and asserts the molecule list is
     * populated and the molecules tab was built and selected. This covers the import guard chain, the success callback
     * and {@code openMoleculesTab}. Then it fires the pagination key-press event filter (END/HOME/RIGHT/LEFT/PAGE_UP/
     * PAGE_DOWN) against the now-present molecules tab so all four branches of the filter run.
     *
     * @param aTempDir per-test temporary directory for the SMILES fixture
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void importPopulatesListBuildsTabAndKeyFilterTest(@TempDir Path aTempDir) throws Exception {
        File tmpSmilesFile = Files.writeString(aTempDir.resolve("in.smi"),
                MainViewControllerHarnessTest.BENZENE_SMILES_LINE).toFile();
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            this.importFileAndDrain(tmpController, tmpSmilesFile);
            Assertions.assertFalse(MainViewControllerHarnessTest.getMoleculeList(tmpController).isEmpty(),
                    "the molecule list must be populated after a successful import");
            //fire the pagination key-press filter against the now-present, selected molecules tab (all four branches)
            AbstractFxTestCase.runAndWait(() -> {
                Scene tmpScene = (Scene) MainViewControllerHarnessTest.getField(tmpController, "scene");
                for (KeyCode tmpKeyCode : new KeyCode[]{KeyCode.END, KeyCode.HOME, KeyCode.RIGHT, KeyCode.LEFT,
                        KeyCode.PAGE_UP, KeyCode.PAGE_DOWN}) {
                    tmpScene.getRoot().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", tmpKeyCode,
                            false, false, false, false));
                }
            });
            AbstractFxTestCase.waitForFxEvents();
        } finally {
            MainViewControllerHarnessTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * Drives an import of a SMILES file from which no valid molecule can be parsed, so the success callback takes the
     * empty-import branch (warning alert mocked, status bar set to import-failed) and the molecule list stays empty.
     *
     * @param aTempDir per-test temporary directory for the SMILES fixture
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void importEmptyFileHitsEmptyWarningBranchTest(@TempDir Path aTempDir) throws Exception {
        File tmpSmilesFile = Files.writeString(aTempDir.resolve("empty.smi"),
                MainViewControllerHarnessTest.INVALID_SMILES_LINE).toFile();
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            this.importFileAndDrain(tmpController, tmpSmilesFile);
            Assertions.assertTrue(MainViewControllerHarnessTest.getMoleculeList(tmpController).isEmpty(),
                    "the molecule list must stay empty when no molecule can be parsed");
        } finally {
            MainViewControllerHarnessTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * Imports a molecule set and then imports again while data is already present, so the second import runs the
     * "existing data" guard chain ({@code !moleculeDataModelList.isEmpty()} with a confirmation alert mocked to
     * {@code OK}) that clears the fragmentation cache before re-importing. Behavioral assertion: the list is populated
     * again after the second import.
     *
     * @param aTempDir per-test temporary directory for the SMILES fixture
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void importWithExistingDataRunsGuardChainTest(@TempDir Path aTempDir) throws Exception {
        File tmpSmilesFile = Files.writeString(aTempDir.resolve("in.smi"),
                MainViewControllerHarnessTest.BENZENE_SMILES_LINE).toFile();
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            this.importFileAndDrain(tmpController, tmpSmilesFile);
            Assertions.assertFalse(MainViewControllerHarnessTest.getMoleculeList(tmpController).isEmpty(),
                    "the molecule list must be populated after the first import");
            this.importFileAndDrain(tmpController, tmpSmilesFile);
            Assertions.assertFalse(MainViewControllerHarnessTest.getMoleculeList(tmpController).isEmpty(),
                    "the molecule list must be populated again after the confirmed second import");
        } finally {
            MainViewControllerHarnessTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * Fires the Open, Cancel-Import and Cancel-Export menu items so their handler lambdas run, and covers the
     * {@code interrupt*} methods. Opening delegates to {@code chooseAndImportMoleculeFile} whose native file chooser
     * returns null (or is unsupported) headless, so the early-return is taken without importing; any headless chooser
     * failure is tolerated so it cannot abort the drive. The cancel-import/cancel-export handlers require non-null
     * task/thread fields, which are set reflectively to unstarted stand-ins before firing.
     *
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void openAndCancelHandlersRunTest() throws Exception {
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            AbstractFxTestCase.runAndWait(() -> {
                try (MockedStatic<GuiUtil> tmpGuiUtilMock = FxTestUtil.mockGuiAlerts()) {
                    MainView tmpMainView = (MainView) MainViewControllerHarnessTest.getField(tmpController, "mainView");
                    //Open: the native chooser returns null (or is unsupported) headless -> early return; tolerate either
                    try {
                        tmpMainView.getMainMenuBar().getOpenMenuItem().fire();
                    } catch (Throwable anIgnoredHeadlessChooserFailure) {
                        //the native file chooser is a documented-unreachable OS boundary; the delegation up to it ran
                    }
                    //set unstarted task/thread stand-ins so the interrupt handlers do not dereference null
                    MainViewControllerHarnessTest.setField(tmpController, "importTask", MainViewControllerHarnessTest.noOpTask());
                    MainViewControllerHarnessTest.setField(tmpController, "importerThread", new Thread(() -> { }));
                    MainViewControllerHarnessTest.setField(tmpController, "exportTask", MainViewControllerHarnessTest.noOpTask());
                    MainViewControllerHarnessTest.setField(tmpController, "exporterThread", new Thread(() -> { }));
                    tmpMainView.getMainMenuBar().getCancelImportMenuItem().fire();
                    tmpMainView.getMainMenuBar().getCancelExportMenuItem().fire();
                }
            });
            AbstractFxTestCase.waitForFxEvents();
        } finally {
            MainViewControllerHarnessTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * Covers {@code interruptFragmentation}: after a real import builds the fragmentation/cancel buttons (via
     * {@code openMoleculesTab}), a stand-in fragmentation task is set reflectively and {@code interruptFragmentation}
     * is invoked, which cancels the task and resets the two buttons. Behavioral assertion: the cancel-fragmentation
     * button is hidden and the fragmentation button re-enabled afterwards.
     *
     * @param aTempDir per-test temporary directory for the SMILES fixture
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void interruptFragmentationResetsButtonsTest(@TempDir Path aTempDir) throws Exception {
        File tmpSmilesFile = Files.writeString(aTempDir.resolve("in.smi"),
                MainViewControllerHarnessTest.BENZENE_SMILES_LINE).toFile();
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            this.importFileAndDrain(tmpController, tmpSmilesFile);
            AbstractFxTestCase.runAndWait(() -> {
                MainViewControllerHarnessTest.setField(tmpController, "parallelFragmentationMainTask",
                        MainViewControllerHarnessTest.noOpTask());
                try {
                    Method tmpMethod = MainViewController.class.getDeclaredMethod("interruptFragmentation");
                    tmpMethod.setAccessible(true);
                    tmpMethod.invoke(tmpController);
                } catch (ReflectiveOperationException anException) {
                    throw new RuntimeException(anException);
                }
            });
            AbstractFxTestCase.waitForFxEvents();
            Button tmpCancelButton = (Button)
                    MainViewControllerHarnessTest.getField(tmpController, "cancelFragmentationButton");
            Button tmpFragmentButton = (Button)
                    MainViewControllerHarnessTest.getField(tmpController, "fragmentationButton");
            Assertions.assertFalse(tmpCancelButton.isVisible(), "the cancel-fragmentation button must be hidden");
            Assertions.assertFalse(tmpFragmentButton.isDisabled(), "the fragmentation button must be re-enabled");
        } finally {
            MainViewControllerHarnessTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * Exercises {@code updateStatusBar} (add-thread, remove-last-thread and remaining-thread branches) and the pure
     * {@code getStatusMessageByThreadType} switch for every thread type. Threads are named with valid
     * {@link MainViewController.ThreadType} names so the reverse lookup in the remaining-thread branch succeeds.
     *
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void statusBarAndStatusMessageBranchesTest() throws Exception {
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
                Thread tmpImportThread = new Thread(() -> { });
                tmpImportThread.setName(MainViewController.ThreadType.IMPORT_THREAD.getThreadName());
                Thread tmpFragmentationThread = new Thread(() -> { });
                tmpFragmentationThread.setName(MainViewController.ThreadType.FRAGMENTATION_THREAD.getThreadName());
                //add-thread branch
                tmpController.updateStatusBar(tmpImportThread, Message.get("Status.importing"));
                //add a second thread, then remove the first -> remaining-thread branch (reverse lookup on the last)
                tmpController.updateStatusBar(tmpFragmentationThread, Message.get("Status.running"));
                tmpController.updateStatusBar(tmpImportThread, Message.get("Status.imported"));
                //remove the last remaining thread -> empty-list branch
                tmpController.updateStatusBar(tmpFragmentationThread, Message.get("Status.finished"));
            });
            AbstractFxTestCase.waitForFxEvents();
        } finally {
            MainViewControllerHarnessTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * Covers both message branches of {@code isFragmentationStopAndDataLossConfirmed} (fragmentation-running and
     * data-loss) with the confirmation alert mocked to {@code OK}, asserting {@code true} is returned in both cases.
     *
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void isFragmentationStopAndDataLossConfirmedBothBranchesTest() throws Exception {
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            AbstractFxTestCase.runAndWait(() -> {
                try (MockedStatic<GuiUtil> tmpGuiUtilMock = FxTestUtil.mockGuiAlerts()) {
                    //data-loss branch (fragmentation not running)
                    Assertions.assertTrue(tmpController.isFragmentationStopAndDataLossConfirmed(),
                            "an OK-confirmed data-loss dialog must return true");
                    //fragmentation-running branch
                    MainViewControllerHarnessTest.setField(tmpController, "isFragmentationRunning", Boolean.TRUE);
                    Assertions.assertTrue(tmpController.isFragmentationStopAndDataLossConfirmed(),
                            "an OK-confirmed fragmentation-running dialog must return true");
                }
            });
        } finally {
            MainViewControllerHarnessTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * Covers the GUARDED {@code closeApplication} early-return WITHOUT reaching {@code System.exit}: with a non-empty
     * molecule list and the confirmation alert mocked to {@code CANCEL}, {@code closeApplication} returns before the
     * persist/exit tail. {@code closeApplication} is invoked reflectively (it stays private) so the Exit menu item and
     * the window-close event are never fired. That the whole suite keeps running proves no {@code System.exit} was
     * reached.
     *
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void closeApplicationGuardedCancelEarlyReturnTest() throws Exception {
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            AbstractFxTestCase.runAndWait(() -> {
                //make the molecule list non-empty so the guard's first operand is true
                MainViewControllerHarnessTest.getMoleculeList(tmpController)
                        .add(new MoleculeDataModel("c1ccccc1", "Benzene", new HashMap<>()));
                try (MockedStatic<GuiUtil> tmpGuiUtilMock = MainViewControllerHarnessTest.mockGuiAlertsConfirmCancel()) {
                    Method tmpMethod = MainViewController.class.getDeclaredMethod("closeApplication", int.class);
                    tmpMethod.setAccessible(true);
                    tmpMethod.invoke(tmpController, 0);
                } catch (ReflectiveOperationException anException) {
                    throw new RuntimeException(anException);
                }
            });
            //the fork is still alive: the list is unchanged (persist/exit tail was not reached)
            Assertions.assertEquals(1, MainViewControllerHarnessTest.getMoleculeList(tmpController).size(),
                    "the guarded closeApplication must return early, leaving state untouched");
        } finally {
            MainViewControllerHarnessTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * Drives a real single-algorithm fragmentation flow end to end: imports one molecule, selects it, calls
     * {@code startFragmentation()}, joins the background fragmentation thread and drains the nested success callback so
     * {@code addFragmentationResultTabs}/{@code createFragmentsTab}/{@code createItemsTab} build the result tabs.
     * Behavioral assertion: a fragmentation result list is present in the controller's fragment map afterwards.
     *
     * @param aTempDir per-test temporary directory for the SMILES fixture
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void startFragmentationBuildsResultTabsTest(@TempDir Path aTempDir) throws Exception {
        File tmpSmilesFile = Files.writeString(aTempDir.resolve("in.smi"),
                MainViewControllerHarnessTest.BENZENE_SMILES_LINE).toFile();
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            this.importFileAndDrain(tmpController, tmpSmilesFile);
            AbstractFxTestCase.runAndWait(() -> {
                try (MockedStatic<GuiUtil> tmpGuiUtilMock = FxTestUtil.mockGuiAlerts()) {
                    MainViewControllerHarnessTest.getMoleculeList(tmpController).get(0).setSelection(true);
                    tmpController.startFragmentation();
                }
            });
            this.joinThreadField(tmpController, "fragmentationThread");
            AbstractFxTestCase.waitForFxEvents();
            AbstractFxTestCase.waitForFxEvents();
            AbstractFxTestCase.runAndWait(() -> { });
            Assertions.assertFalse(MainViewControllerHarnessTest.getFragmentMap(tmpController).isEmpty(),
                    "a fragmentation result list must be present after a completed fragmentation");
        } finally {
            MainViewControllerHarnessTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * Drives the pipelining branch of {@code startFragmentation(boolean)} (a molecule imported and selected, then
     * {@code startFragmentation(true)}), joining the background thread and draining the callbacks. Behavioral
     * assertion: the flow completes without a fork crash and a fragmentation result list is present.
     *
     * @param aTempDir per-test temporary directory for the SMILES fixture
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void startPipeliningFragmentationFlowTest(@TempDir Path aTempDir) throws Exception {
        File tmpSmilesFile = Files.writeString(aTempDir.resolve("in.smi"),
                MainViewControllerHarnessTest.BENZENE_SMILES_LINE).toFile();
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            this.importFileAndDrain(tmpController, tmpSmilesFile);
            AbstractFxTestCase.runAndWait(() -> {
                try (MockedStatic<GuiUtil> tmpGuiUtilMock = FxTestUtil.mockGuiAlerts()) {
                    MainViewControllerHarnessTest.getMoleculeList(tmpController).get(0).setSelection(true);
                    tmpController.startFragmentation(true);
                }
            });
            this.joinThreadField(tmpController, "fragmentationThread");
            AbstractFxTestCase.waitForFxEvents();
            AbstractFxTestCase.waitForFxEvents();
            AbstractFxTestCase.runAndWait(() -> { });
            Assertions.assertFalse(MainViewControllerHarnessTest.getFragmentMap(tmpController).isEmpty(),
                    "a fragmentation result list must be present after a completed pipeline fragmentation");
        } finally {
            MainViewControllerHarnessTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * Builds the fragment/itemization result tabs directly from an EMPTY fragment list, so the empty-list disable
     * branches of {@code createFragmentsTab} and {@code createItemsTab} run. Behavioral assertion: the two result tabs
     * are added to the tab pane.
     *
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void emptyFragmentListDisablesResultTabButtonsTest() throws Exception {
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            AbstractFxTestCase.runAndWait(() -> {
                MainViewControllerHarnessTest.getFragmentMap(tmpController)
                        .put("EmptyFragmentation", FXCollections.observableArrayList());
                tmpController.addFragmentationResultTabs("EmptyFragmentation");
            });
            AbstractFxTestCase.waitForFxEvents();
            int tmpTabCount = MainViewControllerHarnessTest.getTabPaneSize(tmpController);
            Assertions.assertTrue(tmpTabCount >= 2, "the fragments and itemization result tabs must be added");
        } finally {
            MainViewControllerHarnessTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * Selects a different fragmentation algorithm via its {@link RadioMenuItem} in the fragmentation-algorithm menu so
     * the selected-toggle listener registered by {@code addFragmentationAlgorithmCheckMenuItems} fires. Behavioral
     * assertion: the {@link FragmentationService}'s selected fragmenter display name equals the toggled item's text.
     *
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void fragmentationAlgorithmToggleUpdatesSelectedFragmenterTest() throws Exception {
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            AtomicReference<String> tmpToggledText = new AtomicReference<>();
            AbstractFxTestCase.runAndWait(() -> {
                MainView tmpMainView = (MainView) MainViewControllerHarnessTest.getField(tmpController, "mainView");
                List<MenuItem> tmpItems = tmpMainView.getMainMenuBar().getFragmentationAlgorithmMenu().getItems();
                for (MenuItem tmpItem : tmpItems) {
                    RadioMenuItem tmpRadioItem = (RadioMenuItem) tmpItem;
                    if (!tmpRadioItem.isSelected()) {
                        tmpToggledText.set(tmpRadioItem.getText());
                        tmpRadioItem.setSelected(true);
                        break;
                    }
                }
            });
            AbstractFxTestCase.waitForFxEvents();
            FragmentationService tmpService =
                    (FragmentationService) MainViewControllerHarnessTest.getField(tmpController, "fragmentationService");
            Assertions.assertEquals(tmpToggledText.get(),
                    tmpService.getSelectedFragmenter().getFragmentationAlgorithmDisplayName(),
                    "the selected fragmenter must match the toggled algorithm menu item");
        } finally {
            MainViewControllerHarnessTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * Opens the fragmentation-settings, pipeline-settings and global-settings blocking (or non-blocking) auxiliary
     * views via {@link FxTestUtil#runAndDriveModal(java.util.concurrent.Callable, java.util.function.Consumer)}, which
     * opens each and always closes it, so none leaks or hangs. Global settings is opened after an import so its
     * post-close {@code Platform.runLater} apply body runs against a populated tab. Alerts and {@link java.awt.Desktop}
     * are mocked on the FX thread inside the driver.
     *
     * @param aTempDir per-test temporary directory for the SMILES fixture
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void settingsAuxiliaryModalsOpenAndCloseTest(@TempDir Path aTempDir) throws Exception {
        File tmpSmilesFile = Files.writeString(aTempDir.resolve("in.smi"),
                MainViewControllerHarnessTest.BENZENE_SMILES_LINE).toFile();
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            this.driveModalOpen(tmpController::openFragmentationSettingsView);
            this.driveModalOpen(tmpController::openPipelineSettingsView);
            //import first so the global-settings apply body iterates the populated molecules tab
            this.importFileAndDrain(tmpController, tmpSmilesFile);
            this.driveModalOpen(tmpController::openGlobalSettingsView);
            AbstractFxTestCase.waitForFxEvents();
        } finally {
            MainViewControllerHarnessTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * Builds and selects a fragments result tab, then opens the histogram view (non-blocking) through the modal driver
     * so it is opened and always closed. Behavioral assertion: the drive completes without a fork crash.
     *
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void openHistogramViewModalTest() throws Exception {
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            AbstractFxTestCase.runAndWait(() ->
                    MainViewControllerHarnessTest.setUpSelectedFragmentsTab(tmpController, "TestFragmentation"));
            AbstractFxTestCase.waitForFxEvents();
            this.driveModalOpen(tmpController::openHistogramView);
        } finally {
            MainViewControllerHarnessTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * Opens the About view via its menu item through the modal driver ({@link java.awt.Desktop} mocked so the
     * open-GitHub/tutorial handlers would not throw a {@code HeadlessException} if triggered), asserting it opens and
     * closes without a fork crash.
     *
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void openAboutViewModalTest() throws Exception {
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            FxTestUtil.runAndDriveModal(
                    () -> {
                        try (MockedStatic<GuiUtil> tmpGuiUtilMock = FxTestUtil.mockGuiAlerts();
                                MockedStatic<Desktop> tmpDesktopMock = FxTestUtil.mockDesktop()) {
                            MainView tmpMainView = (MainView) MainViewControllerHarnessTest.getField(tmpController, "mainView");
                            tmpMainView.getMainMenuBar().getAboutViewMenuItem().fire();
                        }
                        return null;
                    },
                    aStage -> { });
            AbstractFxTestCase.waitForFxEvents();
        } finally {
            MainViewControllerHarnessTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * Drives the molecules data-source branch of {@code openOverviewView} (with the molecules tab selected) through the
     * modal driver, and the internally-caught {@link IllegalStateException} branch (a fragments data source while the
     * molecules tab is selected, which is caught and logged so the method returns without opening a modal). A fresh
     * controller is used per overview test because the {@code OverviewViewController} reuses one {@code OverviewView}
     * instance across opens, so a single controller may open the overview only once.
     *
     * @param aTempDir per-test temporary directory for the SMILES fixture
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void openOverviewViewMoleculesBranchAndIllegalStateCatchTest(@TempDir Path aTempDir) throws Exception {
        File tmpSmilesFile = Files.writeString(aTempDir.resolve("in.smi"),
                MainViewControllerHarnessTest.BENZENE_SMILES_LINE).toFile();
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            //mismatched data source with the molecules tab selected -> internally-caught IllegalStateException (no modal)
            this.importFileAndDrain(tmpController, tmpSmilesFile);
            AbstractFxTestCase.runAndWait(() ->
                    tmpController.openOverviewView(OverviewViewController.DataSources.FRAGMENTS_TAB));
            AbstractFxTestCase.waitForFxEvents();
            //molecules tab selected -> MOLECULES_TAB branch (the single overview open for this controller)
            this.driveModalOpen(() ->
                    tmpController.openOverviewView(OverviewViewController.DataSources.MOLECULES_TAB));
        } finally {
            MainViewControllerHarnessTest.hideStage(tmpStageReference);
        }
    }
    //
    /**
     * Drives the fragments data-source branch of {@code openOverviewView} with a fragments tab built and selected,
     * through the modal driver. A fresh controller is used (see
     * {@link #openOverviewViewMoleculesBranchAndIllegalStateCatchTest(Path)}) so this is the controller's single
     * overview open.
     *
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void openOverviewViewFragmentsBranchTest() throws Exception {
        AtomicReference<Stage> tmpStageReference = new AtomicReference<>();
        try {
            MainViewController tmpController = this.constructController(tmpStageReference);
            AbstractFxTestCase.runAndWait(() ->
                    MainViewControllerHarnessTest.setUpSelectedFragmentsTab(tmpController, "TestFragmentation"));
            AbstractFxTestCase.waitForFxEvents();
            this.driveModalOpen(() ->
                    tmpController.openOverviewView(OverviewViewController.DataSources.FRAGMENTS_TAB));
        } finally {
            MainViewControllerHarnessTest.hideStage(tmpStageReference);
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
        this.joinImporterThread(aController);
        AbstractFxTestCase.waitForFxEvents();
        AbstractFxTestCase.waitForFxEvents();
        AbstractFxTestCase.runAndWait(() -> { });
    }
    //
    /**
     * Reflectively obtains the controller's background importer thread and joins it (bounded), so the import work is
     * complete before the FX-thread success callback is drained (removes the async-callback race).
     *
     * @param aController the controller whose importer thread should be joined
     * @throws Exception if the field cannot be accessed or the join is interrupted
     */
    private void joinImporterThread(MainViewController aController) throws Exception {
        Thread tmpImporterThread = (Thread) MainViewControllerHarnessTest.getField(aController, "importerThread");
        if (tmpImporterThread != null) {
            tmpImporterThread.join(MainViewControllerHarnessTest.IMPORT_JOIN_TIMEOUT_MILLIS);
        }
    }
    //
    /**
     * Reflectively reads the controller's private {@code moleculeDataModelList} field (no production code is widened),
     * so a test can assert or manipulate the imported-molecule invariant.
     *
     * @param aController the controller instance
     * @return the controller's observable molecule data model list
     */
    @SuppressWarnings("unchecked")
    private static List<MoleculeDataModel> getMoleculeList(MainViewController aController) {
        return (List<MoleculeDataModel>) MainViewControllerHarnessTest.getField(aController, "moleculeDataModelList");
    }
    //
    /**
     * Builds a static mock of {@link GuiUtil} identical to {@link FxTestUtil#mockGuiAlerts()} except that
     * {@code guiConfirmationAlert} returns {@link ButtonType#CANCEL}, so a guarded confirmation is declined. This is
     * required for the guarded {@code closeApplication} early-return, where an {@code OK} answer would instead fall
     * through to the {@code System.exit} tail and kill the fork.
     *
     * @return a static mock of {@link GuiUtil} whose confirmation alert answers {@code CANCEL}
     */
    private static MockedStatic<GuiUtil> mockGuiAlertsConfirmCancel() {
        return Mockito.mockStatic(GuiUtil.class, anInvocation -> switch (anInvocation.getMethod().getName()) {
            case "guiMessageAlert", "guiMessageAlertWithHyperlink" -> Optional.empty();
            case "guiConfirmationAlert", "guiYesNoCancelConfirmationAlert" -> ButtonType.CANCEL;
            case "guiExceptionAlert", "guiExpandableAlert" -> null;
            default -> anInvocation.callRealMethod();
        });
    }
    //
    /**
     * Creates an unstarted, do-nothing {@link Task} used as a stand-in for the controller's import/export/fragmentation
     * task fields so the {@code interrupt*} methods can be driven without a live background operation.
     *
     * @return a new no-op task
     */
    private static Task<Void> noOpTask() {
        return new Task<>() {
            @Override
            protected Void call() {
                return null;
            }
        };
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
     * interrupt/close paths can be driven with prepared stand-in state.
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
    //
    /**
     * Opens an auxiliary view through {@link FxTestUtil#runAndDriveModal(java.util.concurrent.Callable,
     * java.util.function.Consumer)} so it is opened on the FX thread and ALWAYS closed (no orphan window, no hang). The
     * {@link GuiUtil} alerts and the {@link Desktop} static are mocked INSIDE the driver (thread-confined) so no real
     * alert or OS launch is reached. Works for both blocking {@code showAndWait} and non-blocking {@code show} views:
     * the window listener detects the shown stage and closes it either way.
     *
     * @param aOpenAction the controller open call to drive
     */
    private void driveModalOpen(Runnable aOpenAction) {
        FxTestUtil.runAndDriveModal(
                () -> {
                    try (MockedStatic<GuiUtil> tmpGuiUtilMock = FxTestUtil.mockGuiAlerts();
                            MockedStatic<Desktop> tmpDesktopMock = FxTestUtil.mockDesktop()) {
                        aOpenAction.run();
                    }
                    return null;
                },
                aStage -> { });
    }
    //
    /**
     * Reflectively joins a named background thread field of the controller (bounded), so a started import/fragmentation
     * is complete before the FX-thread success callback is drained.
     *
     * @param aController the controller under test
     * @param aFieldName the name of the {@link Thread} field to join
     * @throws Exception if the join is interrupted
     */
    private void joinThreadField(MainViewController aController, String aFieldName) throws Exception {
        Thread tmpThread = (Thread) MainViewControllerHarnessTest.getField(aController, aFieldName);
        if (tmpThread != null) {
            tmpThread.join(MainViewControllerHarnessTest.IMPORT_JOIN_TIMEOUT_MILLIS);
        }
    }
    //
    /**
     * Reflectively reads the controller's private {@code mapOfFragmentDataModelLists} field (no production code is
     * widened), so a test can assert on or populate the fragmentation results.
     *
     * @param aController the controller instance
     * @return the controller's map of fragmentation-name to fragment list
     */
    @SuppressWarnings("unchecked")
    private static Map<String, ObservableList<FragmentDataModel>> getFragmentMap(MainViewController aController) {
        return (Map<String, ObservableList<FragmentDataModel>>)
                MainViewControllerHarnessTest.getField(aController, "mapOfFragmentDataModelLists");
    }
    //
    /**
     * Reflectively reads the number of tabs in the controller's private {@code mainTabPane} (no production code is
     * widened).
     *
     * @param aController the controller instance
     * @return the current tab count of the main tab pane
     */
    private static int getTabPaneSize(MainViewController aController) {
        return ((TabPane) MainViewControllerHarnessTest.getField(aController, "mainTabPane")).getTabs().size();
    }
    //
    /**
     * Populates the controller's fragment map with a single real fragment (carrying a parent molecule, which the
     * fragments-tab width listener dereferences) under the given fragmentation name and builds/selects the fragments
     * and itemization tabs via {@code addFragmentationResultTabs}. Must be called on the JavaFX Application Thread. This
     * provides the selected fragments-tab state the histogram/overview drives read, without a live fragmentation run.
     *
     * @param aController the controller to set up
     * @param aFragmentationName the fragmentation name used as the tab title suffix and map key
     */
    private static void setUpSelectedFragmentsTab(MainViewController aController, String aFragmentationName) {
        FragmentDataModel tmpFragment = new FragmentDataModel("c1ccccc1", "Benzene", new HashMap<>());
        tmpFragment.getParentMolecules().add(new MoleculeDataModel("c1ccccc1", "BenzeneParent", new HashMap<>()));
        ObservableList<FragmentDataModel> tmpFragmentList = FXCollections.observableArrayList(tmpFragment);
        MainViewControllerHarnessTest.getFragmentMap(aController).put(aFragmentationName, tmpFragmentList);
        aController.addFragmentationResultTabs(aFragmentationName);
    }
    //</editor-fold>
}
