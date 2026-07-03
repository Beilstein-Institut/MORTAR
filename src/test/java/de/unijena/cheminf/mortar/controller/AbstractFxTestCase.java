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
import de.unijena.cheminf.mortar.model.util.FileUtil;

import javafx.application.Platform;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Shared headless base class for JavaFX controller tests. This class boots the JavaFX toolkit exactly once per JVM
 * (via a static-guarded {@link Platform#startup(Runnable)} awaited on a bounded {@link CountDownLatch}), sets the
 * mandatory {@code en-GB} default locale and bootstraps the {@link Configuration} singleton, and installs a default
 * uncaught-exception handler that captures failures thrown on the JavaFX Application Thread so they can be surfaced on
 * the test thread. Per test it redirects the {@code user.home} system property to a JUnit {@link TempDir} and
 * reflectively resets the private static {@code appDirPath} cache of {@link FileUtil}, always restoring the original
 * state afterwards, so the real {@code ~/MORTAR} directory is never touched and no logger handler leaks into sibling
 * tests. It provides a bounded {@link #runAndWait(Runnable)} that executes work on the FX thread and rethrows any
 * failure on the caller thread, plus {@link #waitForFxEvents()} to drain the FX event queue.
 * <p>
 * This class intentionally carries NO {@code Test} suffix so that JUnit does not treat it as an executable test class;
 * concrete controller tests extend it. The Monocle headless system properties are supplied by the Gradle
 * {@code tasks.test} block before any FX class loads (see {@code build.gradle.kts}), so the toolkit starts headless.
 *
 * @author Felix Baensch
 * @version 1.0.0.0
 */
public abstract class AbstractFxTestCase {
    //<editor-fold desc="Private static final class constants" defaultstate="collapsed">
    /**
     * Bounded wait (in seconds) applied to every toolkit boot and {@code runAndWait} latch, so a stuck FX thread fails
     * fast instead of hanging the CI build.
     */
    private static final long FX_TIMEOUT_SECONDS = 10L;
    /**
     * Logger of this class.
     */
    private static final Logger LOGGER = Logger.getLogger(AbstractFxTestCase.class.getName());
    /**
     * Captures the last uncaught throwable observed on the JavaFX Application Thread, so it can be surfaced on the test
     * thread rather than being silently swallowed by the FX event loop.
     */
    private static final AtomicReference<Throwable> FX_UNCAUGHT = new AtomicReference<>();
    /**
     * Monitor guarding the {@link #toolkitStarted} check-then-act, so that the once-per-JVM {@link Platform#startup}
     * cannot race even if the suite is later run with JUnit parallel execution enabled.
     */
    private static final Object TOOLKIT_LOCK = new Object();
    //</editor-fold>
    //
    //<editor-fold desc="Private static class variables" defaultstate="collapsed">
    /**
     * Once-per-JVM guard: {@link Platform#startup(Runnable)} may be called only once, so the toolkit is booted lazily
     * on the first {@code @BeforeAll} and this flag prevents a second start.
     */
    private static volatile boolean toolkitStarted = false;
    //</editor-fold>
    //
    //<editor-fold desc="Private instance variables" defaultstate="collapsed">
    /**
     * The original {@code user.home} system property value, saved before each test so it can be restored afterwards.
     */
    private String originalUserHome;
    //</editor-fold>
    //
    //<editor-fold desc="Constructor" defaultstate="collapsed">
    /**
     * Default no-argument constructor. Concrete subclasses inherit the toolkit boot, locale guard, isolation, and
     * bounded-wait helpers; no per-instance state is initialized here.
     */
    protected AbstractFxTestCase() {
    }
    //</editor-fold>
    //
    //<editor-fold desc="Lifecycle hooks" defaultstate="collapsed">
    /**
     * Boots the JavaFX toolkit exactly once per JVM. Sets the default locale to {@code en-GB} (so message-bundle
     * resolution is deterministic), bootstraps the {@link Configuration} singleton, installs a default uncaught-exception
     * handler that records <em>only</em> JavaFX-Application-Thread failures into {@link #FX_UNCAUGHT} (throwables from
     * any other thread are logged and delegated to the previously installed default handler, so a foreign background
     * failure is never misattributed to the current test), and, if not already started, calls
     * {@link Platform#startup(Runnable)} and awaits a bounded {@link CountDownLatch}. Once started,
     * {@link Platform#setImplicitExit(boolean)} is set to {@code false} so the toolkit survives across tests.
     *
     * @throws Exception if the Configuration singleton cannot be initialized or the toolkit does not start within the
     *                   bounded timeout
     */
    @BeforeAll
    public static void bootToolkitOnce() throws Exception {
        Locale.setDefault(Locale.of("en", "GB"));
        Configuration.getInstance();
        synchronized (AbstractFxTestCase.TOOLKIT_LOCK) {
            if (!AbstractFxTestCase.toolkitStarted) {
                Thread.UncaughtExceptionHandler tmpPreviousHandler = Thread.getDefaultUncaughtExceptionHandler();
                Thread.setDefaultUncaughtExceptionHandler((aThread, aThrowable) -> {
                    if (Platform.isFxApplicationThread()) {
                        AbstractFxTestCase.FX_UNCAUGHT.set(aThrowable);
                        AbstractFxTestCase.LOGGER.severe("Uncaught throwable on the JavaFX Application Thread: " + aThrowable);
                    } else {
                        AbstractFxTestCase.LOGGER.severe("Uncaught throwable on thread " + aThread.getName() + ": " + aThrowable);
                        if (tmpPreviousHandler != null) {
                            tmpPreviousHandler.uncaughtException(aThread, aThrowable);
                        }
                    }
                });
                CountDownLatch tmpLatch = new CountDownLatch(1);
                Platform.startup(tmpLatch::countDown);
                if (!tmpLatch.await(AbstractFxTestCase.FX_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("JavaFX toolkit did not start within " + AbstractFxTestCase.FX_TIMEOUT_SECONDS + " seconds");
                }
                Platform.setImplicitExit(false);
                AbstractFxTestCase.toolkitStarted = true;
            }
        }
    }
    //
    /**
     * Redirects the {@code user.home} system property to a per-test temporary directory and reflectively resets the
     * {@link FileUtil} {@code appDirPath} cache, so any application-data-directory resolution during the test is
     * isolated to the {@link TempDir} and the real {@code ~/MORTAR} directory is never touched. Also clears any
     * previously captured FX-thread throwable so it does not leak into this test.
     *
     * @param aTempHome temporary directory used as a fake user home for this test
     * @throws Exception if the {@code appDirPath} cache field cannot be reset
     */
    @BeforeEach
    public void isolateUserHome(@TempDir Path aTempHome) throws Exception {
        this.originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", aTempHome.toString());
        this.resetAppDirPathCache();
        AbstractFxTestCase.FX_UNCAUGHT.set(null);
    }
    //
    /**
     * Always restores the original {@code user.home} system property and resets the {@link FileUtil} {@code appDirPath}
     * cache. Instead of a JVM-wide {@code LogManager.reset()} (which would close and remove every handler on every
     * logger in the entire JVM and is never restored), only {@link FileHandler}s on the root logger are closed and
     * removed. This surgically releases any file handler that may have been rooted in the per-test temporary
     * {@code user.home} (so the {@link TempDir} can be deleted, notably on Windows) without wiping the JVM-global
     * logging configuration that sibling tests rely on.
     *
     * @throws Exception if the {@code appDirPath} cache field cannot be reset
     */
    @AfterEach
    public void restoreUserHome() throws Exception {
        if (this.originalUserHome != null) {
            System.setProperty("user.home", this.originalUserHome);
        }
        this.resetAppDirPathCache();
        Logger tmpRootLogger = LogManager.getLogManager().getLogger("");
        for (Handler tmpHandler : tmpRootLogger.getHandlers()) {
            if (tmpHandler instanceof FileHandler) {
                tmpHandler.close();
                tmpRootLogger.removeHandler(tmpHandler);
            }
        }
    }
    //</editor-fold>
    //
    //<editor-fold desc="Protected methods" defaultstate="collapsed">
    /**
     * Runs the given runnable on the JavaFX Application Thread and blocks (bounded) until it completes. If the calling
     * thread is already the FX thread the runnable runs inline; otherwise it is dispatched via
     * {@link Platform#runLater(Runnable)}, any thrown {@link Throwable} is captured, and it is rethrown on the caller
     * thread. A previously captured FX-thread uncaught throwable is also surfaced.
     *
     * @param aRunnable the work to execute on the FX thread; must not be null
     * @throws Exception if the runnable throws, if a prior FX-thread uncaught throwable was recorded, or if the
     *                   runnable does not complete within the bounded timeout
     */
    protected static void runAndWait(Runnable aRunnable) throws Exception {
        if (Platform.isFxApplicationThread()) {
            aRunnable.run();
            AbstractFxTestCase.rethrowFxUncaught();
            return;
        }
        CountDownLatch tmpLatch = new CountDownLatch(1);
        AtomicReference<Throwable> tmpError = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                aRunnable.run();
            } catch (Throwable anError) {
                tmpError.set(anError);
            } finally {
                tmpLatch.countDown();
            }
        });
        if (!tmpLatch.await(AbstractFxTestCase.FX_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("FX runnable did not complete within " + AbstractFxTestCase.FX_TIMEOUT_SECONDS + " seconds");
        }
        if (tmpError.get() != null) {
            throw new RuntimeException("Runnable failed on the JavaFX Application Thread", tmpError.get());
        }
        AbstractFxTestCase.rethrowFxUncaught();
    }
    //
    /**
     * Drains the JavaFX event queue by delegating to {@link WaitForAsyncUtils#waitForFxEvents()}, so pending
     * {@code runLater} tasks and pulse-driven work are processed before assertions are made.
     */
    protected static void waitForFxEvents() {
        WaitForAsyncUtils.waitForFxEvents();
    }
    //</editor-fold>
    //
    //<editor-fold desc="Private methods" defaultstate="collapsed">
    /**
     * Reflectively resets the private static {@code appDirPath} cache of {@link FileUtil} to null, so the next call to
     * {@code getAppDirPath} re-resolves the data directory from the current {@code user.home} system property.
     *
     * @throws Exception if the field cannot be accessed
     */
    private void resetAppDirPathCache() throws Exception {
        Field tmpField = FileUtil.class.getDeclaredField("appDirPath");
        tmpField.setAccessible(true);
        tmpField.set(null, null);
    }
    //
    /**
     * Surfaces any throwable captured from the JavaFX Application Thread on the calling (test) thread, clearing the
     * captured reference so it is reported at most once.
     *
     * @throws RuntimeException wrapping the captured FX-thread throwable, if one was recorded
     */
    private static void rethrowFxUncaught() {
        Throwable tmpThrowable = AbstractFxTestCase.FX_UNCAUGHT.getAndSet(null);
        if (tmpThrowable != null) {
            throw new RuntimeException("Uncaught throwable on the JavaFX Application Thread", tmpThrowable);
        }
    }
    //</editor-fold>
}
