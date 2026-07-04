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
import de.unijena.cheminf.mortar.gui.views.HistogramView;
import de.unijena.cheminf.mortar.model.data.FragmentDataModel;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Headless coverage tests for the Stage-dependent residual of {@link HistogramViewController} (COV-03). This is the
 * additive counterpart to the Phase 14 characterization pins in {@code HistogramViewControllerTest} (which pin the pure
 * bar-spacing/axis/enum/abbreviation logic and the toolkit-free {@code IViewToolController} members and are left
 * completely untouched here); JaCoCo aggregates both classes, so this class only needs to drive the parts a headless
 * run could not reach before: the whole {@code openHistogramView} flow, {@code createHistogram} (including the per-bar
 * {@code StackPane}/label builders and their hover/context listeners), the apply/checkbox/scene-resize listeners in
 * {@code addListenersToHistogramView}, and both close paths ({@code closeWindowEvent} and the close button).
 * <p>
 * Unlike the overview view, {@code openHistogramView} ends in the NON-BLOCKING {@code histogramStage.show()}
 * ({@code HistogramViewController.java:640}), so the view is opened with a plain
 * {@link AbstractFxTestCase#runAndWait(Runnable)} followed by {@link AbstractFxTestCase#waitForFxEvents()} to let the
 * chart build and the listeners register. None of the exercised flows reaches a modal alert EXCEPT the apply handler's
 * over-count warning: that warning is a blocking {@link javafx.scene.control.Alert#showAndWait()}, so it is driven to
 * completion through {@link FxTestUtil#runAndDriveModal(java.util.concurrent.Callable, java.util.function.Consumer)},
 * which detects the alert's modal stage and closes it (a static {@code GuiUtil} mock is intentionally NOT used, because
 * a Mockito static mock can only be created inside a pumping nested event loop on the JavaFX Application Thread, which
 * the non-blocking open does not provide). Each test always closes the opened stage so no window leaks into a sibling
 * test. Private state that production never widens (the {@code histogramView} and {@code histogramStage}) is reached via
 * reflection, mirroring the harness's own {@code FileUtil.appDirPath} reflection. Assertions are behavioral only (a
 * {@link BarChart} is present, its series holds the expected number of bars, the drive completes without an exception)
 * and never pin exact CDK-derived category strings, since CDK 2.12 is a moving snapshot. No production code is modified.
 *
 * @author Felix Baensch
 * @version 1.0.0.0
 */
public class HistogramViewControllerHarnessTest extends AbstractFxTestCase {
    //<editor-fold desc="Private static final class constants" defaultstate="collapsed">
    /**
     * Valid SMILES codes used to build the fragment list driven into the histogram view.
     */
    private static final String[] SMILES_POOL = {"c1ccccc1", "CCO", "CCC", "CCN", "c1ccncc1"};
    //</editor-fold>
    //
    //<editor-fold desc="Constructor" defaultstate="collapsed">
    /**
     * Default no-argument constructor; all headless setup (toolkit boot, en-GB locale, {@code user.home} isolation) is
     * inherited from {@link AbstractFxTestCase}.
     */
    public HistogramViewControllerHarnessTest() {
    }
    //</editor-fold>
    //
    //<editor-fold desc="Test methods" defaultstate="collapsed">
    /**
     * Opens the histogram view non-blocking and asserts the bar chart was built into the view's scroll pane with one
     * series holding a bar per displayed fragment, then exercises a rendered bar's hover and context-menu listeners and
     * closes the view via the close button. Covers {@code openHistogramView}, {@code createHistogram} (including the
     * per-bar builders), and the close-button path.
     *
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void openBuildsChartAndCloseButtonClosesTest() throws Exception {
        List<FragmentDataModel> tmpFragments = HistogramViewControllerHarnessTest.buildFragments(5);
        HistogramViewController tmpController = new HistogramViewController(Configuration.getInstance());
        this.openHistogram(tmpController, tmpFragments);
        HistogramView tmpView = (HistogramView) HistogramViewControllerHarnessTest.getField(tmpController, "histogramView");
        Assertions.assertNotNull(tmpView, "the histogram view must be initialized after opening");
        Object tmpContent = tmpView.getHistogramScrollPane().getContent();
        Assertions.assertInstanceOf(BarChart.class, tmpContent, "the scroll pane must hold the bar chart after opening");
        BarChart<?, ?> tmpBarChart = (BarChart<?, ?>) tmpContent;
        Assertions.assertEquals(1, tmpBarChart.getData().size(), "the histogram must hold exactly one data series");
        Assertions.assertEquals(tmpFragments.size(), tmpBarChart.getData().get(0).getData().size(),
                "the series must hold one bar per displayed fragment");
        //exercise a rendered bar's hover and context-menu listeners, then close via the close button
        AbstractFxTestCase.runAndWait(() -> {
            HistogramViewControllerHarnessTest.fireFirstBarInteractions(tmpBarChart);
            tmpView.getCloseButton().fire();
        });
        AbstractFxTestCase.waitForFxEvents();
    }
    //
    /**
     * Opens the histogram view, changes every setting control (displayed-fragment number, maximum SMILES length, bar
     * width, frequency option) and fires the apply button so the chart is rebuilt, toggles the grid-lines, SMILES,
     * bar-labels and bar-shadows checkboxes so their listeners run, resizes the scene so the image-size resize listeners
     * run, and finally closes the view through a fired {@code WINDOW_CLOSE_REQUEST} (the close-request event filter and
     * {@code closeWindowEvent}). Behavioral assertion: after the apply, the series holds the reduced number of bars.
     *
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void applyAndSettingListenersRebuildChartThenCloseRequestTest() throws Exception {
        List<FragmentDataModel> tmpFragments = HistogramViewControllerHarnessTest.buildFragments(5);
        HistogramViewController tmpController = new HistogramViewController(Configuration.getInstance());
        this.openHistogram(tmpController, tmpFragments);
        HistogramView tmpView = (HistogramView) HistogramViewControllerHarnessTest.getField(tmpController, "histogramView");
        AbstractFxTestCase.runAndWait(() -> {
            //toggle the checkbox-backed settings so their listeners fire
            tmpView.getDisplayGridLinesCheckBox().setSelected(!tmpView.getDisplayGridLinesCheckBox().isSelected());
            tmpView.getDisplaySmilesOnYAxisCheckBox().setSelected(!tmpView.getDisplaySmilesOnYAxisCheckBox().isSelected());
            tmpView.getDisplayBarLabelsCheckBox().setSelected(!tmpView.getDisplayBarLabelsCheckBox().isSelected());
            tmpView.getDisplayBarShadowsCheckBox().setSelected(!tmpView.getDisplayBarShadowsCheckBox().isSelected());
            //change the combo-box and text-field backed settings, then apply to rebuild the chart with fewer bars (a
            // displayed-fragment count of 3 is <= the 5 available fragments, so no over-count warning alert is reached)
            tmpView.getBarWidthsComboBox().setValue(HistogramViewController.BarWidthOption.SMALL.getDisplayName());
            tmpView.getFrequencyComboBox().setValue(HistogramViewController.FrequencyOption.MOLECULE_FREQUENCY.getDisplayName());
            tmpView.getMaximumSMILESLengthTextField().setText("5");
            tmpView.getDisplayedFragmentsNumberTextField().setText("3");
            tmpView.getApplyButton().fire();
        });
        AbstractFxTestCase.waitForFxEvents();
        //resize the scene so the width/height listeners recompute the image dimensions
        AbstractFxTestCase.runAndWait(() -> {
            Stage tmpStage = (Stage) HistogramViewControllerHarnessTest.getField(tmpController, "histogramStage");
            tmpStage.setWidth(tmpStage.getWidth() + 120.0);
            tmpStage.setHeight(tmpStage.getHeight() + 120.0);
        });
        AbstractFxTestCase.waitForFxEvents();
        Object tmpContent = tmpView.getHistogramScrollPane().getContent();
        BarChart<?, ?> tmpBarChart = (BarChart<?, ?>) tmpContent;
        Assertions.assertEquals(3, tmpBarChart.getData().get(0).getData().size(),
                "after applying a displayed-fragment number of 3 the series must hold three bars");
        //close via a fired window-close request to exercise the close-request event filter and closeWindowEvent
        AbstractFxTestCase.runAndWait(() -> {
            Stage tmpStage = (Stage) HistogramViewControllerHarnessTest.getField(tmpController, "histogramStage");
            tmpStage.fireEvent(new WindowEvent(tmpStage, WindowEvent.WINDOW_CLOSE_REQUEST));
        });
        AbstractFxTestCase.waitForFxEvents();
    }
    //
    /**
     * Opens the histogram view and fires the apply button with a displayed-fragment number larger than the number of
     * available fragments, exercising the apply handler's over-count warning branch. That branch shows a blocking
     * warning {@link javafx.scene.control.Alert}, so the apply is fired through
     * {@link FxTestUtil#runAndDriveModal(java.util.concurrent.Callable, java.util.function.Consumer)} which detects and
     * closes the alert's modal stage. Behavioral assertion: the chart is still present afterwards (the warning path
     * returns without a rebuild). The view is then closed via the close button.
     *
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void applyWithTooManyFragmentsHitsWarningBranchTest() throws Exception {
        List<FragmentDataModel> tmpFragments = HistogramViewControllerHarnessTest.buildFragments(5);
        HistogramViewController tmpController = new HistogramViewController(Configuration.getInstance());
        this.openHistogram(tmpController, tmpFragments);
        HistogramView tmpView = (HistogramView) HistogramViewControllerHarnessTest.getField(tmpController, "histogramView");
        //fire the over-count apply through the modal driver: the warning Alert.showAndWait is detected and closed
        FxTestUtil.runAndDriveModal(
                () -> {
                    tmpView.getMaximumSMILESLengthTextField().setText("10");
                    tmpView.getDisplayedFragmentsNumberTextField().setText("99");
                    tmpView.getApplyButton().fire();
                    return null;
                },
                aAlertStage -> {
                    //no interaction needed; the helper's finally closes the warning alert's stage
                });
        AbstractFxTestCase.waitForFxEvents();
        Assertions.assertNotNull(tmpView.getHistogramScrollPane().getContent(),
                "the chart must still be present after an over-count apply that was rejected");
        //close the histogram view
        AbstractFxTestCase.runAndWait(() -> {
            Stage tmpStage = (Stage) HistogramViewControllerHarnessTest.getField(tmpController, "histogramStage");
            tmpStage.fireEvent(new WindowEvent(tmpStage, WindowEvent.WINDOW_CLOSE_REQUEST));
        });
        AbstractFxTestCase.waitForFxEvents();
    }
    //
    /**
     * Pins the null-argument guards of {@code openHistogramView}: a null main stage and a null fragment list each throw
     * {@link NullPointerException} before the view is built, so the guards are exercised on the FX thread without opening
     * any stage.
     *
     * @throws Exception if anything goes wrong on the FX thread
     */
    @Test
    public void openHistogramViewNullArgumentsThrowTest() throws Exception {
        Configuration tmpConfiguration = Configuration.getInstance();
        List<FragmentDataModel> tmpFragments = HistogramViewControllerHarnessTest.buildFragments(2);
        AtomicBoolean tmpNullStageThrew = new AtomicBoolean(false);
        AtomicBoolean tmpNullListThrew = new AtomicBoolean(false);
        AbstractFxTestCase.runAndWait(() -> {
            HistogramViewController tmpController = new HistogramViewController(tmpConfiguration);
            try {
                tmpController.openHistogramView(null, tmpFragments);
            } catch (NullPointerException anException) {
                tmpNullStageThrew.set(true);
            }
            try {
                tmpController.openHistogramView(FxTestUtil.newOffscreenStage(), null);
            } catch (NullPointerException anException) {
                tmpNullListThrew.set(true);
            }
        });
        Assertions.assertTrue(tmpNullStageThrew.get(), "a null main stage must throw NullPointerException");
        Assertions.assertTrue(tmpNullListThrew.get(), "a null fragment list must throw NullPointerException");
    }
    //</editor-fold>
    //
    //<editor-fold desc="Private helper methods" defaultstate="collapsed">
    /**
     * Opens the histogram view for the given controller and fragment list on the JavaFX Application Thread and drains
     * the FX event queue so the chart is built and the listeners are registered before the caller asserts. No static
     * {@code GuiUtil} mock is used because the open flow reaches no modal alert.
     *
     * @param aController the controller under test
     * @param aFragments the fragment data models to visualize
     * @throws Exception if anything goes wrong on the FX thread
     */
    private void openHistogram(HistogramViewController aController, List<FragmentDataModel> aFragments) throws Exception {
        AbstractFxTestCase.runAndWait(() -> aController.openHistogramView(FxTestUtil.newOffscreenStage(), aFragments));
        AbstractFxTestCase.waitForFxEvents();
    }
    //
    /**
     * Builds a list of the given number of {@link FragmentDataModel} instances from a small pool of valid SMILES codes,
     * each with a distinct absolute and molecule frequency so the chart's frequency sorting and maximum-frequency logic
     * have meaningful data.
     *
     * @param aCount number of fragment data models to build
     * @return a mutable list of fragment data models
     */
    private static List<FragmentDataModel> buildFragments(int aCount) {
        List<FragmentDataModel> tmpList = new ArrayList<>(aCount);
        for (int i = 0; i < aCount; i++) {
            String tmpSmiles = HistogramViewControllerHarnessTest.SMILES_POOL[i % HistogramViewControllerHarnessTest.SMILES_POOL.length];
            FragmentDataModel tmpFragment = new FragmentDataModel(tmpSmiles, "Fragment" + i, new HashMap<>());
            tmpFragment.setAbsoluteFrequency((i + 1) * 10);
            tmpFragment.setMoleculeFrequency(i + 1);
            tmpList.add(tmpFragment);
        }
        return tmpList;
    }
    //
    /**
     * Fires a mouse-entered, mouse-exited and context-menu-requested event at the {@link StackPane} node of the first
     * bar of the given chart's first series, so the per-bar hover-structure-display and context-menu listeners added in
     * {@code createStackPaneWithContextMenuAndStructureDisplayForBar} are exercised. Any failure of a popup/clipboard
     * operation on a headless host is swallowed so it cannot abort the drive; the handler bodies up to that point are
     * still covered. Does nothing if the first bar has no node.
     *
     * @param aBarChart the bar chart whose first bar is interacted with
     */
    private static void fireFirstBarInteractions(BarChart<?, ?> aBarChart) {
        if (aBarChart.getData().isEmpty() || aBarChart.getData().get(0).getData().isEmpty()) {
            return;
        }
        XYChart.Data<?, ?> tmpFirstData = aBarChart.getData().get(0).getData().get(0);
        Node tmpBarNode = tmpFirstData.getNode();
        if (!(tmpBarNode instanceof StackPane)) {
            return;
        }
        try {
            MouseEvent tmpEnter = new MouseEvent(MouseEvent.MOUSE_ENTERED, 1.0, 1.0, 1.0, 1.0, MouseButton.PRIMARY, 0,
                    false, false, false, false, true, false, false, true, false, false, null);
            Event.fireEvent(tmpBarNode, tmpEnter);
            MouseEvent tmpExit = new MouseEvent(MouseEvent.MOUSE_EXITED, 1.0, 1.0, 1.0, 1.0, MouseButton.PRIMARY, 0,
                    false, false, false, false, true, false, false, true, false, false, null);
            Event.fireEvent(tmpBarNode, tmpExit);
            ContextMenuEvent tmpContextMenuEvent = new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                    1.0, 1.0, 1.0, 1.0, false, null);
            Event.fireEvent(tmpBarNode, tmpContextMenuEvent);
        } catch (Throwable anIgnoredHeadlessFailure) {
            //a popup/clipboard operation may fail on a headless host; the handler bodies up to that point are still
            //exercised and the failure must not abort the drive
        }
    }
    //
    /**
     * Reflectively reads the value of a private field of the given controller.
     *
     * @param aController the controller under test
     * @param aFieldName the name of the field to read
     * @return the current field value
     */
    private static Object getField(HistogramViewController aController, String aFieldName) {
        try {
            Field tmpField = HistogramViewController.class.getDeclaredField(aFieldName);
            tmpField.setAccessible(true);
            return tmpField.get(aController);
        } catch (ReflectiveOperationException anException) {
            throw new RuntimeException("Could not read field " + aFieldName + " via reflection", anException);
        }
    }
    //</editor-fold>
}
