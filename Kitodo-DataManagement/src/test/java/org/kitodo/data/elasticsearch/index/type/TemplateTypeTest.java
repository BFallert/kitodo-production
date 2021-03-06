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

package org.kitodo.data.elasticsearch.index.type;

import java.io.StringReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;

import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.kitodo.data.database.beans.Docket;
import org.kitodo.data.database.beans.Project;
import org.kitodo.data.database.beans.Ruleset;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.beans.Template;
import org.kitodo.data.elasticsearch.index.type.enums.TaskTypeField;
import org.kitodo.data.elasticsearch.index.type.enums.TemplateTypeField;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TemplateTypeTest {

    private static List<Template> prepareData() {

        List<Template> templates = new ArrayList<>();
        List<Task> tasks = new ArrayList<>();

        Project project = new Project();
        project.setTitle("Project");
        project.setId(1);

        Ruleset ruleset = new Ruleset();
        ruleset.setId(1);

        Docket docket = new Docket();
        docket.setId(1);

        Task firstTask = new Task();
        firstTask.setId(1);
        firstTask.setTitle("Task one");
        tasks.add(firstTask);

        Task secondTask = new Task();
        secondTask.setId(2);
        secondTask.setTitle("Task two");
        tasks.add(secondTask);

        Template firstTemplate = new Template();
        firstTemplate.setId(1);
        firstTemplate.setTitle("Testing");
        firstTemplate.setOutputName("Test");
        LocalDate localDate = new LocalDate(2017, 1, 1);
        firstTemplate.setCreationDate(localDate.toDate());
        firstTemplate.setTasks(tasks);
        firstTemplate.setWikiField("Wiki");
        firstTemplate.setProject(project);
        firstTemplate.setRuleset(ruleset);
        templates.add(firstTemplate);

        Template secondTemplate = new Template();
        secondTemplate.setId(2);
        secondTemplate.setTitle("Rendering");
        secondTemplate.setOutputName("Render");
        secondTemplate.setWikiField("Field");
        secondTemplate.setProject(project);
        secondTemplate.setDocket(docket);
        templates.add(secondTemplate);

        Template thirdTemplate = new Template();
        thirdTemplate.setId(3);
        thirdTemplate.setTitle("Incomplete");
        templates.add(thirdTemplate);

        return templates;
    }

    @Test
    public void shouldCreateFirstDocument() throws Exception {
        TemplateType templateType = new TemplateType();

        Template template = prepareData().get(0);
        HttpEntity document = templateType.createDocument(template);

        JsonObject actual = Json.createReader(new StringReader(EntityUtils.toString(document))).readObject();

        assertEquals("Key title doesn't match to given value!", "Testing",
            actual.getString(TemplateTypeField.TITLE.getName()));
        assertEquals("Key outputName doesn't match to given value!", "Test",
            actual.getString(TemplateTypeField.OUTPUT_NAME.getName()));
        assertEquals("Key wikiField doesn't match to given value!", "Wiki",
            actual.getString(TemplateTypeField.WIKI_FIELD.getName()));
        assertEquals("Key creationDate doesn't match to given value!", "2017-01-01",
            actual.getString(TemplateTypeField.CREATION_DATE.getName()));
        assertEquals("Key sortHelperStatus doesn't match to given value!", "",
            actual.getString(TemplateTypeField.SORT_HELPER_STATUS.getName()));
        assertEquals("Key project.id doesn't match to given value!", 1,
            actual.getInt(TemplateTypeField.PROJECT_ID.getName()));
        assertEquals("Key project.title doesn't match to given value!", "Project",
            actual.getString(TemplateTypeField.PROJECT_TITLE.getName()));
        assertTrue("Key project.active doesn't match to given value!",
            actual.getBoolean(TemplateTypeField.PROJECT_ACTIVE.getName()));
        assertEquals("Key docket doesn't match to given value!", 0, actual.getInt(TemplateTypeField.DOCKET.getName()));
        assertEquals("Key ruleset doesn't match to given value!", 1,
            actual.getInt(TemplateTypeField.RULESET.getName()));

        JsonArray tasks = actual.getJsonArray(TemplateTypeField.TASKS.getName());
        assertEquals("Size tasks doesn't match to given value!", 2, tasks.size());

        JsonObject task = tasks.getJsonObject(0);
        assertEquals("Key tasks.id doesn't match to given value!", 1, task.getInt(TaskTypeField.ID.getName()));
        assertEquals("Key tasks.title doesn't match to given value!", "Task one",
            task.getString(TaskTypeField.TITLE.getName()));

        task = tasks.getJsonObject(1);
        assertEquals("Key tasks.id doesn't match to given value!", 2, task.getInt(TaskTypeField.ID.getName()));
        assertEquals("Key tasks.title doesn't match to given value!", "Task two",
            task.getString(TaskTypeField.TITLE.getName()));
    }

    @Test
    public void shouldCreateSecondDocument() throws Exception {
        TemplateType templateType = new TemplateType();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        Template template = prepareData().get(1);
        HttpEntity document = templateType.createDocument(template);

        JsonObject actual = Json.createReader(new StringReader(EntityUtils.toString(document))).readObject();

        assertEquals("Key title doesn't match to given value!", "Rendering",
            actual.getString(TemplateTypeField.TITLE.getName()));
        assertEquals("Key outputName doesn't match to given value!", "Render",
            actual.getString(TemplateTypeField.OUTPUT_NAME.getName()));
        assertEquals("Key wikiField doesn't match to given value!", "Field",
            actual.getString(TemplateTypeField.WIKI_FIELD.getName()));
        assertEquals("Key creationDate doesn't match to given value!", dateFormat.format(template.getCreationDate()),
            actual.getString(TemplateTypeField.CREATION_DATE.getName()));
        assertEquals("Key sortHelperStatus doesn't match to given value!", "",
            actual.getString(TemplateTypeField.SORT_HELPER_STATUS.getName()));
        assertEquals("Key project.id doesn't match to given value!", 1,
            actual.getInt(TemplateTypeField.PROJECT_ID.getName()));
        assertEquals("Key project.title doesn't match to given value!", "Project",
            actual.getString(TemplateTypeField.PROJECT_TITLE.getName()));
        assertTrue("Key project.active doesn't match to given value!",
            actual.getBoolean(TemplateTypeField.PROJECT_ACTIVE.getName()));
        assertEquals("Key docket doesn't match to given value!", 1, actual.getInt(TemplateTypeField.DOCKET.getName()));
        assertEquals("Key ruleset doesn't match to given value!", 0,
            actual.getInt(TemplateTypeField.RULESET.getName()));

        JsonArray tasks = actual.getJsonArray(TemplateTypeField.TASKS.getName());
        assertEquals("Size tasks doesn't match to given value!", 0, tasks.size());
    }

    @Test
    public void shouldCreateThirdDocument() throws Exception {
        TemplateType templateType = new TemplateType();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        Template template = prepareData().get(2);
        HttpEntity document = templateType.createDocument(template);

        JsonObject actual = Json.createReader(new StringReader(EntityUtils.toString(document))).readObject();

        assertEquals("Key title doesn't match to given value!", "Incomplete",
            actual.getString(TemplateTypeField.TITLE.getName()));
        assertEquals("Key outputName doesn't match to given value!", "",
            actual.getString(TemplateTypeField.OUTPUT_NAME.getName()));
        assertEquals("Key wikiField doesn't match to given value!", "",
            actual.getString(TemplateTypeField.WIKI_FIELD.getName()));
        assertEquals("Key creationDate doesn't match to given value!", dateFormat.format(template.getCreationDate()),
            actual.getString(TemplateTypeField.CREATION_DATE.getName()));
        assertEquals("Key sortHelperStatus doesn't match to given value!", "",
            actual.getString(TemplateTypeField.SORT_HELPER_STATUS.getName()));
        assertEquals("Key project.id doesn't match to given value!", 0,
            actual.getInt(TemplateTypeField.PROJECT_ID.getName()));
        assertEquals("Key project.title doesn't match to given value!", "",
            actual.getString(TemplateTypeField.PROJECT_TITLE.getName()));
        assertFalse("Key project.active doesn't match to given value!",
            actual.getBoolean(TemplateTypeField.PROJECT_ACTIVE.getName()));
        assertEquals("Key docket doesn't match to given value!", 0, actual.getInt(TemplateTypeField.DOCKET.getName()));
        assertEquals("Key ruleset doesn't match to given value!", 0,
            actual.getInt(TemplateTypeField.RULESET.getName()));

        JsonArray tasks = actual.getJsonArray(TemplateTypeField.TASKS.getName());
        assertEquals("Size tasks doesn't match to given value!", 0, tasks.size());
    }

    @Test
    public void shouldCreateDocumentWithCorrectAmountOfKeys() throws Exception {
        TemplateType templateType = new TemplateType();

        Template template = prepareData().get(0);
        HttpEntity document = templateType.createDocument(template);

        JsonObject actual = Json.createReader(new StringReader(EntityUtils.toString(document))).readObject();
        assertEquals("Amount of keys is incorrect!", 11, actual.keySet().size());

        JsonArray tasks = actual.getJsonArray(TemplateTypeField.TASKS.getName());
        JsonObject task = tasks.getJsonObject(0);
        assertEquals("Amount of keys in tasks is incorrect!", 2, task.keySet().size());
    }

    @Test
    public void shouldCreateDocuments() {
        TemplateType templateType = new TemplateType();

        List<Template> templates = prepareData();
        Map<Integer, HttpEntity> documents = templateType.createDocuments(templates);
        assertEquals("HashMap of documents doesn't contain given amount of elements!", 3, documents.size());
    }
}
