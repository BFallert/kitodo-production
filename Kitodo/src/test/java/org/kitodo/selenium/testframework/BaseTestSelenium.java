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

package org.kitodo.selenium.testframework;

import java.net.URI;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.kitodo.FileLoader;
import org.kitodo.MockDatabase;
import org.kitodo.selenium.testframework.helper.TestWatcherImpl;
import org.kitodo.services.ServiceManager;
import org.kitodo.services.file.FileService;

public class BaseTestSelenium {

    private static final FileService fileService = new ServiceManager().getFileService();

    @BeforeClass
    public static void setUp() throws Exception {
        MockDatabase.startNode();
        MockDatabase.insertProcessesFull();
        MockDatabase.startDatabaseServer();

        fileService.createDirectory(URI.create(""), "diagrams");
        FileLoader.createDiagramBaseFile();

        Browser.Initialize();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        Browser.close();

        FileLoader.deleteDiagramBaseFile();
        fileService.delete(URI.create("diagrams"));

        MockDatabase.stopNode();
        MockDatabase.stopDatabaseServer();
        MockDatabase.cleanDatabase();
    }

    @Before
    public void login() throws Exception {
        Pages.getLoginPage().goTo().performLoginAsAdmin();
    }

    /**
     * Watcher for WebDriverExceptions on travis which takes screenshot and sends
     * email
     */
    @Rule
    public TestRule seleniumExceptionWatcher = new TestWatcherImpl();
}
