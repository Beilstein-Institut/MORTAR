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

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Desktop;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Static helper utility for headless JavaFX controller tests. Provides an offscreen {@link Stage} factory (constructed
 * but never shown, so it must be invoked on the FX thread) and a factory that returns a {@link MockedStatic} over
 * {@link GuiUtil} in which every alert entry point is neutralized, so no code path reaches a real JavaFX {@code Alert}
 * (which throws or blocks when run headless). The static mock is created with {@link Mockito#CALLS_REAL_METHODS} so
 * the non-alert {@link GuiUtil} helpers stay functional; callers use the returned {@link MockedStatic} in a
 * try-with-resources block. It further offers {@link #runAndDriveModal(Callable, Consumer)}, the single shared seam that
 * drives a blocking {@code showAndWait} construct to completion headlessly (fire handlers, then always close), and
 * {@link #mockDesktop()}, a {@link MockedStatic} over {@link Desktop} so OS-launch handlers do not throw when run
 * headless.
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
    /**
     * Bounded wait (in seconds) applied to the modal-driving helper, mirroring the harness's own 10 s bound so a stuck
     * {@code showAndWait} construct fails fast with an {@link IllegalStateException} instead of hanging the CI build.
     */
    private static final long FX_TIMEOUT_SECONDS = 10L;
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
    //
    /**
     * Drives a blocking {@code showAndWait} construct to completion headlessly and returns its result. The supplied
     * {@code aConstruct} is expected to build a controller (or open a view) whose stage is displayed via
     * {@link Stage#showAndWait()}, which blocks the JavaFX Application Thread in a nested event loop until that stage is
     * closed; a plain {@code runAndWait} over such a construct would therefore hang to the harness timeout. This helper
     * instead registers a {@link ListChangeListener} on {@link Window#getWindows()}; when the modal stage becomes
     * visible it schedules — via {@link Platform#runLater(Runnable)} so the work runs INSIDE the nested loop — a driver
     * that first invokes {@code aDriver} on the stage (if non-null) so button and close handlers can be fired, and then
     * ALWAYS closes the stage in a {@code finally} block so no orphan window leaks into a sibling test. The construct is
     * invoked on the FX thread and blocks until that close returns; the window listener is always removed in a
     * {@code finally}. The outer wait is bounded at {@link #FX_TIMEOUT_SECONDS} seconds (the same bound the harness
     * applies) so a stuck modal fails fast with an {@link IllegalStateException} rather than hanging the CI build.
     *
     * @param <T> the type produced by the construct (e.g. the controller instance; may be null for void opens)
     * @param aConstruct the blocking construct to invoke on the JavaFX Application Thread; must not be null
     * @param aDriver a callback fired on the modal stage before it is closed, or null to simply open then close
     * @return the value produced by the construct (may be null)
     * @throws IllegalStateException if the construct does not complete within the bounded timeout, or the waiting thread
     *                               is interrupted
     * @throws RuntimeException if the construct throws on the JavaFX Application Thread
     */
    public static <T> T runAndDriveModal(Callable<T> aConstruct, Consumer<Stage> aDriver) {
        AtomicReference<T> tmpResult = new AtomicReference<>();
        AtomicReference<Throwable> tmpError = new AtomicReference<>();
        CountDownLatch tmpDone = new CountDownLatch(1);
        Platform.runLater(() -> {
            ListChangeListener<Window> tmpListener = aChange -> {
                while (aChange.next()) {
                    for (Window tmpWindow : aChange.getAddedSubList()) {
                        if (tmpWindow instanceof Stage tmpStage && tmpWindow.isShowing()) {
                            Platform.runLater(() -> {
                                try {
                                    if (aDriver != null) {
                                        aDriver.accept(tmpStage);
                                    }
                                } finally {
                                    tmpStage.close();
                                }
                            });
                        }
                    }
                }
            };
            Window.getWindows().addListener(tmpListener);
            try {
                tmpResult.set(aConstruct.call());
            } catch (Throwable anError) {
                tmpError.set(anError);
            } finally {
                Window.getWindows().removeListener(tmpListener);
                tmpDone.countDown();
            }
        });
        try {
            if (!tmpDone.await(FxTestUtil.FX_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Modal construct did not complete within the bounded timeout");
            }
        } catch (InterruptedException anInterruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the modal construct to complete", anInterruptedException);
        }
        if (tmpError.get() != null) {
            throw new RuntimeException("Modal construct failed on the JavaFX Application Thread", tmpError.get());
        }
        return tmpResult.get();
    }
    //
    /**
     * Creates a {@link MockedStatic} over {@link Desktop} so OS-launch handlers (e.g. the About view's open-GitHub and
     * open-tutorial actions) do not throw a {@code HeadlessException} when fired headlessly: the static
     * {@code getDesktop()} is stubbed to return a plain {@link Mockito#mock(Class)} {@link Desktop} instance and
     * {@code isDesktopSupported()} is stubbed to return {@code true}. The returned mock is intentionally minimal;
     * callers add any per-test behavior (for example stubbing {@code browse}/{@code open} as a no-op, or making them
     * throw an {@link java.io.IOException} to cover a fallback branch) on the mocked {@link Desktop} instance obtained
     * via {@code Desktop.getDesktop()} inside the try-with-resources scope. The caller is responsible for closing the
     * returned mock, typically via try-with-resources.
     *
     * @return a static mock of {@link Desktop} whose {@code getDesktop()} yields a mock instance and whose
     *         {@code isDesktopSupported()} returns {@code true}
     */
    public static MockedStatic<Desktop> mockDesktop() {
        Desktop tmpDesktop = Mockito.mock(Desktop.class);
        MockedStatic<Desktop> tmpMock = Mockito.mockStatic(Desktop.class);
        tmpMock.when(Desktop::isDesktopSupported).thenReturn(true);
        tmpMock.when(Desktop::getDesktop).thenReturn(tmpDesktop);
        return tmpMock;
    }
    //</editor-fold>
}
