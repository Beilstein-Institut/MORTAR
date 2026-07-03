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

import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Optional;

/**
 * Static helper utility for headless JavaFX controller tests. Provides an offscreen {@link Stage} factory (constructed
 * but never shown, so it must be invoked on the FX thread) and a factory that returns a {@link MockedStatic} over
 * {@link GuiUtil} in which every alert entry point is neutralized, so no code path reaches a real JavaFX {@code Alert}
 * (which throws or blocks when run headless). The static mock is created with {@link Mockito#CALLS_REAL_METHODS} so
 * the non-alert {@link GuiUtil} helpers stay functional; callers use the returned {@link MockedStatic} in a
 * try-with-resources block.
 * <p>
 * This class is {@code final} with a private no-argument constructor and only static members, following the MORTAR
 * utility-class convention, and intentionally carries NO {@code Test} suffix so JUnit does not treat it as a test class.
 *
 * @author Felix Baensch
 * @version 1.0.0.0
 */
public final class FxTestUtil {
    //<editor-fold desc="Private static final class constants" defaultstate="collapsed">
    /**
     * Default width of the trivial offscreen scene, in pixels.
     */
    private static final double DEFAULT_SCENE_WIDTH = 800.0;
    /**
     * Default height of the trivial offscreen scene, in pixels.
     */
    private static final double DEFAULT_SCENE_HEIGHT = 600.0;
    //</editor-fold>
    //
    //<editor-fold desc="Private constructor" defaultstate="collapsed">
    /**
     * Private constructor that prevents instantiation of this static-only utility class.
     */
    private FxTestUtil() {
    }
    //</editor-fold>
    //
    //<editor-fold desc="Public static methods" defaultstate="collapsed">
    /**
     * Constructs a new offscreen {@link Stage} backed by a trivial {@link Scene} over an empty {@link Pane}. The stage
     * is NOT shown; construction alone is enough to exercise FX-thread wiring. This method MUST be called on the JavaFX
     * Application Thread (e.g. inside {@code AbstractFxTestCase.runAndWait}).
     *
     * @return a newly constructed, unshown offscreen stage
     */
    public static Stage newOffscreenStage() {
        Stage tmpStage = new Stage();
        tmpStage.setScene(new Scene(new Pane(), FxTestUtil.DEFAULT_SCENE_WIDTH, FxTestUtil.DEFAULT_SCENE_HEIGHT));
        return tmpStage;
    }
    //
    /**
     * Creates a {@link MockedStatic} over {@link GuiUtil} with {@link Mockito#CALLS_REAL_METHODS} default answer and
     * stubs every alert entry point so none reaches a real JavaFX {@code Alert}: {@code guiMessageAlert} and
     * {@code guiMessageAlertWithHyperlink} return {@link Optional#empty()}; {@code guiConfirmationAlert} and
     * {@code guiYesNoCancelConfirmationAlert} return {@link ButtonType#OK}; the void {@code guiExceptionAlert} and
     * {@code guiExpandableAlert} are stubbed to do nothing. All non-alert {@link GuiUtil} helpers remain functional.
     * The caller is responsible for closing the returned mock, typically via try-with-resources.
     *
     * @return a static mock of {@link GuiUtil} with all alert entry points neutralized
     */
    public static MockedStatic<GuiUtil> mockGuiAlerts() {
        MockedStatic<GuiUtil> tmpMock = Mockito.mockStatic(GuiUtil.class, Mockito.CALLS_REAL_METHODS);
        tmpMock.when(() -> GuiUtil.guiMessageAlert(Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Optional.empty());
        tmpMock.when(() -> GuiUtil.guiMessageAlertWithHyperlink(Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(Optional.empty());
        tmpMock.when(() -> GuiUtil.guiConfirmationAlert(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(ButtonType.OK);
        tmpMock.when(() -> GuiUtil.guiYesNoCancelConfirmationAlert(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(ButtonType.OK);
        tmpMock.when(() -> GuiUtil.guiExceptionAlert(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenAnswer(anInvocation -> null);
        tmpMock.when(() -> GuiUtil.guiExpandableAlert(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(anInvocation -> null);
        return tmpMock;
    }
    //</editor-fold>
}
