/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package de.sub.goobi.helper;

import java.io.IOException;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.data.database.beans.BaseIndexedBean;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.elasticsearch.exceptions.CustomResponseException;
import org.kitodo.services.data.base.SearchService;

public class IndexWorker implements Runnable {

    private int indexedObjects = 0;
    private SearchService searchService;
    private static final Logger logger = LogManager.getLogger(IndexWorker.class);

    /**
     * Constructor initializing an IndexWorker object with the given SearchService
     * and list of objects that will be indexed.
     *
     * @param searchService
     *            SearchService instance used for indexing
     */
    public IndexWorker(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
        this.indexedObjects = 0;
        int offset = 10000;
        int amountToIndex;
        try {
            amountToIndex = searchService.countDatabaseRows().intValue();
            if (amountToIndex < offset) {
                for (Object object : searchService.getAll()) {
                    this.searchService.saveToIndex((BaseIndexedBean) object);
                    this.indexedObjects++;
                }
            } else {
                while (this.indexedObjects < amountToIndex) {
                    List<Object> objectsToIndex = searchService.getAll(this.indexedObjects, this.indexedObjects + offset);
                    for (Object object : objectsToIndex) {
                        this.searchService.saveToIndex((BaseIndexedBean) object);
                        this.indexedObjects++;
                    }
                }
            }
        } catch (CustomResponseException | DAOException | IOException e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * Return the number of objects that have already been indexed during the
     * current indexing process.
     *
     * @return int the number of objects indexed during the current indexing run
     */
    public int getIndexedObjects() {
        return indexedObjects;
    }
}
