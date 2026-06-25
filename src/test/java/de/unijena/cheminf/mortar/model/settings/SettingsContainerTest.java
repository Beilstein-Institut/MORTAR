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

package de.unijena.cheminf.mortar.model.settings;

import de.unijena.cheminf.mortar.configuration.Configuration;
import de.unijena.cheminf.mortar.gui.util.GuiUtil;
import de.unijena.cheminf.mortar.model.io.Exporter;
import de.unijena.cheminf.mortar.model.util.FileUtil;

import javafx.beans.property.Property;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.logging.LogManager;

/**
 * Test class for testing and demonstrating the usage of SettingsContainer class.
 *
 * @author Jonas Schaub
 * @version 1.0.0.0
 */
public class SettingsContainerTest {
    //<editor-fold desc="Constructor" defaultstate="collapsed">
    /**
     * Constructor to initialize locale and configuration.
     *
     * @throws Exception if anything goes wrong
     */
    public SettingsContainerTest() throws Exception {
        Locale.setDefault(Locale.of("en", "GB"));
        Configuration.getInstance();
    }
    //</editor-fold>
    //
    //<editor-fold desc="Test methods" defaultstate="collapsed">
    /**
     * Tests the basic functionalities of SettingsContainer. These are instantiation, restoring default settings,
     * getting the settings, changing the settings, persisting the settings, and reloading them. This test is isolated
     * via a temporary user.home so it does not write to the real ~/MORTAR/Settings directory.
     *
     * @param aTempHome JUnit-managed temporary directory used as a redirected user.home
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testSettingsContainerBasics(@TempDir Path aTempHome) throws Exception {
        String tmpOldHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", aTempHome.toString());
            this.resetAppDirPathCache();
            Exporter.CSVSeparator tmpCsvExportSeparatorTest = Exporter.CSVSeparator.COMMA;
            //if there is a persisted settings container file already on the machine, it is loaded into the new SettingsContainer object
            SettingsContainer tmpSettingsContainer = new SettingsContainer();
            //restoring to default because a previous settings file with altered settings may have been imported (see below)
            tmpSettingsContainer.restoreDefaultSettings();
            List<Property<?>> tmpPropertiesList = tmpSettingsContainer.settingsProperties();
            for (Property<?> tmpProp : tmpPropertiesList) {
                Assertions.assertNotNull(tmpProp.getName());
                Assertions.assertNotNull(tmpProp.getValue());
            }
            Assertions.assertNotNull(tmpSettingsContainer.recentDirectoryPathSettingProperty().getName());
            Assertions.assertNotNull(tmpSettingsContainer.recentDirectoryPathSettingProperty().getValue());
            Assertions.assertEquals(SettingsContainer.ROWS_PER_PAGE_SETTING_DEFAULT, tmpSettingsContainer.getRowsPerPageSetting());
            Assertions.assertEquals(SettingsContainer.ADD_IMPLICIT_HYDROGENS_AT_IMPORT_SETTING_DEFAULT, tmpSettingsContainer.getAddImplicitHydrogensAtImportSetting());
            Assertions.assertEquals(tmpSettingsContainer.getNumberOfTasksForFragmentationSettingDefault(), tmpSettingsContainer.getNumberOfTasksForFragmentationSetting());
            Assertions.assertEquals(SettingsContainer.RECENT_DIRECTORY_PATH_SETTING_DEFAULT, tmpSettingsContainer.getRecentDirectoryPathSetting());
            Assertions.assertEquals(SettingsContainer.ALWAYS_MDLV3000_FORMAT_AT_EXPORT_SETTING_DEFAULT, tmpSettingsContainer.getAlwaysMDLV3000FormatAtExportSetting());
            Assertions.assertEquals(SettingsContainer.CSV_EXPORT_SEPARATOR_SETTING_DEFAULT, tmpSettingsContainer.getCsvExportSeparatorSetting());
            tmpSettingsContainer.setRowsPerPageSetting(SettingsContainer.ROWS_PER_PAGE_SETTING_DEFAULT + 5);
            tmpSettingsContainer.setAddImplicitHydrogensAtImportSetting(!SettingsContainer.ADD_IMPLICIT_HYDROGENS_AT_IMPORT_SETTING_DEFAULT);
            tmpSettingsContainer.setAlwaysMDLV3000FormatAtExportSetting(!SettingsContainer.ALWAYS_MDLV3000_FORMAT_AT_EXPORT_SETTING_DEFAULT);
            tmpSettingsContainer.setCsvExportSeparatorSetting(tmpCsvExportSeparatorTest);
            //persisting the settings container
            tmpSettingsContainer.preserveSettings();
            //reload persisted container
            SettingsContainer tmpSecondContainer = new SettingsContainer();
            tmpSecondContainer.reloadGlobalSettings();
            Assertions.assertEquals(SettingsContainer.ROWS_PER_PAGE_SETTING_DEFAULT + 5, tmpSecondContainer.getRowsPerPageSetting());
            Assertions.assertEquals(!SettingsContainer.ADD_IMPLICIT_HYDROGENS_AT_IMPORT_SETTING_DEFAULT, tmpSecondContainer.getAddImplicitHydrogensAtImportSetting());
            Assertions.assertEquals(!SettingsContainer.ALWAYS_MDLV3000_FORMAT_AT_EXPORT_SETTING_DEFAULT, tmpSettingsContainer.getAlwaysMDLV3000FormatAtExportSetting());
            Assertions.assertEquals(tmpCsvExportSeparatorTest, tmpSecondContainer.getCsvExportSeparatorSetting());
            tmpSecondContainer.restoreDefaultSettings();
            tmpSecondContainer.preserveSettings();
        } finally {
            System.setProperty("user.home", tmpOldHome);
            this.resetAppDirPathCache();
            LogManager.getLogManager().reset();
        }
    }

    /**
     * Tests that every trivial JavaFX property accessor returns a non-null property (Group A). No filesystem or GUI
     * dependency is involved.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testPropertyAccessorsAreNonNull() throws Exception {
        SettingsContainer tmpSettingsContainer = new SettingsContainer();
        tmpSettingsContainer.restoreDefaultSettings();
        Assertions.assertNotNull(tmpSettingsContainer.rowsPerPageSettingProperty());
        Assertions.assertNotNull(tmpSettingsContainer.numberOfTasksForFragmentationSettingProperty());
        Assertions.assertNotNull(tmpSettingsContainer.addImplicitHydrogensAtImportSettingProperty());
        Assertions.assertNotNull(tmpSettingsContainer.alwaysMDLV3000FormatAtExportSettingProperty());
        Assertions.assertNotNull(tmpSettingsContainer.csvExportSeparatorSettingProperty());
        Assertions.assertNotNull(tmpSettingsContainer.regardStereochemistrySettingProperty());
    }

    /**
     * Tests that the display-name and tooltip-text maps are non-null (Group A). No filesystem or GUI dependency is
     * involved.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testSettingNameMapsAreNonNull() throws Exception {
        SettingsContainer tmpSettingsContainer = new SettingsContainer();
        tmpSettingsContainer.restoreDefaultSettings();
        Assertions.assertNotNull(tmpSettingsContainer.getSettingNameToTooltipTextMap());
        Assertions.assertNotNull(tmpSettingsContainer.getSettingNameToDisplayNameMap());
    }

    /**
     * Tests that the csv export separator character accessor returns the default separator's character (Group A).
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testCsvExportSeparatorSettingCharacterMatchesDefault() throws Exception {
        SettingsContainer tmpSettingsContainer = new SettingsContainer();
        tmpSettingsContainer.restoreDefaultSettings();
        Assertions.assertEquals(SettingsContainer.CSV_EXPORT_SEPARATOR_SETTING_DEFAULT.getSeparatorChar(),
                tmpSettingsContainer.getCsvExportSeparatorSettingCharacter());
    }

    /**
     * Tests the valid setters by round-tripping legal values through the getters (Group B). No filesystem or GUI
     * dependency is involved; the recent-directory path uses an existing directory so the validator's existence check
     * passes.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testValidSettersRoundTrip() throws Exception {
        SettingsContainer tmpSettingsContainer = new SettingsContainer();
        tmpSettingsContainer.restoreDefaultSettings();
        tmpSettingsContainer.setNumberOfTasksForFragmentationSetting(1);
        Assertions.assertEquals(1, tmpSettingsContainer.getNumberOfTasksForFragmentationSetting());
        tmpSettingsContainer.setRegardStereochemistrySetting(false);
        Assertions.assertFalse(tmpSettingsContainer.getRegardStereochemistrySetting());
        String tmpExistingDirectory = System.getProperty("java.io.tmpdir");
        tmpSettingsContainer.setRecentDirectoryPathSetting(tmpExistingDirectory);
        Assertions.assertEquals(tmpExistingDirectory, tmpSettingsContainer.getRecentDirectoryPathSetting());
        tmpSettingsContainer.setCsvExportSeparatorSetting(Exporter.CSVSeparator.SEMICOLON);
        Assertions.assertEquals(Exporter.CSVSeparator.SEMICOLON, tmpSettingsContainer.getCsvExportSeparatorSetting());
    }

    /**
     * Tests the two deprecated dummy settings (Group C): their getters hard-return false, their property accessors are
     * non-null, and calling the dummy setters with true is a no-op (the values stay false).
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testDeprecatedDummySettingsAreNoOp() throws Exception {
        SettingsContainer tmpSettingsContainer = new SettingsContainer();
        tmpSettingsContainer.restoreDefaultSettings();
        Assertions.assertFalse(tmpSettingsContainer.getKeepAtomContainerInDataModelSetting());
        Assertions.assertFalse(tmpSettingsContainer.isKeepLastFragmentSetting());
        Assertions.assertNotNull(tmpSettingsContainer.keepAtomContainerInDataModelSettingProperty());
        Assertions.assertNotNull(tmpSettingsContainer.keepLastFragmentSettingProperty());
        tmpSettingsContainer.setKeepAtomContainerInDataModelSetting(true);
        tmpSettingsContainer.setKeepLastFragmentSetting(true);
        Assertions.assertFalse(tmpSettingsContainer.getKeepAtomContainerInDataModelSetting());
        Assertions.assertFalse(tmpSettingsContainer.isKeepLastFragmentSetting());
        Assertions.assertFalse(tmpSettingsContainer.keepAtomContainerInDataModelSettingProperty().get());
        Assertions.assertFalse(tmpSettingsContainer.keepLastFragmentSettingProperty().get());
    }

    /**
     * Tests a @TempDir-isolated persist->reload round-trip (Group D): non-default values written by a first container
     * are persisted and then read back by a fresh second container, asserting the reloaded values equal the written
     * ones. The user.home redirect and FileUtil cache are restored in a finally block so the real ~/MORTAR/Settings is
     * never touched.
     *
     * @param aTempHome JUnit-managed temporary directory used as a redirected user.home
     * @throws Exception if anything goes wrong
     */
    @Test
    public void persistAndReloadRoundTrip(@TempDir Path aTempHome) throws Exception {
        String tmpOldHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", aTempHome.toString());
            this.resetAppDirPathCache();
            SettingsContainer tmpFirst = new SettingsContainer();
            tmpFirst.setRowsPerPageSetting(SettingsContainer.ROWS_PER_PAGE_SETTING_DEFAULT + 5);
            tmpFirst.setAlwaysMDLV3000FormatAtExportSetting(true);
            tmpFirst.setCsvExportSeparatorSetting(Exporter.CSVSeparator.SEMICOLON);
            tmpFirst.preserveSettings();
            SettingsContainer tmpSecond = new SettingsContainer();
            tmpSecond.reloadGlobalSettings();
            Assertions.assertEquals(SettingsContainer.ROWS_PER_PAGE_SETTING_DEFAULT + 5, tmpSecond.getRowsPerPageSetting());
            Assertions.assertTrue(tmpSecond.getAlwaysMDLV3000FormatAtExportSetting());
            Assertions.assertEquals(Exporter.CSVSeparator.SEMICOLON, tmpSecond.getCsvExportSeparatorSetting());
        } finally {
            System.setProperty("user.home", tmpOldHome);
            this.resetAppDirPathCache();
            LogManager.getLogManager().reset();
        }
    }

    /**
     * Tests that the recent-directory-path validator rejects null both through the public setter and through the
     * underlying JavaFX property's set() override (Group E). This validator raises NO GUI alert (it is an internal
     * setting), so no Mockito neutralization is required.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testIllegalRecentDirectoryPathThrows() throws Exception {
        SettingsContainer tmpSettingsContainer = new SettingsContainer();
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tmpSettingsContainer.setRecentDirectoryPathSetting(null));
        //also drive the property override's illegal branch directly (no GUI alert on this internal setting)
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tmpSettingsContainer.recentDirectoryPathSettingProperty().set(null));
    }

    /**
     * Tests that an illegal rows-per-page value is rejected both by the public setter and by the JavaFX property's
     * set() override (Group E). The property override routes through GuiUtil.guiExceptionAlert, so that static call is
     * neutralized via Mockito.mockStatic for the headless run.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testIllegalRowsPerPageThrowsAndAlertNeutralized() throws Exception {
        SettingsContainer tmpSettingsContainer = new SettingsContainer();
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tmpSettingsContainer.setRowsPerPageSetting(0));
        try (MockedStatic<GuiUtil> tmpGuiUtilMock = Mockito.mockStatic(GuiUtil.class)) {
            tmpGuiUtilMock.when(() -> GuiUtil.guiExceptionAlert(
                    Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenAnswer(anInvocation -> null);
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> tmpSettingsContainer.rowsPerPageSettingProperty().set(0));
        }
    }

    /**
     * Tests that an illegal number-of-tasks value is rejected both by the public setter and by the JavaFX property's
     * set() override (Group E). The property override routes through GuiUtil.guiExceptionAlert, so that static call is
     * neutralized via Mockito.mockStatic for the headless run.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testIllegalNumberOfTasksThrowsAndAlertNeutralized() throws Exception {
        SettingsContainer tmpSettingsContainer = new SettingsContainer();
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tmpSettingsContainer.setNumberOfTasksForFragmentationSetting(0));
        try (MockedStatic<GuiUtil> tmpGuiUtilMock = Mockito.mockStatic(GuiUtil.class)) {
            tmpGuiUtilMock.when(() -> GuiUtil.guiExceptionAlert(
                    Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenAnswer(anInvocation -> null);
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> tmpSettingsContainer.numberOfTasksForFragmentationSettingProperty().set(0));
        }
    }
    //</editor-fold>
    //
    //<editor-fold desc="Private methods" defaultstate="collapsed">
    /**
     * Reflectively resets the private static appDirPath cache in FileUtil to null so a redirected user.home is
     * re-resolved instead of read from the stale cache.
     *
     * @throws Exception if the reflective field access fails
     */
    private void resetAppDirPathCache() throws Exception {
        Field tmpField = FileUtil.class.getDeclaredField("appDirPath");
        tmpField.setAccessible(true);
        tmpField.set(null, null);
    }
    //</editor-fold>
}
