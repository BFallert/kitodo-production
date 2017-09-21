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

import java.util.List;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kitodo.MockDatabase;
import org.kitodo.dto.ProcessDTO;

public class ProzessverwaltungFormIT {

    @BeforeClass
    public static void prepareDatabase() throws Exception {
        MockDatabase.startNode();
        MockDatabase.insertProcessesFull();
    }

    @AfterClass
    public static void cleanDatabase() throws Exception {
        MockDatabase.stopNode();
        MockDatabase.cleanDatabase();
    }

    @Before
    public void multipleInit() throws InterruptedException {
        Thread.sleep(500);
    }

    @Test
    public void shouldFilterAlleStart() throws Exception {
        ProzessverwaltungForm prozessverwaltungForm = new ProzessverwaltungForm();

        prozessverwaltungForm.setShowArchivedProjects(false);
        prozessverwaltungForm.setShowClosedProcesses(false);
        prozessverwaltungForm.setModusAnzeige("aktuell");
        prozessverwaltungForm.setFilter("id:2 3");

        prozessverwaltungForm.filterAlleStart();
        List<ProcessDTO> processDTOS = prozessverwaltungForm.getProcessDTOS();
        assertEquals("Amount of found processes is incorrect!", 2, processDTOS.size());

        prozessverwaltungForm.setFilter("");

        prozessverwaltungForm.filterAlleStart();
        processDTOS = prozessverwaltungForm.getProcessDTOS();
        assertEquals("Amount of found processes is incorrect!", 3, processDTOS.size());

        prozessverwaltungForm.setModusAnzeige("vorlagen");

        prozessverwaltungForm.filterAlleStart();
        processDTOS = prozessverwaltungForm.getProcessDTOS();
        assertEquals("Amount of found processes is incorrect!", 1, processDTOS.size());
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
