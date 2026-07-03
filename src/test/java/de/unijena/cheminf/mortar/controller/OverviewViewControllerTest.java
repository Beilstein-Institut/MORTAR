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

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.Property;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

/**
 * Direct, headless unit tests for {@link OverviewViewController}. The controller is constructed without a Stage
 * (its constructor only builds two {@code SimpleIntegerProperty} settings and a {@code ScheduledThreadPoolExecutor},
 * touching no scene graph), so no JavaFX toolkit boot is required and this class deliberately extends no FX harness.
 * <p>
 * The primary purpose is a characterization pin on the two pure pagination-math methods
 * {@code calculateMaxColumnsPerPage(double)} and {@code calculateMaxRowsPerPage(double)}, which were widened from
 * private to package-private (RFCT-01) so they can be exercised directly here. The expected results are hard-coded
 * integer literals derived from the documented arithmetic and the public grid constants, so any future edit to the
 * formula or a constant is caught. The guard branch (IllegalArgumentException on a parameter {@literal <=} zero) is
 * pinned for both methods. A handful of other headless-reachable members are exercised for partial coverage.
 * <p>
 * This plan intentionally does NOT chase {@literal >=}80% line coverage on the controller: the Stage/Scene/GridPane
 * remainder is deferred to Phase 15 (COV-02). Real objects only, no mocks.
 *
 * @author Felix Baensch
 * @version 1.0.0.0
 */
public class OverviewViewControllerTest {
    //<editor-fold desc="Constructor" defaultstate="collapsed">
    /**
     * Constructor that sets the default locale to en-GB (so the setting names resolved from the message bundle during
     * controller instantiation are deterministic) and bootstraps the Configuration singleton from the classpath (the
     * controller reads config; no data directory is touched by this).
     *
     * @throws Exception if the Configuration singleton cannot be initialized
     */
    public OverviewViewControllerTest() throws Exception {
        Locale.setDefault(Locale.of("en", "GB"));
        Configuration.getInstance();
    }
    //</editor-fold>
    //
    //<editor-fold desc="Characterization test methods for pagination math" defaultstate="collapsed">
    /**
     * Characterization pin for {@link OverviewViewController#calculateMaxColumnsPerPage(double)}. Asserts the guard
     * throws {@link IllegalArgumentException} for a zero and a negative width, and pins two happy-path results.
     * The expected values follow the documented formula
     * {@code (int) ((width - (2 * GUI_INSETS_VALUE - GRIDLINES_WIDTH)) / (IMAGE_MIN_WIDTH + GRIDLINES_WIDTH))}
     * = {@code (int) ((width - 12.0) / 38.0)}: width 800.0 -> 20, width 200.0 -> 4.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void calculateMaxColumnsPerPageCharacterizationTest() throws Exception {
        OverviewViewController tmpController = new OverviewViewController(Configuration.getInstance());
        //guard branch: parameter <= 0.0 must throw
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tmpController.calculateMaxColumnsPerPage(0.0));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tmpController.calculateMaxColumnsPerPage(-5.0));
        //happy path: (int) ((width - 12.0) / 38.0)
        Assertions.assertEquals(20, tmpController.calculateMaxColumnsPerPage(800.0));
        Assertions.assertEquals(4, tmpController.calculateMaxColumnsPerPage(200.0));
    }
    //
    /**
     * Characterization pin for {@link OverviewViewController#calculateMaxRowsPerPage(double)}. Asserts the guard
     * throws {@link IllegalArgumentException} for a zero and a negative height, and pins two happy-path results.
     * The expected values follow the documented formula
     * {@code (int) ((height - GUI_PAGINATION_CONTROL_PANEL_HEIGHT - GRIDLINES_WIDTH) / (IMAGE_MIN_HEIGHT + GRIDLINES_WIDTH))}
     * = {@code (int) ((height - 53.0) / 28.0)}: height 600.0 -> 19, height 200.0 -> 5.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void calculateMaxRowsPerPageCharacterizationTest() throws Exception {
        OverviewViewController tmpController = new OverviewViewController(Configuration.getInstance());
        //guard branch: parameter <= 0.0 must throw
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tmpController.calculateMaxRowsPerPage(0.0));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tmpController.calculateMaxRowsPerPage(-5.0));
        //happy path: (int) ((height - 53.0) / 28.0)
        Assertions.assertEquals(19, tmpController.calculateMaxRowsPerPage(600.0));
        Assertions.assertEquals(5, tmpController.calculateMaxRowsPerPage(200.0));
    }
    //</editor-fold>
    //
    //<editor-fold desc="Headless partial-coverage test methods" defaultstate="collapsed">
    /**
     * Exercises the headless-reachable IViewToolController members: {@code settingsProperties()} exposes exactly the
     * two page-size settings, {@code getViewToolNameForDisplay()} returns a non-blank name, {@code canBeUsedOnTab(...)}
     * is true for the molecules and fragments tabs and false for the itemization tab, and {@code restoreDefaultSettings()}
     * resets both settings to their documented defaults.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void headlessViewToolMembersTest() throws Exception {
        OverviewViewController tmpController = new OverviewViewController(Configuration.getInstance());
        List<Property<?>> tmpSettings = tmpController.settingsProperties();
        Assertions.assertNotNull(tmpSettings);
        Assertions.assertEquals(2, tmpSettings.size());
        Assertions.assertNotNull(tmpController.getViewToolNameForDisplay());
        Assertions.assertFalse(tmpController.getViewToolNameForDisplay().isBlank());
        Assertions.assertTrue(tmpController.canBeUsedOnTab(TabNames.MOLECULES));
        Assertions.assertTrue(tmpController.canBeUsedOnTab(TabNames.FRAGMENTS));
        Assertions.assertFalse(tmpController.canBeUsedOnTab(TabNames.ITEMIZATION));
        //mutate then restore: defaults are 5 rows / 5 columns per page
        IntegerProperty tmpRowsPerPageSetting = (IntegerProperty) tmpSettings.get(0);
        IntegerProperty tmpColumnsPerPageSetting = (IntegerProperty) tmpSettings.get(1);
        tmpRowsPerPageSetting.set(3);
        tmpColumnsPerPageSetting.set(7);
        tmpController.restoreDefaultSettings();
        Assertions.assertEquals(
                OverviewViewController.OVERVIEW_VIEW_STRUCTURE_GRID_PANE_ROWS_PER_PAGE_DEFAULT,
                tmpRowsPerPageSetting.get());
        Assertions.assertEquals(
                OverviewViewController.OVERVIEW_VIEW_STRUCTURE_GRID_PANE_COLUMNS_PER_PAGE_DEFAULT,
                tmpColumnsPerPageSetting.get());
    }
    //
    /**
     * Exercises the cached-structure-index accessors on a fresh, headless controller: the index reads back as -1 (no
     * return-to-structure event has occurred) and the reset keeps it at -1.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void cachedIndexAccessorsTest() throws Exception {
        OverviewViewController tmpController = new OverviewViewController(Configuration.getInstance());
        Assertions.assertEquals(-1, tmpController.getCachedIndexOfStructureInMoleculeDataModelList());
        tmpController.resetCachedIndexOfStructureInMoleculeDataModelList();
        Assertions.assertEquals(-1, tmpController.getCachedIndexOfStructureInMoleculeDataModelList());
    }
    //</editor-fold>
}
