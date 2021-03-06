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

package org.kitodo.forms;

import de.sub.goobi.config.ConfigCore;
import de.sub.goobi.helper.Helper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import javax.enterprise.context.SessionScoped;
import javax.faces.context.FacesContext;
import javax.inject.Named;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.data.database.beans.Workflow;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.services.ServiceManager;
import org.kitodo.workflow.model.Reader;
import org.kitodo.workflow.model.beans.Diagram;

@Named("ModelerForm")
@SessionScoped
public class ModelerForm implements Serializable {
    private static final long serialVersionUID = -3635859478787639614L;
    private String svgDiagram;
    private String xmlDiagram;
    private String xmlDiagramName;
    private String newXMLDiagramName;
    private List<URI> xmlDiagramNamesURI;
    private Map<String, String> xmlDiagramNames = new TreeMap<>();
    private String diagramsFolder = ConfigCore.getKitodoDiagramDirectory();
    private static final String BPMN_EXTENSION = ".bpmn20.xml";
    private static final String BASE_FILE = "base" + BPMN_EXTENSION;
    private static final Logger logger = LogManager.getLogger(ModelerForm.class);
    private static final ServiceManager serviceManager = new ServiceManager();

    ModelerForm() {
        xmlDiagramNamesURI = serviceManager.getFileService().getSubUris(new File(diagramsFolder).toURI());
        // TODO: this needs to be removed after base file is stored inside the app
        for (URI uri : xmlDiagramNamesURI) {
            String fileName = serviceManager.getFileService().getFileNameWithExtension(uri);
            if (!fileName.equals(BASE_FILE) && !fileName.contains(".svg")) {
                xmlDiagramNames.put(decodeXMLDiagramName(fileName), fileName);
            }
        }
        readXMLDiagram(BASE_FILE);
    }

    /**
     * Get content of XML diagram file.
     * 
     * @return content of XML diagram file as String
     */
    public String getXmlDiagram() {
        return xmlDiagram;
    }

    /**
     * Set content of XML diagram file.
     * 
     * @param xmlDiagram
     *            content of XML diagram as String
     */
    public void setXmlDiagram(String xmlDiagram) {
        this.xmlDiagram = xmlDiagram;
    }

    /**
     * Get name of XML diagram file.
     * 
     * @return name of XML diagram file as String
     */
    public String getXmlDiagramName() {
        return xmlDiagramName;
    }

    /**
     * Set name of XML diagram file.
     * 
     * @param xmlDiagramName
     *            name of XML diagram file as String
     */
    public void setXmlDiagramName(String xmlDiagramName) {
        this.xmlDiagramName = xmlDiagramName;
    }

    /**
     * Get new name of XML diagram file as String. It is used for file creation.
     * 
     * @return new name of XML diagram file as String
     */
    public String getNewXMLDiagramName() {
        return newXMLDiagramName;
    }

    /**
     * Set new name of XML diagram file as String. It is used for file creation.
     * 
     * @param newXMLDiagramName
     *            new name of XML diagram file as String
     */
    public void setNewXMLDiagramName(String newXMLDiagramName) {
        this.newXMLDiagramName = newXMLDiagramName;
    }

    /**
     * Get List of URIs for collection of the names of XML diagrams.
     * 
     * @return List of URIs
     */
    public List<URI> getXmlDiagramNamesURI() {
        return xmlDiagramNamesURI;
    }

    /**
     * Set List of URIs for collection of the names of XML diagrams.
     * 
     * @param xmlDiagramNamesURI
     *            as List of URIs
     */
    public void setXmlDiagramNamesURI(List<URI> xmlDiagramNamesURI) {
        this.xmlDiagramNamesURI = xmlDiagramNamesURI;
    }

    /**
     * Get Map of XML diagrams' names. Key store the name without extension and
     * value store name with extenstion ".bpmn20.xml".
     * 
     * @return Map of Strings
     */
    public Map<String, String> getXmlDiagramNames() {
        return xmlDiagramNames;
    }

    /**
     * Set Map of XML diagrams' names. Key store the name without extension and
     * value store name with extenstion ".bpmn20.xml".
     * 
     * @param xmlDiagramNames
     *            as Map of Strings
     */
    public void setXmlDiagramNames(Map<String, String> xmlDiagramNames) {
        this.xmlDiagramNames = xmlDiagramNames;
    }

    /**
     * Create XML diagram. Method first checks if file already exists. If not it
     * loads the base diagram content, next create new file with given name, saves
     * content to this file, and at the end it opens it in the editor.
     */
    public void createXMLDiagram() {
        for (String value : xmlDiagramNames.values()) {
            if (value.equals(encodeXMLDiagramName(newXMLDiagramName))) {
                logger.error("Diagram with name \"" + newXMLDiagramName + "\" already exists!");
                return;
            }
        }
        // TODO: this one needs to be stored inside the WAR file
        readXMLDiagram(BASE_FILE);
        xmlDiagramName = newXMLDiagramName;
        newXMLDiagramName = "";
        try {
            serviceManager.getFileService().createResource(new File(diagramsFolder).toURI(),
                encodeXMLDiagramName(xmlDiagramName));
            xmlDiagramNames.put(decodeXMLDiagramName(xmlDiagramName), encodeXMLDiagramName(xmlDiagramName));
        } catch (IOException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
        }
        saveXMLDiagram();
        readXMLDiagram();
    }

    /**
     * Read XML for file chosen out of the select list.
     */
    public void readXMLDiagram() {
        readXMLDiagram(xmlDiagramName);
    }

    private void readXMLDiagram(String xmlDiagramName) {
        try (InputStream inputStream = serviceManager.getFileService()
                .read(new File(diagramsFolder + encodeXMLDiagramName(xmlDiagramName)).toURI());
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder sb = new StringBuilder();
            String line = bufferedReader.readLine();
            while (line != null) {
                sb.append(line).append("\n");
                line = bufferedReader.readLine();
            }
            xmlDiagram = sb.toString();
        } catch (IOException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
        }
    }

    /**
     * Save updated content of the diagram.
     */
    public void save() {
        Map<String, String> requestParameterMap = FacesContext.getCurrentInstance().getExternalContext()
                .getRequestParameterMap();
        svgDiagram = requestParameterMap.get("svg");
        if (Objects.nonNull(svgDiagram)) {
            saveSVGDiagram();
        }

        xmlDiagram = requestParameterMap.get("xml");
        if (Objects.nonNull(xmlDiagram)) {
            saveXMLDiagram();
            saveWorkflow();
        }
    }

    private String decodeXMLDiagramName(String xmlDiagramName) {
        if (xmlDiagramName.contains(BPMN_EXTENSION)) {
            return xmlDiagramName.replace(BPMN_EXTENSION, "");
        }
        return xmlDiagramName;

    }

    private String encodeXMLDiagramName(String xmlDiagramName) {
        if (!xmlDiagramName.contains(BPMN_EXTENSION)) {
            return xmlDiagramName + BPMN_EXTENSION;
        }
        return xmlDiagramName;
    }

    private void saveWorkflow() {
        String decodedXMLDiagramName = decodeXMLDiagramName(xmlDiagramName);
        try {
            Reader reader = new Reader(decodedXMLDiagramName);
            Diagram diagram = reader.getWorkflow();
            Workflow workflow = getWorkflow(diagram.getId(), decodedXMLDiagramName);
            if (isWorkflowAlreadyInUse(workflow)) {
                workflow.setActive(false);
                Workflow newWorkflow = new Workflow(diagram.getId(), decodedXMLDiagramName);
                serviceManager.getWorkflowService().save(newWorkflow);
            }
            serviceManager.getWorkflowService().save(workflow);
        } catch (DataException | IOException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
        }
    }

    private Workflow getWorkflow(String title, String file) {
        List<Workflow> workflows = serviceManager.getWorkflowService().getWorkflowsForTitleAndFile(title, file);

        if (workflows.isEmpty()) {
            return new Workflow(title, file);
        } else {
            if (workflows.size() == 1) {
                return workflows.get(0);
            } else {
                return getFirstActiveWorkflow(workflows);
            }
        }
    }

    private Workflow getFirstActiveWorkflow(List<Workflow> workflows) {
        for (Workflow workflow : workflows) {
            if (workflow.isActive()) {
                return workflow;
            }
        }
        throw new UnsupportedOperationException("There is not active workflow!");
    }

    private boolean isWorkflowAlreadyInUse(Workflow workflow) {
        return !workflow.getTemplates().isEmpty();
    }

    void saveSVGDiagram() {
        try (OutputStream outputStream = serviceManager.getFileService()
                .write(new File(diagramsFolder + decodeXMLDiagramName(xmlDiagramName) + ".svg").toURI());
                BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream))) {
            bufferedWriter.write(svgDiagram);
        } catch (IOException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
        }
    }

    void saveXMLDiagram() {
        try (OutputStream outputStream = serviceManager.getFileService()
                .write(new File(diagramsFolder + encodeXMLDiagramName(xmlDiagramName)).toURI());
                BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream))) {
            bufferedWriter.write(xmlDiagram);
        } catch (IOException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
        }
    }
}
