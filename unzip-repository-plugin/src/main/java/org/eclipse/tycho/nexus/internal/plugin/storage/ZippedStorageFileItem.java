/*******************************************************************************
 * Copyright (c) 2010, 2014 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.nexus.internal.plugin.storage;

import java.io.IOException;
import java.io.InputStream;

import org.sonatype.nexus.proxy.item.ContentLocator;
import org.sonatype.nexus.proxy.item.DefaultStorageFileItem;

/**
 * This class implements a storage file item for a file inside a zip file.
 */
public final class ZippedStorageFileItem extends DefaultStorageFileItem {

    private static class ZippedStorageFileContentLocator implements ContentLocator {
        private final ZippedItem zippedItem;
        private final long length;

        private ZippedStorageFileContentLocator(final ZippedItem zippedItem, long length) {
            this.zippedItem = zippedItem;
            this.length = length;
        }

        @Override
        public InputStream getContent() throws IOException {
            return zippedItem.getStreamOfZippedFile();
        }

        @Override
        public String getMimeType() {
            return zippedItem.getMimeType();
        }

        @Override
        public boolean isReusable() {
            return false;
        }

        @Override
        public long getLength() {
            return length;
        }

    }

    /**
     * Constructor
     * 
     * @param zippedItem
     *            the file item represented by this storage item
     * @param length
     *            the length of the represented file
     * @param modified
     *            the modification time of the represented file
     */
    public ZippedStorageFileItem(final ZippedItem zippedItem, final long length) {
        super(zippedItem.getRepository(), zippedItem.getRequest(), true, false, new ZippedStorageFileContentLocator(
                zippedItem, length));
        // At creation time the underlying zip entry is known.
        // Keeping this information avoids to open the zip and loop over the
        // entries when answering related questions
        setModified(zippedItem.getLastModified());
    }

}
