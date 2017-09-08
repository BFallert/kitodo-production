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

package de.sub.goobi.forms;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kitodo.MockDatabase;

public class ProzessverwaltungFormIT {

    @BeforeClass
    public static void prepareDatabase() throws Exception {
        MockDatabase.insertProcessesFull();
    }

    @Before
    public void multipleInit() throws InterruptedException {
        Thread.sleep(1000);
    }

    @Test
    public void shouldGetStatisticsManager() throws Exception {
        ProzessverwaltungForm prozessverwaltungForm = new ProzessverwaltungForm();

        prozessverwaltungForm.filterAlleStart();
        //TODO: think how to test this manager
        //prozessverwaltungForm.StatisticsProject();

        //assertEquals("Number of pages was not counted correctly!", Integer.valueOf(50), project.getNumberOfPages());
        //assertEquals("Number of volumes was not counted correctly!", Integer.valueOf(3), project.getNumberOfVolumes());
    }
}
