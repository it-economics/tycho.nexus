/*******************************************************************************
 * Copyright (c) 2010, 2012 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.nexus.internal.plugin.cache;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;
import org.eclipse.tycho.nexus.internal.plugin.test.TestUtil;
import org.eclipse.tycho.nexus.internal.plugin.test.UnzipPluginTestSupport;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.sonatype.nexus.proxy.ItemNotFoundException;
import org.sonatype.nexus.proxy.StorageException;

@SuppressWarnings("deprecation")
public class UnzipCacheTest extends UnzipPluginTestSupport {

    private static final String PATH_UP_TO_VERSION = "/ga/1.0.0-SNAPSHOT/archive-1.0.0-";

    private static final String LATEST_VERSION = "20101013";
    private static final String SNAPSHOT_REQUEST_PATH = "/ga/1.0.0-SNAPSHOT/archive-1.0.0-SNAPSHOT.zip-unzip";

    private static final String PATH_TO_OLD_ZIP = "/ga/1.0.0-SNAPSHOT/archive-1.0.0-20101012-1.zip";
    private static final String PATH_TO_OLD_OTHER_ZIP = "/ga/1.0.0-SNAPSHOT/archive-1.0.0-20101012-1-juhu.zip";
    private static final String PATH_TO_LATEST_OTHER_ZIP = "/ga/1.0.0-SNAPSHOT/archive-1.0.0-20101013-2-juhu.zip";
    private static final String PATH_TO_LATEST_ZIP = "/ga/1.0.0-SNAPSHOT/archive-1.0.0-20101013-2.zip";

    private UnzipCache snapshotRepoUnzipCache;
    private static File oldZip;
    private static File oldOtherzip;
    private static File latestOtherZip;

    @Before
    public void setupTestRepos() throws Exception {
        snapshotRepoUnzipCache = createUnzipRepo(createSnapshotRepo()).getCache();

        oldZip = snapshotRepoUnzipCache.getArchive(PATH_TO_OLD_ZIP);
        oldOtherzip = snapshotRepoUnzipCache.getArchive(PATH_TO_OLD_OTHER_ZIP);
        latestOtherZip = snapshotRepoUnzipCache.getArchive(PATH_TO_LATEST_OTHER_ZIP);
        assertTrue(oldOtherzip.exists());
        assertTrue(oldZip.exists());
        assertTrue(latestOtherZip.exists());
    }

    @Test
    public void testReleaseRedeploy() throws Exception {
        String redeployableRepoDir = "src/test/resources/redeployRelRepo";
        String redeployableArchiveRepoPath = "/dir/redeployable-1.0.0.zip";
        String redeployableArchiveFullPath = redeployableRepoDir + redeployableArchiveRepoPath;
        final File redeployableArchiveFile = new File(redeployableArchiveFullPath);

        try {
            UnzipCache cache = createUnzipRepo(createRedeployRelRepo()).getCache();

            final File version1File = new File(redeployableRepoDir + "/dir/version-1.zip");
            final File version2File = new File(redeployableRepoDir + "/dir/version-2.zip");

            FileUtils.copyFile(version1File, redeployableArchiveFile);

            final File redeployableRequest1 = cache.getArchive(redeployableArchiveRepoPath);
            //expect content of version-1.zip
            Assert.assertArrayEquals(FileUtils.readFileToByteArray(version1File),
                    FileUtils.readFileToByteArray(redeployableRequest1));

            //update content of redeployable-1.0.0
            FileUtils.copyFile(version2File, redeployableArchiveFile);
            FileUtils.touch(redeployableArchiveFile);

            final File redeployableRequest2 = cache.getArchive(redeployableArchiveRepoPath);
            //expect content of version-2.zip
            Assert.assertArrayEquals(FileUtils.readFileToByteArray(version2File),
                    FileUtils.readFileToByteArray(redeployableRequest2));
        } finally {
            FileUtils.deleteQuietly(redeployableArchiveFile);
        }
    }

    @Test
    public void testCleanUpOldSnapshots() throws StorageException, ItemNotFoundException {

        final File latestZip = new File(oldZip.getParentFile() + File.separator + "archive-1.0.0-20101013-2.zip");
        assertFalse(latestZip.exists());

        final ConversionResult conversionResult = new ConversionResult(SNAPSHOT_REQUEST_PATH, PATH_TO_LATEST_ZIP,
                LATEST_VERSION, PATH_UP_TO_VERSION);

        assertTrue(conversionResult.isPathConverted());

        final File latestZipFromCache = snapshotRepoUnzipCache.getArchive(PATH_TO_LATEST_ZIP);
        snapshotRepoUnzipCache.cleanSnapshots(conversionResult);
        assertEquals(latestZip.getPath(), latestZipFromCache.getPath());

        assertFalse(oldZip.exists());
        assertFalse(oldOtherzip.exists());

        assertTrue(latestZip.exists());
        assertTrue(latestOtherZip.exists());
    }

    @Test
    public void testCleanUpOldSnapshotsCurrentSnapshotAlreadyCached() throws StorageException, ItemNotFoundException {

        final File latestZip = snapshotRepoUnzipCache.getArchive(PATH_TO_LATEST_ZIP);
        assertTrue(latestZip.exists());
        assertTrue(oldZip.exists());
        assertTrue(oldOtherzip.exists());
        assertTrue(latestOtherZip.exists());

        final ConversionResult conversionResult = new ConversionResult("/ga/1.0.0-SNAPSHOT/archive-1.0.0-SNAPSHOT.zip",
                PATH_TO_LATEST_ZIP, "20101013", PATH_UP_TO_VERSION);

        assertTrue(conversionResult.isPathConverted());

        final File latestZipFromCache = snapshotRepoUnzipCache.getArchive(PATH_TO_LATEST_ZIP);
        snapshotRepoUnzipCache.cleanSnapshots(conversionResult);
        assertEquals(latestZip.getPath(), latestZipFromCache.getPath());

        assertFalse(oldZip.exists());
        assertFalse(oldOtherzip.exists());

        assertTrue(latestZip.exists());
        assertTrue(latestOtherZip.exists());
    }

    @Test
    public void testNoCleanUpOldSnapshotsNoConversion() throws StorageException, ItemNotFoundException {

        final File latestZip = new File(oldZip.getParentFile() + File.separator + "archive-1.0.0-20101013-2.zip");
        assertFalse(latestZip.exists());

        final ConversionResult conversionResult = new ConversionResult(SNAPSHOT_REQUEST_PATH);

        assertFalse(conversionResult.isPathConverted());

        final File latestZipFromCache = snapshotRepoUnzipCache.getArchive(PATH_TO_LATEST_ZIP);
        snapshotRepoUnzipCache.cleanSnapshots(conversionResult);
        assertEquals(latestZip.getPath(), latestZipFromCache.getPath());

        assertTrue(oldZip.exists());
        assertTrue(oldOtherzip.exists());

        assertTrue(latestZip.exists());
        assertTrue(latestOtherZip.exists());
    }

    @Test
    public void testNoSnapshotsCleanAll() throws ItemNotFoundException, IOException {
        final File latestZipFromCache = snapshotRepoUnzipCache.getArchive(PATH_TO_LATEST_ZIP);

        // modification of the cached file, so that we can check the recreation after the cache was cleaned
        latestZipFromCache.delete();
        latestZipFromCache.createNewFile();
        final long modifiedFileLength = latestZipFromCache.length();

        final ConversionResult conversionResult = new ConversionResult(SNAPSHOT_REQUEST_PATH, PATH_UP_TO_VERSION, false);

        assertFalse(conversionResult.isPathConverted());
        assertFalse(conversionResult.isASnapshotAvailable());

        snapshotRepoUnzipCache.cleanSnapshots(conversionResult);
        final File latestZipFromCache2 = snapshotRepoUnzipCache.getArchive(PATH_TO_LATEST_ZIP);
        assertTrue("Expected that the file would be deleted and recreated by the cache, but was not.",
                modifiedFileLength != latestZipFromCache2.length());

        assertFalse(oldZip.exists());
        assertFalse(oldOtherzip.exists());
        assertFalse(latestOtherZip.exists());

    }

    @Test
    public void testCacheForThreadSafty() throws InterruptedException, ExecutionException {
        final CountDownLatch startSignal = new CountDownLatch(1);
        final CountDownLatch doneSignal = new CountDownLatch(40);

        final ExecutorService executor = Executors.newCachedThreadPool();
        final List<Future<Void>> results = new LinkedList<Future<Void>>();

        for (int i = 0; i < 20; i++) {
            final Callable<Void> c = new CacheStressWorker(startSignal, doneSignal, PATH_TO_OLD_ZIP);
            results.add(executor.submit(c));
            final Callable<Void> c2 = new CacheStressWorker(startSignal, doneSignal, PATH_TO_LATEST_ZIP);
            results.add(executor.submit(c2));
        }

        //all threads: GO!
        startSignal.countDown();

        //Wait till all threads are done
        doneSignal.await();

        //Check all results: if an exception occured fail (probably an concurrency issue)
        for (final Future<Void> submitResult : results) {
            try {
                submitResult.get();
            } catch (final ExecutionException e) {
                throw new ExecutionException("At least one thread fail to execute", e);
            }
        }

        executor.shutdown();
    }

    class CacheStressWorker implements Callable<Void> {
        private final CountDownLatch startSignal;
        private final String archivePath;
        private final CountDownLatch doneSignal;

        CacheStressWorker(final CountDownLatch startSignal, final CountDownLatch doneSignal, final String archivePath) {
            this.startSignal = startSignal;
            this.archivePath = archivePath;
            this.doneSignal = doneSignal;
        }

        @Override
        public Void call() throws Exception {
            try {
                startSignal.await();
                return doWork();
            } finally {
                doneSignal.countDown();
            }
        }

        public Void doWork() throws StorageException, ItemNotFoundException {
            snapshotRepoUnzipCache.getArchive(archivePath);
            return null;
        }

    }

    @Test
    public void testMultiThreadedCleanUpAndCreation() throws InterruptedException, ExecutionException {
        final CountDownLatch startSignal = new CountDownLatch(1);
        final CountDownLatch doneSignal = new CountDownLatch(40);

        final ExecutorService executor = Executors.newCachedThreadPool();
        final List<Future<Void>> results = new LinkedList<Future<Void>>();

        for (int i = 0; i < 20; i++) {
            final Callable<Void> c = new RequestWorker(startSignal, doneSignal, true);
            results.add(executor.submit(c));
            final Callable<Void> c2 = new RequestWorker(startSignal, doneSignal, false);
            results.add(executor.submit(c2));
        }

        //all threads: GO!
        startSignal.countDown();

        //Wait till all threads are done
        doneSignal.await();

        //Check all results: if an exception occured fail (probably an concurrency issue)
        for (final Future<Void> submitResult : results) {
            try {
                submitResult.get();
            } catch (final ExecutionException e) {
                throw new ExecutionException("At least one thread fail to execute", e);
            }
        }

        executor.shutdown();
    }

    class RequestWorker implements Callable<Void> {

        private final CountDownLatch startSignal;
        private final boolean cleanup;
        private final CountDownLatch doneSignal;

        RequestWorker(final CountDownLatch startSignal, final CountDownLatch doneSignal, final boolean cleanup) {
            this.startSignal = startSignal;
            this.cleanup = cleanup;
            this.doneSignal = doneSignal;
        }

        @Override
        public Void call() throws Exception {
            try {
                startSignal.await();
                doWork();
            } finally {
                doneSignal.countDown();
            }
            return null;
        }

        void doWork() throws StorageException, ItemNotFoundException {

            if (cleanup) {
                snapshotRepoUnzipCache.getArchive(PATH_TO_LATEST_ZIP);
                snapshotRepoUnzipCache.cleanSnapshots(new ConversionResult(SNAPSHOT_REQUEST_PATH, PATH_TO_LATEST_ZIP,
                        LATEST_VERSION, PATH_UP_TO_VERSION));
            } else {
                snapshotRepoUnzipCache.getArchive(PATH_TO_OLD_ZIP);
            }
        }
    }

    @AfterClass
    public static void classTearDown() {
        TestUtil.cleanUpTestFiles();
    }
}
