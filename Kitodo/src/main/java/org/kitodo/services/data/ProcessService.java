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

package org.kitodo.services.data;

import com.sun.research.ws.wadl.HTTPMethods;

import de.sub.goobi.config.ConfigCore;
import de.sub.goobi.config.ConfigProjects;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.exceptions.InvalidImagesException;
import de.sub.goobi.metadaten.MetadatenHelper;
import de.sub.goobi.metadaten.MetadatenSperrung;
import de.sub.goobi.metadaten.MetadatenVerifizierung;
import de.sub.goobi.metadaten.copier.CopierData;
import de.sub.goobi.metadaten.copier.DataCopier;
import de.sub.goobi.persistence.apache.FolderInformation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.faces.context.FacesContext;
import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.ConfigurationException;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.goobi.production.cli.helper.WikiFieldHelper;
import org.hibernate.Hibernate;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.json.simple.JSONObject;
import org.kitodo.api.docket.DocketData;
import org.kitodo.api.docket.DocketInterface;
import org.kitodo.api.filemanagement.ProcessSubType;
import org.kitodo.api.filemanagement.filters.FileNameBeginsAndEndsWithFilter;
import org.kitodo.api.filemanagement.filters.FileNameEndsAndDoesNotBeginWithFilter;
import org.kitodo.data.database.beans.Batch;
import org.kitodo.data.database.beans.Batch.Type;
import org.kitodo.data.database.beans.Docket;
import org.kitodo.data.database.beans.History;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Project;
import org.kitodo.data.database.beans.ProjectFileGroup;
import org.kitodo.data.database.beans.Property;
import org.kitodo.data.database.beans.Ruleset;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.beans.Template;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.beans.Workpiece;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.database.helper.MetadataHelper;
import org.kitodo.data.database.helper.enums.IndexAction;
import org.kitodo.data.database.helper.enums.MetadataFormat;
import org.kitodo.data.database.helper.enums.TaskStatus;
import org.kitodo.data.database.persistence.ProcessDAO;
import org.kitodo.data.elasticsearch.exceptions.CustomResponseException;
import org.kitodo.data.elasticsearch.index.Indexer;
import org.kitodo.data.elasticsearch.index.type.ProcessType;
import org.kitodo.data.elasticsearch.search.Searcher;
import org.kitodo.data.elasticsearch.search.enums.SearchCondition;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.serviceloader.KitodoServiceLoader;
import org.kitodo.services.ServiceManager;
import org.kitodo.services.data.base.TitleSearchService;
import org.kitodo.services.file.FileService;

import ugh.dl.ContentFile;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.dl.VirtualFileGroup;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.WriteException;
import ugh.fileformats.excel.RDFFile;
import ugh.fileformats.mets.MetsMods;
import ugh.fileformats.mets.MetsModsImportExport;
import ugh.fileformats.mets.XStream;

public class ProcessService extends TitleSearchService<Process> {

    private ProcessDAO processDAO = new ProcessDAO();
    private ProcessType processType = new ProcessType();
    private final MetadatenSperrung msp = new MetadatenSperrung();
    private final ServiceManager serviceManager = new ServiceManager();
    private final FileService fileService = serviceManager.getFileService();
    private static final Logger logger = LogManager.getLogger(ProcessService.class);
    private static final String TEMPORARY_FILENAME_PREFIX = "temporary_";

    private static String DIRECTORY_PREFIX = "orig";
    private static String DIRECTORY_SUFFIX = "images";

    /**
     * Constructor with Searcher and Indexer assigning.
     */
    public ProcessService() {
        super(new Searcher(Process.class));
        this.indexer = new Indexer<>(Process.class);
    }

    public Process find(Integer id) throws DAOException {
        return processDAO.find(id);
    }

    public List<Process> findAll() {
        return processDAO.findAll();
    }

    /**
     * Method saves process object to database.
     *
     * @param process
     *            object
     */
    public void saveToDatabase(Process process) throws DAOException {
        processDAO.save(process, getProgress(process));
    }

    /**
     * Method saves process document to the index of Elastic Search.
     *
     * @param process
     *            object
     */
    @SuppressWarnings("unchecked")
    public void saveToIndex(Process process) throws CustomResponseException, IOException {
        indexer.setMethod(HTTPMethods.PUT);
        if (process != null) {
            indexer.performSingleRequest(process, processType);
        }
    }

    /**
     * Method saves or removes batches, tasks and project related to modified
     * process.
     *
     * @param process
     *            object
     */
    protected void manageDependenciesForIndex(Process process)
            throws CustomResponseException, DAOException, DataException, IOException {
        manageBatchesDependenciesForIndex(process);
        manageProjectDependenciesForIndex(process);
        manageTaskDependenciesForIndex(process);
        manageTemplatesDependenciesForIndex(process);
        manageWorkpiecesDependenciesForIndex(process);
    }

    /**
     * Check if IndexAction flag is delete. If true remove process from list of
     * processes and re-save batch, if false only re-save batch object.
     *
     * @param process
     *            object
     */
    private void manageBatchesDependenciesForIndex(Process process) throws CustomResponseException, IOException {
        if (process.getIndexAction() == IndexAction.DELETE) {
            for (Batch batch : process.getBatches()) {
                batch.getProcesses().remove(process);
                serviceManager.getBatchService().saveToIndex(batch);
            }
        } else {
            for (Batch batch : process.getBatches()) {
                serviceManager.getBatchService().saveToIndex(batch);
            }
        }
    }

    /**
     * Add process to project, if project is assigned to process.
     *
     * @param process
     *            object
     */
    private void manageProjectDependenciesForIndex(Process process) throws CustomResponseException, IOException {
        if (process.getProject() != null) {
            serviceManager.getProjectService().saveToIndex(process.getProject());
        }
    }

    /**
     * Check IndexAction flag in for process object. If DELETE remove all tasks
     * from index, if other call saveOrRemoveTaskInIndex() method.
     *
     * @param process
     *            object
     */
    private void manageTaskDependenciesForIndex(Process process)
            throws CustomResponseException, DAOException, IOException, DataException {
        if (process.getIndexAction() == IndexAction.DELETE) {
            for (Task task : process.getTasks()) {
                serviceManager.getTaskService().removeFromIndex(task);
            }
        } else {
            saveOrRemoveTasksInIndex(process);
        }
    }

    /**
     * Compare index and database, according to comparisons results save or
     * remove tasks.
     *
     * @param process
     *            object
     */
    private void saveOrRemoveTasksInIndex(Process process)
            throws CustomResponseException, DAOException, IOException, DataException {
        List<Integer> database = new ArrayList<>();
        List<Integer> index = new ArrayList<>();

        for (Task task : process.getTasks()) {
            database.add(task.getId());
            serviceManager.getTaskService().saveToIndex(task);
        }

        List<JSONObject> searchResults = serviceManager.getTaskService().findByProcessId(process.getId());
        for (JSONObject object : searchResults) {
            Integer id = getIdFromJSONObject(object);
            index.add(id);
        }

        List<Integer> missingInIndex = findMissingValues(database, index);
        List<Integer> notNeededInIndex = findMissingValues(index, database);

        if (missingInIndex.size() > 0) {
            for (Integer missing : missingInIndex) {
                serviceManager.getTaskService().saveToIndex(serviceManager.getTaskService().find(missing));
            }
        }

        if (notNeededInIndex.size() > 0) {
            for (Integer notNeeded : notNeededInIndex) {
                serviceManager.getTaskService().removeFromIndex(notNeeded);
            }
        }
    }

    /**
     * Remove template if process is removed, add template if process is marked
     * as template.
     *
     * @param process
     *            object
     */
    private void manageTemplatesDependenciesForIndex(Process process) throws CustomResponseException, IOException {
        if (process.getIndexAction() == IndexAction.DELETE) {
            for (Template template : process.getTemplates()) {
                serviceManager.getTemplateService().removeFromIndex(template);
            }
        } else {
            for (Template template : process.getTemplates()) {
                serviceManager.getTemplateService().saveToIndex(template);
                saveDependantProperties(template.getProperties());
            }
        }
    }

    /**
     * Remove workpiece if process is removed, add workpiece if process is
     * marked as workpiece.
     *
     * @param process
     *            object
     */
    private void manageWorkpiecesDependenciesForIndex(Process process) throws CustomResponseException, IOException {
        if (process.getIndexAction() == IndexAction.DELETE) {
            for (Workpiece workpiece : process.getWorkpieces()) {
                serviceManager.getWorkpieceService().removeFromIndex(workpiece);
            }
        } else {
            for (Workpiece workpiece : process.getWorkpieces()) {
                serviceManager.getWorkpieceService().saveToIndex(workpiece);
                saveDependantProperties(workpiece.getProperties());
            }
        }
    }

    /**
     * Compare two list and return difference between them.
     *
     * @param firstList
     *            list from which records can be remove
     * @param secondList
     *            records stored here will be removed from firstList
     * @return difference between two lists
     */
    private List<Integer> findMissingValues(List<Integer> firstList, List<Integer> secondList) {
        List<Integer> newList = new ArrayList<>(firstList);
        newList.removeAll(secondList);
        return newList;
    }

    /**
     * Save to index dependant properties.
     *
     * @param properties
     *            List
     */
    private void saveDependantProperties(List<Property> properties) throws CustomResponseException, IOException {
        for (Property property : properties) {
            serviceManager.getPropertyService().saveToIndex(property);
        }
    }

    /**
     * Save list of processes to database.
     *
     * @param list
     *            of processes
     */
    public void saveList(List<Process> list) throws DAOException {
        processDAO.saveList(list);
    }

    /**
     * Method removes process object from database.
     *
     * @param process
     *            object
     */
    public void removeFromDatabase(Process process) throws DAOException {
        processDAO.remove(process);
    }

    /**
     * Method removes process object from database.
     *
     * @param id
     *            of process object
     */
    public void removeFromDatabase(Integer id) throws DAOException {
        processDAO.remove(id);
    }

    /**
     * Method removes process object from index of Elastic Search.
     *
     * @param process
     *            object
     */
    @SuppressWarnings("unchecked")
    public void removeFromIndex(Process process) throws CustomResponseException, IOException {
        indexer.setMethod(HTTPMethods.DELETE);
        if (process != null) {
            indexer.performSingleRequest(process, processType);
        }
    }

    public List<Process> search(String query) throws DAOException {
        return processDAO.search(query);
    }

    /**
     * Count all processes.
     *
     * @return amount of all processes
     */
    public Long count() throws DataException {
        return searcher.countDocuments();
    }

    /**
     * Count processes according to given query.
     *
     * @param query
     *            for index search
     * @return amount of processes according to given query
     */
    public Long count(String query) throws DataException {
        return searcher.countDocuments(query);
    }

    public void refresh(Process process) {
        processDAO.refresh(process);
    }

    /**
     * Find processes by output name.
     *
     * @param outputName
     *            as String
     * @return list of JSON objects
     */
    public List<JSONObject> findByOutputName(String outputName) throws DataException {
        QueryBuilder query = createSimpleQuery("outputName", outputName, true, Operator.AND);
        return searcher.findDocuments(query.toString());
    }

    /**
     * Find processes for exact creation date.
     *
     * @param creationDate
     *            of the searched processes as Date
     * @param searchCondition
     *            as SearchCondition - bigger, smaller and so on
     * @return list of JSON objects
     */
    public List<JSONObject> findByCreationDate(Date creationDate, SearchCondition searchCondition)
            throws DataException {
        QueryBuilder query = createSimpleCompareDateQuery("creationDate", creationDate, searchCondition);
        return searcher.findDocuments(query.toString());
    }

    /**
     * Find processes by wiki field.
     *
     * @param wikiField
     *            as String
     * @return list of JSON objects
     */
    public List<JSONObject> findByWikiField(String wikiField) throws DataException {
        QueryBuilder query = createSimpleQuery("wikiField", wikiField, true, Operator.AND);
        return searcher.findDocuments(query.toString());
    }

    /**
     * Find processes if template.
     *
     * @param template
     *            as Boolean
     * @return list of search results
     */
    public List<JSONObject> findByTemplate(Boolean template) throws DataException {
        QueryBuilder query = createSimpleQuery("template", template, true);
        System.out.println(query.toString());
        return searcher.findDocuments(query.toString());
    }

    /**
     * Find processes by id of project.
     *
     * @param id
     *            of project
     * @return list of JSON objects with processes for specific process id
     */
    public List<JSONObject> findByProjectId(Integer id) throws DataException {
        QueryBuilder query = createSimpleQuery("project", id, true);
        return searcher.findDocuments(query.toString());
    }

    /**
     * Find processes by docket.
     *
     * @param docket
     *            of project
     * @return list of JSON objects with processes for specific docket
     */
    public List<JSONObject> findByDocket(Docket docket) throws DataException {
        QueryBuilder query = createSimpleQuery("docket_id", docket.getId(), true);
        return searcher.findDocuments(query.toString());
    }

    /**
     * Find processes by ruleset.
     *
     * @param ruleset
     *            of project
     * @return list of JSON objects with processes for specific ruleset
     */
    public List<JSONObject> findByRuleset(Ruleset ruleset) throws DataException {
        QueryBuilder query = createSimpleQuery("ruleset", ruleset.getId(), true);
        return searcher.findDocuments(query.toString());
    }

    /**
     * Find processes by title of project.
     *
     * @param title
     *            of process
     * @return list of JSON objects with processes for specific process id
     */
    public List<JSONObject> findByProjectTitle(String title) throws DataException {
        List<JSONObject> processes = new ArrayList<>();

        List<JSONObject> projects = serviceManager.getProjectService().findByTitle(title, true);
        for (JSONObject project : projects) {
            processes.addAll(findByProjectId(getIdFromJSONObject(project)));
        }
        return processes;
    }

    /**
     * Find processes by id of batch.
     *
     * @param id
     *            of process
     * @return list of JSON objects with processes for specific batch id
     */
    public List<JSONObject> findByBatchId(Integer id) throws DataException {
        QueryBuilder query = createSimpleQuery("batches.id", id, true);
        return searcher.findDocuments(query.toString());
    }

    /**
     * Find processes by title of batch.
     *
     * @param title
     *            of batch
     * @return list of JSON objects with processes for specific batch title
     */
    public List<JSONObject> findByBatchTitle(String title) throws DataException {
        List<JSONObject> processes = new ArrayList<>();

        List<JSONObject> batches = serviceManager.getBatchService().findByTitle(title, true);
        for (JSONObject batch : batches) {
            processes.addAll(findByBatchId(getIdFromJSONObject(batch)));
        }
        return processes;
    }

    /**
     * Find processes by property.
     *
     * @param title
     *            of property
     * @param value
     *            of property
     * @return list of JSON objects with processes for specific property
     */
    public List<JSONObject> findByProperty(String title, String value) throws DataException {
        List<JSONObject> processes = new ArrayList<>();

        List<JSONObject> properties = serviceManager.getPropertyService().findByTitleAndValue(title, value);
        for (JSONObject property : properties) {
            processes.addAll(findByPropertyId(getIdFromJSONObject(property)));
        }
        return processes;
    }

    /**
     * Simulate relationship between property and process type.
     *
     * @param id
     *            of property
     * @return list of JSON objects with processes for specific property id
     */
    private List<JSONObject> findByPropertyId(Integer id) throws DataException {
        QueryBuilder query = createSimpleQuery("properties.id", id, true);
        return searcher.findDocuments(query.toString());
    }

    /**
     * Method adds all object found in database to Elastic Search index.
     */
    @SuppressWarnings("unchecked")
    public void addAllObjectsToIndex() throws CustomResponseException, InterruptedException, IOException {
        indexer.setMethod(HTTPMethods.PUT);
        indexer.performMultipleRequests(findAll(), processType);
    }

    /**
     * Get title without white spaces.
     * 
     * @param process
     *            object
     * @return title with '__' instead of ' '
     */
    public String getNormalizedTitle(Process process) {
        return process.getTitle().replace(" ", "__");
    }

    /**
     * Returns the batches of the desired type for a process.
     *
     * @param type
     *            of batches to return
     * @return all batches of the desired type
     */
    public List<Batch> getBatchesByType(Process process, Type type) {
        List<Batch> batches = getBatchesInitialized(process);
        if (type != null) {
            List<Batch> result = new ArrayList<>(batches);
            Iterator<Batch> indicator = result.iterator();
            while (indicator.hasNext()) {
                if (!type.equals(indicator.next().getType())) {
                    indicator.remove();
                }
            }
            return result;
        }
        return batches;
    }

    /**
     * The function getBatchesInitialized() returns the batches for a process
     * and takes care that the object is initialized from Hibernate already and
     * will not be bothered if the Hibernate session ends. TODO: check if it is
     * necessary!!
     *
     * @return the batches field of the process which is loaded
     */
    public List<Batch> getBatchesInitialized(Process process) {
        if (process.getId() != null) {
            Hibernate.initialize(process.getBatches());
        }
        return process.getBatches();
    }

    /**
     * The function getHistoryInitialized() returns the history events for a
     * process and takes care that the object is initialized from Hibernate
     * already and will not be bothered if the Hibernate session ends. TODO:
     * check if it is necessary!!
     *
     * @return the history field of the process which is loaded
     */
    public List<History> getHistoryInitialized(Process process) {
        try {
            @SuppressWarnings("unused")
            Session s = Helper.getHibernateSession();
            Hibernate.initialize(process.getHistory());
        } catch (HibernateException e) {
            logger.debug("Hibernate exception: ", e);
        }
        if (process.getHistory() == null) {
            process.setHistory(new ArrayList<>());
        }
        return process.getHistory();
    }

    /**
     * The function getPropertiesInitialized() returns the descriptive fields
     * (“properties”) for a process and takes care that the object is
     * initialized from Hibernate already and will not be bothered if the
     * Hibernate session ends. TODO: check if it is necessary!! <- e.g.
     * BeanHelper uses it
     *
     * @return the properties field of the process which is loaded
     */
    public List<Property> getPropertiesInitialized(Process process) {
        try {
            Hibernate.initialize(process.getProperties());
        } catch (HibernateException e) {
            logger.debug("Hibernate exception: ", e);
        }
        return process.getProperties();
    }

    /**
     * Get blocked user.
     *
     * @return blocked metadata (user)
     */
    public User getBlockedUsers(Process process) {
        User result = null;
        if (MetadatenSperrung.isLocked(process.getId())) {
            String userID = this.msp.getLockBenutzer(process.getId());
            try {
                result = serviceManager.getUserService().find(Integer.valueOf(userID));
            } catch (Exception e) {
                Helper.setFehlerMeldung(Helper.getTranslation("userNotFound"), e);
            }
        }
        return result;
    }

    public long getBlockedMinutes(Process process) {
        return this.msp.getLockSekunden(process.getId()) / 60;
    }

    public long getBlockedSeconds(Process process) {
        return this.msp.getLockSekunden(process.getId()) % 60;
    }

    /**
     * Get directory for tig images.
     *
     * @param useFallBack
     *            add description
     * @param process
     *            object
     * @return tif directory
     */
    public URI getImagesTifDirectory(boolean useFallBack, Process process) throws IOException {
        URI dir = fileService.getProcessSubTypeURI(process, ProcessSubType.IMAGE, null);
        DIRECTORY_SUFFIX = ConfigCore.getParameter("DIRECTORY_SUFFIX", "tif");
        DIRECTORY_PREFIX = ConfigCore.getParameter("DIRECTORY_PREFIX", "orig");
        /* nur die _tif-Ordner anzeigen, die nicht mir orig_ anfangen */
        FilenameFilter filterDirectory = new FileNameEndsAndDoesNotBeginWithFilter(DIRECTORY_PREFIX + "_",
                "_" + DIRECTORY_SUFFIX);
        URI tifOrdner = null;
        ArrayList<URI> directories = fileService.getSubUris(filterDirectory, dir);
        for (URI directory : directories) {
            tifOrdner = directory;
        }

        if (tifOrdner == null && useFallBack) {
            String suffix = ConfigCore.getParameter("MetsEditorDefaultSuffix", "");
            if (!suffix.equals("")) {
                ArrayList<URI> folderList = fileService.getSubUrisForProcess(null, process, ProcessSubType.IMAGE, "");
                for (URI folder : folderList) {
                    if (folder.toString().endsWith(suffix)) {
                        tifOrdner = folder;
                        break;
                    }
                }
            }
        }

        if (!(tifOrdner == null) && useFallBack) {
            String suffix = ConfigCore.getParameter("MetsEditorDefaultSuffix", "");
            if (!suffix.equals("")) {
                URI tif = tifOrdner;
                ArrayList<URI> files = fileService.getSubUris(tif);
                if (files == null || files.size() == 0) {
                    ArrayList<URI> folderList = fileService.getSubUris(dir);
                    for (URI folder : folderList) {
                        if (folder.toString().endsWith(suffix) && !folder.getPath().startsWith(DIRECTORY_PREFIX)) {
                            tifOrdner = folder;
                            break;
                        }
                    }
                }
            }
        }

        URI result = fileService.getProcessSubTypeURI(process, ProcessSubType.IMAGE, null);

        if (tifOrdner == null) {
            tifOrdner = URI.create(result.toString() + getNormalizedTitle(process) + "_" + DIRECTORY_SUFFIX);
        }

        if (!ConfigCore.getBooleanParameter("useOrigFolder", true)
                && ConfigCore.getBooleanParameter("createOrigFolderIfNotExists", false)) {
            fileService.createMetaDirectory(result, tifOrdner.toString());
        }

        return tifOrdner;
    }

    /**
     * Check if Tif directory exists.
     * 
     * @return true if the Tif-Image-Directory exists, false if not
     */
    public Boolean checkIfTifDirectoryExists(Process process) {
        URI testMe;
        try {
            testMe = getImagesTifDirectory(true, process);
            return fileService.getSubUris(testMe) != null && fileService.fileExist(testMe)
                    && fileService.getSubUris(testMe).size() > 0;
        } catch (IOException e) {
            logger.error(e);
            return false;
        }

    }

    /**
     * Get images origin directory.
     *
     * @param useFallBack
     *            as boolean
     * @param process
     *            object
     * @return path
     */
    public URI getImagesOrigDirectory(boolean useFallBack, Process process) throws IOException {
        if (ConfigCore.getBooleanParameter("useOrigFolder", true)) {
            URI dir = fileService.getProcessSubTypeURI(process, ProcessSubType.IMAGE, null);
            DIRECTORY_SUFFIX = ConfigCore.getParameter("DIRECTORY_SUFFIX", "tif");
            DIRECTORY_PREFIX = ConfigCore.getParameter("DIRECTORY_PREFIX", "orig");
            /* nur die _tif-Ordner anzeigen, die mit orig_ anfangen */
            FilenameFilter filterDirectory = new FileNameBeginsAndEndsWithFilter(DIRECTORY_PREFIX + "_",
                    "_" + DIRECTORY_SUFFIX);
            URI origOrdner = null;
            ArrayList<URI> verzeichnisse = fileService.getSubUris(filterDirectory, dir);
            for (URI aVerzeichnisse : verzeichnisse) {
                origOrdner = aVerzeichnisse;
            }

            if (origOrdner == null && useFallBack) {
                String suffix = ConfigCore.getParameter("MetsEditorDefaultSuffix", "");
                if (!suffix.equals("")) {
                    ArrayList<URI> folderList = fileService.getSubUris(dir);
                    for (URI folder : folderList) {
                        if (folder.toString().endsWith(suffix)) {
                            origOrdner = folder;
                            break;
                        }
                    }
                }
            }

            if (!(origOrdner == null) && useFallBack) {
                String suffix = ConfigCore.getParameter("MetsEditorDefaultSuffix", "");
                if (!suffix.equals("")) {
                    URI tif = origOrdner;
                    ArrayList<URI> files = fileService.getSubUris(tif);
                    if (files == null || files.size() == 0) {
                        ArrayList<URI> folderList = fileService.getSubUris(dir);
                        for (URI folder : folderList) {
                            if (folder.toString().endsWith(suffix)) {
                                origOrdner = folder;
                                break;
                            }
                        }
                    }
                }
            }

            URI result = fileService.getProcessSubTypeURI(process, ProcessSubType.IMAGE, null);

            if (origOrdner == null) {
                origOrdner = URI.create(result.toString() + DIRECTORY_PREFIX + "_" + getNormalizedTitle(process) + "_"
                        + DIRECTORY_SUFFIX);
            }

            if (ConfigCore.getBooleanParameter("createOrigFolderIfNotExists", false)
                    && process.getSortHelperStatus().equals("100000000")) {
                fileService.createMetaDirectory(result, origOrdner.toString());
            }

            return origOrdner;
        } else {
            return getImagesTifDirectory(useFallBack, process);
        }
    }

    /**
     * Get process data directory.
     *
     * @param process
     *            object
     * @return path
     */
    public URI getProcessDataDirectory(Process process) {
        if (process.getProcessBaseUri() == null) {
            process.setProcessBaseUri(fileService.getProcessBaseUriForExistingProcess(process));
            try {
                save(process);
            } catch (DataException e) {
                logger.error(e);
                return URI.create("");
            }
        }
        return process.getProcessBaseUri();
    }

    /**
     * The function getBatchID returns the batches the process is associated
     * with as readable text as read-only property "batchID".
     *
     * @return the batches the process is in
     */
    public String getBatchID(Process process) {
        if (process.getBatches() == null || process.getBatches().size() == 0) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        BatchService batchService = new BatchService();
        for (Batch batch : process.getBatches()) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(batchService.getLabel(batch));
        }
        return result.toString();
    }

    /**
     * Get size of tasks' list.
     *
     * @param process
     *            object
     * @return size
     */
    public int getTasksSize(Process process) {
        if (process.getTasks() == null) {
            return 0;
        } else {
            return process.getTasks().size();
        }
    }

    /**
     * Get size of histories' list.
     *
     * @param process
     *            object
     * @return size
     */
    public int getHistorySize(Process process) {
        if (process.getHistory() == null) {
            return 0;
        } else {
            return process.getHistory().size();
        }
    }

    /**
     * Get size of properties' list.
     *
     * @param process
     *            object
     * @return size
     */
    public int getPropertiesSize(Process process) {
        if (process.getProperties() == null) {
            return 0;
        } else {
            return process.getProperties().size();
        }
    }

    /**
     * Get size of workpieces' list.
     *
     * @param process
     *            object
     * @return size
     */
    public int getWorkpiecesSize(Process process) {
        if (process.getWorkpieces() == null) {
            return 0;
        } else {
            return process.getWorkpieces().size();
        }
    }

    /**
     * Get size of templates' list.
     *
     * @param process
     *            object
     * @return size
     */
    public int getTemplatesSize(Process process) {
        if (process.getTemplates() == null) {
            return 0;
        } else {
            return process.getTemplates().size();
        }
    }

    /**
     * Get current task.
     *
     * @param process
     *            object
     * @return current task
     */
    public Task getCurrentTask(Process process) {
        for (Task task : process.getTasks()) {
            if (task.getProcessingStatusEnum() == TaskStatus.OPEN
                    || task.getProcessingStatusEnum() == TaskStatus.INWORK) {
                return task;
            }
        }
        return null;
    }

    public String getCreationDateAsString(Process process) {
        return Helper.getDateAsFormattedString(process.getCreationDate());
    }

    /**
     * Get full progress for process.
     *
     * @param process
     *            object
     * @return string
     */
    public String getProgress(Process process) {
        HashMap<String, Integer> tasks = calculationForProgress(process);

        double closed = (tasks.get("closed") * 100)
                / (double) (tasks.get("closed") + tasks.get("inProcessing") + tasks.get("open") + tasks.get("locked"));
        double inProcessing = (tasks.get("inProcessing") * 100)
                / (double) (tasks.get("closed") + tasks.get("inProcessing") + tasks.get("open") + tasks.get("locked"));
        double open = (tasks.get("open") * 100)
                / (double) (tasks.get("closed") + tasks.get("inProcessing") + tasks.get("open") + tasks.get("locked"));
        double locked = (tasks.get("locked") * 100)
                / (double) (tasks.get("closed") + tasks.get("inProcessing") + tasks.get("open") + tasks.get("locked"));

        DecimalFormat decimalFormat = new DecimalFormat("#000");
        return decimalFormat.format(closed) + decimalFormat.format(inProcessing) + decimalFormat.format(open)
                + decimalFormat.format(locked);
    }

    /**
     * Get progress for closed tasks.
     *
     * @param process
     *            object
     * @return progress for closed steps
     */
    public int getProgressClosed(Process process) {
        HashMap<String, Integer> tasks = calculationForProgress(process);

        return (tasks.get("closed") * 100)
                / (tasks.get("closed") + tasks.get("inProcessing") + tasks.get("open") + tasks.get("locked"));
    }

    /**
     * Get progress for processed tasks.
     *
     * @param process
     *            object
     * @return progress for processed tasks
     */
    public int getProgressInProcessing(Process process) {
        HashMap<String, Integer> tasks = calculationForProgress(process);

        return (tasks.get("inProcessing") * 100)
                / (tasks.get("closed") + tasks.get("inProcessing") + tasks.get("open") + tasks.get("locked"));
    }

    /**
     * Get progress for open tasks.
     *
     * @param process
     *            object
     * @return return progress for open tasks
     */
    public int getProgressOpen(Process process) {
        HashMap<String, Integer> tasks = calculationForProgress(process);
        return (tasks.get("open") * 100)
                / (tasks.get("closed") + tasks.get("inProcessing") + tasks.get("open") + tasks.get("locked"));
    }

    /**
     * Get progress for open tasks.
     *
     * @param process
     *            object
     * @return return progress for open tasks
     */
    public int getProgressLocked(Process process) {
        HashMap<String, Integer> tasks = calculationForProgress(process);
        return (tasks.get("locked") * 100)
                / (tasks.get("closed") + tasks.get("inProcessing") + tasks.get("open") + tasks.get("locked"));
    }

    private HashMap<String, Integer> calculationForProgress(Process process) {
        HashMap<String, Integer> results = new HashMap<>();
        int open = 0;
        int inProcessing = 0;
        int closed = 0;
        int locked = 0;
        Hibernate.initialize(process.getTasks());
        for (Task task : process.getTasks()) {
            if (task.getProcessingStatusEnum() == TaskStatus.DONE) {
                closed++;
            } else if (task.getProcessingStatusEnum() == TaskStatus.OPEN) {
                open++;
            } else if (task.getProcessingStatusEnum() == TaskStatus.LOCKED) {
                locked++;
            } else {
                inProcessing++;
            }
        }

        results.put("closed", closed);
        results.put("inProcessing", inProcessing);
        results.put("open", open);
        results.put("locked", locked);

        if ((open + inProcessing + closed + locked) == 0) {
            results.put("locked", 1);
        }

        return results;
    }

    /**
     * Get full text file path.
     *
     * @param process
     *            object
     * @return path as a String to the full text file
     */
    public String getFulltextFilePath(Process process) {
        return getProcessDataDirectory(process) + "/fulltext.xml";
    }

    /**
     * Read metadata file.
     *
     * @param process
     *            object
     * @return filer format
     */
    public Fileformat readMetadataFile(Process process) throws ReadException, IOException, PreferencesException {
        URI metadataFileUri = serviceManager.getFileService().getMetadataFilePath(process);
        if (!checkForMetadataFile(process)) {
            throw new IOException(Helper.getTranslation("metadataFileNotFound") + " " + metadataFileUri);
        }
        Hibernate.initialize(process.getRuleset());
        /* prüfen, welches Format die Metadaten haben (Mets, xstream oder rdf */
        String type = MetadatenHelper.getMetaFileType(metadataFileUri);
        if (logger.isDebugEnabled()) {
            logger.debug("current meta.xml file type for id " + process.getId() + ": " + type);
        }

        Fileformat ff = determineFileFormat(type, process);
        try {
            ff.read(serviceManager.getFileService().getFile(metadataFileUri).toString());
        } catch (ReadException e) {
            if (e.getMessage().startsWith("Parse error at line -1")) {
                Helper.setFehlerMeldung("metadataCorrupt");
            } else {
                throw e;
            }
        }
        return ff;
    }

    private Fileformat determineFileFormat(String type, Process process) throws PreferencesException {
        Fileformat fileFormat = null;
        RulesetService rulesetService = new RulesetService();

        switch (type) {
            case "metsmods":
                fileFormat = new MetsModsImportExport(rulesetService.getPreferences(process.getRuleset()));
                break;
            case "mets":
                fileFormat = new MetsMods(rulesetService.getPreferences(process.getRuleset()));
                break;
            case "xstream":
                fileFormat = new XStream(rulesetService.getPreferences(process.getRuleset()));
                break;
            default:
                fileFormat = new RDFFile(rulesetService.getPreferences(process.getRuleset()));
                break;
        }
        return fileFormat;
    }

    private boolean checkForMetadataFile(Process process) {
        return fileService.fileExist(fileService.getMetadataFilePath(process));
    }

    /**
     * Read metadata as template file.
     *
     * @param process
     *            object
     * @return file format
     */
    public Fileformat readMetadataAsTemplateFile(Process process)
            throws ReadException, IOException, PreferencesException {
        RulesetService rulesetService = new RulesetService();
        Hibernate.initialize(process.getRuleset());
        if (new File(fileService.getProcessSubTypeURI(process, ProcessSubType.TEMPLATE, null)).exists()) {
            Fileformat ff = null;
            String type = MetadatenHelper
                    .getMetaFileType(fileService.getProcessSubTypeURI(process, ProcessSubType.TEMPLATE, null));
            if (logger.isDebugEnabled()) {
                logger.debug("current template.xml file type: " + type);
            }
            ff = determineFileFormat(type, process);
            ff.read(fileService.getProcessSubTypeURI(process, ProcessSubType.TEMPLATE, null).toString());
            return ff;
        } else {
            throw new IOException(
                    "File does not exist: " + fileService.getProcessSubTypeURI(process, ProcessSubType.TEMPLATE, null));
        }
    }

    /**
     * Check whether the operation contains steps that are not assigned to a
     * user or user group.
     */
    public boolean getContainsUnreachableSteps(Process process) {
        TaskService taskService = serviceManager.getTaskService();
        if (process.getTasks().size() == 0) {
            return true;
        }
        for (Task task : process.getTasks()) {
            if (taskService.getUserGroupsSize(task) == 0 && taskService.getUsersSize(task) == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if there is one task in edit mode, where the user has the rights to
     * write to image folder.
     */
    public boolean isImageFolderInUse(Process process) {
        for (Task task : process.getTasks()) {
            if (task.getProcessingStatusEnum() == TaskStatus.INWORK && task.isTypeImagesWrite()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get user of task in edit mode with rights to write to image folder.
     */
    public User getImageFolderInUseUser(Process process) {
        for (Task task : process.getTasks()) {
            if (task.getProcessingStatusEnum() == TaskStatus.INWORK && task.isTypeImagesWrite()) {
                return task.getProcessingUser();
            }
        }
        return null;
    }

    /**
     * Download docket.
     *
     * @param process
     *            object
     */
    public void downloadDocket(Process process) throws IOException {

        if (logger.isDebugEnabled()) {
            logger.debug("generate docket for process " + process.getId());
        }
        URI rootPath = Paths.get(ConfigCore.getParameter("xsltFolder")).toUri();
        URI xsltFile;
        if (process.getDocket() != null) {
            xsltFile = serviceManager.getFileService().createResource(rootPath, process.getDocket().getFile());
            if (!fileService.fileExist(xsltFile)) {
                Helper.setFehlerMeldung("docketMissing");
            }
        } else {
            xsltFile = serviceManager.getFileService().createResource(rootPath, "docket.xsl");
        }
        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (!facesContext.getResponseComplete()) {

            // write run note to servlet output stream
            DocketInterface module = initialiseDocketModule();

            File file = module.generateDocket(getDocketData(process), xsltFile);
            writeToOutputStream(facesContext, file, process.getTitle() + ".pdf");
        }
    }

    /**
     * Writes a multipage docket for a list of processes to an outpustream.
     * 
     * @param processes
     *            The list of processes
     * @throws IOException
     *             when xslt file could not be loaded, or write to output
     *             failed.
     */
    public void downloadDocket(List<Process> processes) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("generate docket for processes " + processes);
        }
        URI rootPath = Paths.get(ConfigCore.getParameter("xsltFolder")).toUri();
        URI xsltFile = serviceManager.getFileService().createResource(rootPath, "docket_multipage.xsl");
        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (!facesContext.getResponseComplete()) {

            DocketInterface module = initialiseDocketModule();
            File file = module.generateMultipleDockets(serviceManager.getProcessService().getDocketData(processes),
                    xsltFile);

            writeToOutputStream(facesContext, file, "batch_docket.pdf");
        }
    }

    private void writeToOutputStream(FacesContext facesContext, File file, String fileName) throws IOException {
        HttpServletResponse response = (HttpServletResponse) facesContext.getExternalContext().getResponse();
        ServletContext servletContext = (ServletContext) facesContext.getExternalContext().getContext();
        String contentType = servletContext.getMimeType(fileName);
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "attachment;filename=\"" + fileName + "\"");

        ServletOutputStream outputStream = response.getOutputStream();
        byte[] bytes = IOUtils.toByteArray(new FileInputStream(file));
        outputStream.write(bytes);
        outputStream.flush();
        facesContext.responseComplete();
    }

    private DocketInterface initialiseDocketModule() {
        KitodoServiceLoader<DocketInterface> loader = new KitodoServiceLoader<>(DocketInterface.class,
                ConfigCore.getParameter("moduleFolder"));
        return loader.loadModule();
    }

    /**
     * Get first open task for the process.
     *
     * @param process
     *            object
     * @return first open task
     */
    public Task getFirstOpenStep(Process process) {
        for (Task step : process.getTasks()) {
            if (step.getProcessingStatusEnum().equals(TaskStatus.OPEN)
                    || step.getProcessingStatusEnum().equals(TaskStatus.INWORK)) {
                return step;
            }
        }
        return null;
    }

    /**
     * Get method from name.
     *
     * @param methodName
     *            string
     * @param process
     *            object
     * @return method from name
     */
    public URI getMethodFromName(String methodName, Process process) {
        java.lang.reflect.Method method;
        try {
            method = this.getClass().getMethod(methodName);
            Object o = method.invoke(this);
            return (URI) o;
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
                | SecurityException e) {
            logger.debug("exception: " + e);
        }
        try {
            URI folder = this.getImagesTifDirectory(false, process);
            String folderName = fileService.getFileName(folder);
            folderName = folderName.substring(0, folderName.lastIndexOf("_"));
            folderName = folderName + "_" + methodName;
            folder = fileService.renameFile(folder, folderName);
            if (fileService.fileExist(folder)) {
                return folder;
            }
        } catch (IOException ex) {
            logger.debug("exception: " + ex);
        }
        return null;
    }

    /*
     * public List<String> getPossibleDigitalCollections(Process process) throws
     * JDOMException, IOException { return
     * DigitalCollections.possibleDigitalCollectionsForProcess(process); }
     */

    /**
     * The addMessageToWikiField() method is a helper method which composes the
     * new wiki field using a StringBuilder. The message is encoded using HTML
     * entities to prevent certain characters from playing merry havoc when the
     * message box shall be rendered in a browser later.
     *
     * @param message
     *            the message to append
     */
    public Process addToWikiField(String message, Process process) {
        StringBuilder composer = new StringBuilder();
        if (process.getWikiField() != null && process.getWikiField().length() > 0) {
            composer.append(process.getWikiField());
            composer.append("\r\n");
        }
        composer.append("<p>");
        composer.append(StringEscapeUtils.escapeHtml(message));
        composer.append("</p>");
        process.setWikiField(composer.toString());

        return process;
    }

    /**
     * The method addToWikiField() adds a message with a given level to the wiki
     * field of the process. Four level strings will be recognized and result in
     * different colors:
     *
     * <dl>
     * <dt><code>debug</code></dt>
     * <dd>gray</dd>
     * <dt><code>error</code></dt>
     * <dd>red</dd>
     * <dt><code>user</code></dt>
     * <dd>green</dd>
     * <dt><code>warn</code></dt>
     * <dd>orange</dd>
     * <dt><i>any other value</i></dt>
     * <dd>blue</dd>
     * <dt>
     *
     * @param level
     *            message colour, one of: "debug", "error", "info", "user" or
     *            "warn"; any other value defaults to "info"
     * @param message
     *            text
     */
    public String addToWikiField(String level, String message, Process process) {
        return WikiFieldHelper.getWikiMessage(process, process.getWikiField(), level, message);
    }

    /**
     * The method addToWikiField() adds a message signed by the given user to
     * the wiki field of the process.
     *
     * @param user
     *            to sign the message with
     * @param message
     *            to print
     */
    public void addToWikiField(User user, String message, Process process) {
        String text = message + " (" + user.getSurname() + ")";
        // addToWikiField("user", process, text);
    }

    /**
     * The method createProcessDirs() starts creation of directories configured
     * by parameter processDirs within kitodo_config.properties
     */
    public void createProcessDirs(Process process) throws IOException {

        String[] processDirs = ConfigCore.getStringArrayParameter("processDirs");

        for (String processDir : processDirs) {
            fileService.createMetaDirectory(this.getProcessDataDirectory(process),
                    processDir.replace("(processtitle)", process.getTitle()));
        }

    }

    /**
     * The function getDigitalDocument() returns the digital act of this
     * process.
     *
     * @return the digital act of this process
     * @throws PreferencesException
     *             if the no node corresponding to the file format is available
     *             in the rule set configured
     * @throws ReadException
     *             if the meta data file cannot be read
     * @throws IOException
     *             if creating the process directory or reading the meta data
     *             file fails
     */
    public DigitalDocument getDigitalDocument(Process process) throws PreferencesException, ReadException, IOException {
        return readMetadataFile(process).getDigitalDocument();
    }

    /**
     * Filter for correction / solution messages.
     *
     * @param lpe
     *            List of process properties
     * @return List of filtered correction / solution messages
     */
    protected List<Property> filterForCorrectionSolutionMessages(List<Property> lpe) {
        ArrayList<Property> filteredList = new ArrayList<>();
        List<String> listOfTranslations = new ArrayList<>();
        String propertyTitle = "";

        listOfTranslations.add("Korrektur notwendig");
        listOfTranslations.add("Korrektur durchgefuehrt");
        listOfTranslations.add(Helper.getTranslation("Korrektur notwendig"));
        listOfTranslations.add(Helper.getTranslation("Korrektur durchgefuehrt"));

        if ((lpe == null) || (lpe.size() == 0)) {
            return filteredList;
        }

        // filtering for correction and solution messages
        for (Property property : lpe) {
            propertyTitle = property.getTitle();
            if (listOfTranslations.contains(propertyTitle)) {
                filteredList.add(property);
            }
        }
        return filteredList;
    }

    /**
     * Filter and sort after creation date list of process properties for
     * correction and solution messages.
     *
     * @return list of ProcessProperty objects
     */
    public List<Property> getSortedCorrectionSolutionMessages(Process process) {
        List<Property> filteredList;
        List<Property> lpe = process.getProperties();

        if (lpe.isEmpty()) {
            return new ArrayList<>();
        }

        filteredList = filterForCorrectionSolutionMessages(lpe);

        // sorting after creation date
        Collections.sort(filteredList, new Comparator<Property>() {
            @Override
            public int compare(Property o1, Property o2) {
                Date o1Date = o1.getCreationDate();
                Date o2Date = o2.getCreationDate();
                if (o1Date == null) {
                    o1Date = new Date();
                }
                if (o2Date == null) {
                    o2Date = new Date();
                }
                return o1Date.compareTo(o2Date);
            }
        });

        return new ArrayList<>(filteredList);
    }

    public Long getNumberOfProcessesWithTitle(String title) throws DataException {
        return count(createSimpleQuery("title", title, true, Operator.AND).toString());
    }

    /**
     * Reads the metadata File.
     *
     * @param metadataFile
     *            The given metadataFile.
     * @param prefs
     *            The Preferences
     * @return The fileFormat.
     */
    public Fileformat readMetadataFile(URI metadataFile, Prefs prefs)
            throws IOException, PreferencesException, ReadException {
        /* prüfen, welches Format die Metadaten haben (Mets, xstream oder rdf */
        String type = MetadataHelper.getMetaFileType(metadataFile);
        Fileformat ff;
        switch (type) {
            case "metsmods":
                ff = new MetsModsImportExport(prefs);
                break;
            case "mets":
                ff = new MetsMods(prefs);
                break;
            case "xstream":
                ff = new XStream(prefs);
                break;
            default:
                ff = new RDFFile(prefs);
                break;
        }
        ff.read(metadataFile.getPath());

        return ff;
    }

    /**
     * DMS-Export an eine gewünschte Stelle.
     *
     * @param process
     *            object
     */

    public boolean startDmsExport(Process process, boolean exportWithImages, boolean exportFullText) throws IOException,
            PreferencesException, org.apache.commons.configuration.ConfigurationException, WriteException {
        Prefs preferences = serviceManager.getRulesetService().getPreferences(process.getRuleset());

        Project project = process.getProject();

        ConfigProjects configProjects = new ConfigProjects(project.getTitle());
        String atsPpnBand = process.getTitle();

        /*
         * Dokument einlesen
         */
        Fileformat gdzfile;
        Fileformat newfile;
        FolderInformation fi = new FolderInformation(process.getId(), process.getTitle());
        try {
            URI metadataPath = fi.getMetadataFilePath();
            gdzfile = readMetadataFile(metadataPath, preferences);
            switch (MetadataFormat.findFileFormatsHelperByName(project.getFileFormatDmsExport())) {
                case METS:
                    newfile = new MetsModsImportExport(preferences);
                    break;
                case METS_AND_RDF:
                default:
                    newfile = new RDFFile(preferences);
                    break;
            }

            newfile.setDigitalDocument(gdzfile.getDigitalDocument());
            gdzfile = newfile;

        } catch (Exception e) {
            Helper.setFehlerMeldung(Helper.getTranslation("exportError") + process.getTitle(), e);
            logger.error("Export abgebrochen, xml-LeseFehler", e);
            return false;
        }

        String rules = ConfigCore.getParameter("copyData.onExport");
        if (rules != null && !rules.equals("- keine Konfiguration gefunden -")) {
            try {
                new DataCopier(rules).process(new CopierData(newfile, process));
            } catch (ConfigurationException e) {
                Helper.setFehlerMeldung("dataCopier.syntaxError", e.getMessage());
                return false;
            } catch (RuntimeException e) {
                Helper.setFehlerMeldung("dataCopier.runtimeException", e.getMessage());
                return false;
            }
        }

        trimAllMetadata(gdzfile.getDigitalDocument().getLogicalDocStruct());

        /*
         * Metadaten validieren
         */

        if (ConfigCore.getBooleanParameter("useMetadatenvalidierung")) {
            MetadatenVerifizierung mv = new MetadatenVerifizierung();
            if (!mv.validate(gdzfile, preferences, process)) {
                return false;

            }
        }

        /*
         * Speicherort vorbereiten und downloaden
         */
        URI zielVerzeichnis;
        URI benutzerHome;

        zielVerzeichnis = new File(project.getDmsImportImagesPath()).toURI();
        benutzerHome = zielVerzeichnis;

        /* ggf. noch einen Vorgangsordner anlegen */
        if (project.isDmsImportCreateProcessFolder()) {
            zielVerzeichnis = benutzerHome.resolve(File.separator + process.getTitle());
            /* alte Import-Ordner löschen */
            if (!fileService.delete(benutzerHome)) {
                Helper.setFehlerMeldung("Export canceled, Process: " + process.getTitle(),
                        "Import folder could not be cleared");
                return false;
            }
            /* alte Success-Ordner löschen */
            File successFile = new File(project.getDmsImportSuccessPath() + File.separator + process.getTitle());
            if (!fileService.delete(successFile.toURI())) {
                Helper.setFehlerMeldung("Export canceled, Process: " + process.getTitle(),
                        "Success folder could not be cleared");
                return false;
            }
            /* alte Error-Ordner löschen */
            File errorfile = new File(project.getDmsImportErrorPath() + File.separator + process.getTitle());
            if (!fileService.delete(errorfile.toURI())) {
                Helper.setFehlerMeldung("Export canceled, Process: " + process.getTitle(),
                        "Error folder could not be cleared");
                return false;
            }

            if (!fileService.fileExist(benutzerHome)) {
                fileService.createDirectory(benutzerHome, File.separator + process.getTitle());
            }
        }

        /*
         * der eigentliche Download der Images
         */
        try {
            if (exportWithImages) {
                imageDownload(process, benutzerHome, atsPpnBand, DIRECTORY_SUFFIX, fi);
                fulltextDownload(benutzerHome, atsPpnBand, fi);
            } else if (exportFullText) {
                fulltextDownload(benutzerHome, atsPpnBand, fi);
            }

            directoryDownload(process, zielVerzeichnis);
        } catch (Exception e) {
            Helper.setFehlerMeldung("Export canceled, Process: " + process.getTitle(), e);
            return false;
        }

        /*
         * zum Schluss Datei an gewünschten Ort exportieren entweder direkt in
         * den Import-Ordner oder ins Benutzerhome anschliessend den
         * Import-Thread starten
         */
        if (project.isUseDmsImport()) {
            if (MetadataFormat.findFileFormatsHelperByName(project.getFileFormatDmsExport()) == MetadataFormat.METS) {
                /* Wenn METS, dann per writeMetsFile schreiben... */
                writeMetsFile(process, benutzerHome + File.separator + atsPpnBand + ".xml", gdzfile, false);
            } else {
                /* ...wenn nicht, nur ein Fileformat schreiben. */
                gdzfile.write(benutzerHome + File.separator + atsPpnBand + ".xml");
            }

            /* ggf. sollen im Export mets und rdf geschrieben werden */
            if (MetadataFormat
                    .findFileFormatsHelperByName(project.getFileFormatDmsExport()) == MetadataFormat.METS_AND_RDF) {
                writeMetsFile(process, benutzerHome + File.separator + atsPpnBand + ".mets.xml", gdzfile, false);
            }

            Helper.setMeldung(null, process.getTitle() + ": ", "DMS-Export started");

            if (!ConfigCore.getBooleanParameter("exportWithoutTimeLimit")) {
                /* Success-Ordner wieder löschen */
                if (project.isDmsImportCreateProcessFolder()) {
                    File successFile = new File(
                            project.getDmsImportSuccessPath() + File.separator + process.getTitle());
                    fileService.delete(successFile.toURI());
                }
            }
        }
        return true;
    }

    /**
     * Run through all metadata and children of given docstruct to trim the
     * strings calls itself recursively.
     */
    private void trimAllMetadata(DocStruct inStruct) {
        /* trim all metadata values */
        if (inStruct.getAllMetadata() != null) {
            for (ugh.dl.Metadata md : inStruct.getAllMetadata()) {
                if (md.getValue() != null) {
                    md.setValue(md.getValue().trim());
                }
            }
        }

        /* run through all children of docstruct */
        if (inStruct.getAllChildren() != null) {
            for (DocStruct child : inStruct.getAllChildren()) {
                trimAllMetadata(child);
            }
        }
    }

    /**
     * Download full text.
     *
     * @param userHome
     *            safe file
     * @param atsPpnBand
     *            String
     */
    private void fulltextDownload(URI userHome, String atsPpnBand, FolderInformation fi) throws IOException {

        // download sources
        URI sources = fi.getSourceDirectory();
        if (fileService.fileExist(sources) && fileService.getSubUris(sources).size() > 0) {
            URI destination = userHome.resolve(File.separator + atsPpnBand + "_src");
            if (!fileService.fileExist(destination)) {
                fileService.createDirectory(userHome, atsPpnBand + "_src");
            }
            ArrayList<URI> dateien = fileService.getSubUris(sources);
            for (URI aDateien : dateien) {
                if (fileService.isFile(aDateien)) {
                    URI meinZiel = destination.resolve(File.separator + fileService.getFileNameWithExtension(aDateien));
                    fileService.copyFile(aDateien, meinZiel);
                }
            }
        }

        URI ocr = fi.getOcrDirectory();
        if (fileService.fileExist(ocr)) {
            ArrayList<URI> folder = fileService.getSubUris(ocr);
            for (URI dir : folder) {
                if (fileService.isDirectory(dir) && fileService.getSubUris(dir).size() > 0
                        && fileService.getFileName(dir).contains("_")) {
                    String suffix = fileService.getFileNameWithExtension(dir)
                            .substring(fileService.getFileNameWithExtension(dir).lastIndexOf("_"));
                    URI destination = userHome.resolve(File.separator + atsPpnBand + suffix);
                    if (!fileService.fileExist(destination)) {
                        fileService.createDirectory(userHome, atsPpnBand + suffix);
                    }
                    ArrayList<URI> files = fileService.getSubUris(dir);
                    for (URI file : files) {
                        if (fileService.isFile(file)) {
                            URI target = destination
                                    .resolve(File.separator + fileService.getFileNameWithExtension(file));
                            fileService.copyFile(file, target);
                        }
                    }
                }
            }
        }
    }

    /**
     * Download image.
     *
     * @param process
     *            process object
     * @param userHome
     *            safe file
     * @param atsPpnBand
     *            String
     * @param ordnerEndung
     *            String
     */
    public void imageDownload(Process process, URI userHome, String atsPpnBand, final String ordnerEndung,
            FolderInformation fi) throws IOException {

        Project project = process.getProject();
        /*
         * den Ausgangspfad ermitteln
         */
        URI tifOrdner = fi.getImagesTifDirectory(true);

        /*
         * jetzt die Ausgangsordner in die Zielordner kopieren
         */
        if (fileService.fileExist(tifOrdner) && fileService.getSubUris(tifOrdner).size() > 0) {
            URI zielTif = userHome.resolve(File.separator + atsPpnBand + ordnerEndung);

            /* bei Agora-Import einfach den Ordner anlegen */
            if (project.isUseDmsImport()) {
                if (!fileService.fileExist(zielTif)) {
                    fileService.createDirectory(userHome, atsPpnBand + ordnerEndung);
                }
            } else {
                /*
                 * wenn kein Agora-Import, dann den Ordner mit
                 * Benutzerberechtigung neu anlegen
                 */
                User myUser = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
                try {
                    fileService.createDirectoryForUser(zielTif, myUser.getLogin());
                } catch (Exception e) {
                    Helper.setFehlerMeldung("Export canceled, error", "could not create destination directory");
                    logger.error("could not create destination directory", e);
                }
            }

            /* jetzt den eigentlichen Kopiervorgang */

            ArrayList<URI> dateien = fileService.getSubUris(Helper.dataFilter, tifOrdner);
            for (URI file : dateien) {
                if (fileService.isFile(file)) {
                    URI target = zielTif.resolve(File.separator + fileService.getFileNameWithExtension(file));
                    fileService.copyFile(file, target);
                }
            }
        }
    }

    /**
     * write MetsFile to given Path.
     *
     * @param process
     *            the Process to use
     * @param targetFileName
     *            the filename where the metsfile should be written
     * @param gdzfile
     *            the FileFormat-Object to use for Mets-Writing
     */
    protected boolean writeMetsFile(Process process, String targetFileName, Fileformat gdzfile,
            boolean writeLocalFilegroup) throws PreferencesException, IOException, WriteException {
        FolderInformation fi = new FolderInformation(process.getId(), process.getTitle());
        Prefs preferences = serviceManager.getRulesetService().getPreferences(process.getRuleset());
        Project project = process.getProject();
        MetsModsImportExport mm = new MetsModsImportExport(preferences);
        mm.setWriteLocal(writeLocalFilegroup);
        URI imageFolderPath = fi.getImagesDirectory();
        File imageFolder = new File(imageFolderPath);
        /*
         * before creating mets file, change relative path to absolute -
         */
        DigitalDocument dd = gdzfile.getDigitalDocument();
        if (dd.getFileSet() == null) {
            Helper.setFehlerMeldung(process.getTitle() + ": digital document does not contain images; aborting");
            return false;
        }

        /*
         * get the topstruct element of the digital document depending on anchor
         * property
         */
        DocStruct topElement = dd.getLogicalDocStruct();
        if (preferences.getDocStrctTypeByName(topElement.getType().getName()).getAnchorClass() != null) {
            if (topElement.getAllChildren() == null || topElement.getAllChildren().size() == 0) {
                throw new PreferencesException(process.getTitle()
                        + ": the topstruct element is marked as anchor, but does not have any children for "
                        + "physical docstrucs");
            } else {
                topElement = topElement.getAllChildren().get(0);
            }
        }

        /*
         * if the top element does not have any image related, set them all
         */
        if (topElement.getAllToReferences("logical_physical") == null
                || topElement.getAllToReferences("logical_physical").size() == 0) {
            if (dd.getPhysicalDocStruct() != null && dd.getPhysicalDocStruct().getAllChildren() != null) {
                Helper.setMeldung(process.getTitle()
                        + ": topstruct element does not have any referenced images yet; temporarily adding them "
                        + "for mets file creation");
                for (DocStruct mySeitenDocStruct : dd.getPhysicalDocStruct().getAllChildren()) {
                    topElement.addReferenceTo(mySeitenDocStruct, "logical_physical");
                }
            } else {
                Helper.setFehlerMeldung(process.getTitle() + ": could not find any referenced images, export aborted");
                return false;
            }
        }

        for (ContentFile cf : dd.getFileSet().getAllFiles()) {
            String location = cf.getLocation();
            // If the file's location string shoes no sign of any protocol,
            // use the file protocol.
            if (!location.contains("://")) {
                location = "file://" + location;
            }
            String url = new URL(location).getFile();
            File f = new File(!url.startsWith(imageFolder.toURL().getPath()) ? imageFolder : null, url);
            cf.setLocation(f.toURI().toString());
        }

        mm.setDigitalDocument(dd);

        /*
         * wenn Filegroups definiert wurden, werden diese jetzt in die
         * Metsstruktur übernommen
         */
        // Replace all paths with the given VariableReplacer, also the file
        // group paths!
        VariableReplacer vp = new VariableReplacer(mm.getDigitalDocument(), preferences, process, null);
        List<ProjectFileGroup> myFilegroups = project.getProjectFileGroups();

        if (myFilegroups != null && myFilegroups.size() > 0) {
            for (ProjectFileGroup pfg : myFilegroups) {
                // check if source files exists
                if (pfg.getFolder() != null && pfg.getFolder().length() > 0) {
                    URI folder = new File(fi.getMethodFromName(pfg.getFolder())).toURI();
                    if (fileService.fileExist(folder)
                            && serviceManager.getFileService().getSubUris(folder).size() > 0) {
                        VirtualFileGroup v = new VirtualFileGroup();
                        v.setName(pfg.getName());
                        v.setPathToFiles(vp.replace(pfg.getPath()));
                        v.setMimetype(pfg.getMimeType());
                        v.setFileSuffix(pfg.getSuffix());
                        mm.getDigitalDocument().getFileSet().addVirtualFileGroup(v);
                    }
                } else {

                    VirtualFileGroup v = new VirtualFileGroup();
                    v.setName(pfg.getName());
                    v.setPathToFiles(vp.replace(pfg.getPath()));
                    v.setMimetype(pfg.getMimeType());
                    v.setFileSuffix(pfg.getSuffix());
                    mm.getDigitalDocument().getFileSet().addVirtualFileGroup(v);
                }
            }
        }

        // Replace rights and digiprov entries.
        mm.setRightsOwner(vp.replace(project.getMetsRightsOwner()));
        mm.setRightsOwnerLogo(vp.replace(project.getMetsRightsOwnerLogo()));
        mm.setRightsOwnerSiteURL(vp.replace(project.getMetsRightsOwnerSite()));
        mm.setRightsOwnerContact(vp.replace(project.getMetsRightsOwnerMail()));
        mm.setDigiprovPresentation(vp.replace(project.getMetsDigiprovPresentation()));
        mm.setDigiprovReference(vp.replace(project.getMetsDigiprovReference()));
        mm.setDigiprovPresentationAnchor(vp.replace(project.getMetsDigiprovPresentationAnchor()));
        mm.setDigiprovReferenceAnchor(vp.replace(project.getMetsDigiprovReferenceAnchor()));

        mm.setPurlUrl(vp.replace(project.getMetsPurl()));
        mm.setContentIDs(vp.replace(project.getMetsContentIDs()));

        // Set mets pointers. MetsPointerPathAnchor or mptrAnchorUrl is the
        // pointer used to point to the superordinate (anchor) file, that is
        // representing a “virtual” group such as a series. Several anchors
        // pointer paths can be defined/ since it is possible to define several
        // levels of superordinate structures (such as the complete edition of
        // a daily newspaper, one year ouf of that edition, …)
        String anchorPointersToReplace = project.getMetsPointerPath();
        mm.setMptrUrl(null);
        for (String anchorPointerToReplace : anchorPointersToReplace.split(Project.ANCHOR_SEPARATOR)) {
            String anchorPointer = vp.replace(anchorPointerToReplace);
            mm.setMptrUrl(anchorPointer);
        }

        // metsPointerPathAnchor or mptrAnchorUrl is the pointer used to point
        // from the (lowest) superordinate (anchor) file to the lowest level
        // file (the non-anchor file).
        String anchor = project.getMetsPointerPathAnchor();
        String pointer = vp.replace(anchor);
        mm.setMptrAnchorUrl(pointer);

        try {
            // TODO andere Dateigruppen nicht mit image Namen ersetzen
            List<URI> images = fi.getDataFiles();
            List<String> imageStrings = new ArrayList<>();
            for (URI image : images) {
                imageStrings.add(image.getPath());
            }
            int sizeOfPagination = dd.getPhysicalDocStruct().getAllChildren().size();
            int sizeOfImages = images.size();
            if (sizeOfPagination == sizeOfImages) {
                dd.overrideContentFiles(imageStrings);
            } else {
                List<String> param = new ArrayList<>();
                param.add(String.valueOf(sizeOfPagination));
                param.add(String.valueOf(sizeOfImages));
                Helper.setFehlerMeldung(Helper.getTranslation("imagePaginationError", param));
                return false;
            }
        } catch (IndexOutOfBoundsException | InvalidImagesException e) {
            logger.error(e);
        }
        mm.write(targetFileName);
        Helper.setMeldung(null, process.getTitle() + ": ", "ExportFinished");
        return true;
    }

    /**
     * Starts copying all directories configured in kitodo_config.properties
     * parameter "processDirs" to export folder.
     *
     * @param myProcess
     *            the process object
     * @param targetDirectory
     *            the destination directory
     */
    private void directoryDownload(Process myProcess, URI targetDirectory) throws IOException {
        String[] processDirs = ConfigCore.getStringArrayParameter("processDirs");

        for (String processDir : processDirs) {
            URI sourceDirectory = URI.create(getProcessDataDirectory(myProcess).toString() + "/"
                    + processDir.replace("(processtitle)", myProcess.getTitle()));
            URI destinationDirectory = URI.create(
                    targetDirectory.toString() + "/" + processDir.replace("(processtitle)", myProcess.getTitle()));

            if (fileService.isDirectory(sourceDirectory)) {
                fileService.copyFile(sourceDirectory, destinationDirectory);
            }
        }
    }

    /**
     * Creates a List of Docket data for the given processes.
     * 
     * @param processes
     *            the process to create the docket data for.
     * @return A List of docketdata
     */
    public ArrayList<DocketData> getDocketData(List<Process> processes) {
        ArrayList<DocketData> docketdata = new ArrayList<>();
        for (Process process : processes) {
            docketdata.add(getDocketData(process));
        }
        return docketdata;
    }

    /**
     * Creates the DocketData for a given Process.
     * 
     * @param process
     *            The process to create the docket data for.
     * @return The DocketData for the process.
     */
    private DocketData getDocketData(Process process) {
        DocketData docketdata = new DocketData();

        docketdata.setCreationDate(process.getCreationDate().toString());
        docketdata.setProcessId(process.getId().toString());
        docketdata.setProcessName(process.getTitle());
        docketdata.setProjectName(process.getProject().getTitle());
        docketdata.setRulesetName(process.getRuleset().getTitle());
        docketdata.setComment(process.getWikiField());

        if (!process.getTemplates().isEmpty() && process.getTemplates().get(0) != null) {
            docketdata.setTemplateProperties(getDocketDataForProperties(process.getTemplates().get(0).getProperties()));
        }
        if (!process.getWorkpieces().isEmpty() && process.getWorkpieces().get(0) != null) {
            docketdata
                    .setWorkpieceProperties(getDocketDataForProperties(process.getWorkpieces().get(0).getProperties()));
        }
        docketdata.setProcessProperties(getDocketDataForProperties(process.getProperties()));

        return docketdata;

    }

    private ArrayList<org.kitodo.api.docket.Property> getDocketDataForProperties(List<Property> properties) {
        ArrayList<org.kitodo.api.docket.Property> propertiesForDocket = new ArrayList<>();
        for (Property property : properties) {
            org.kitodo.api.docket.Property propertyForDocket = new org.kitodo.api.docket.Property();
            propertyForDocket.setId(property.getId());
            propertyForDocket.setTitle(property.getTitle());
            propertyForDocket.setValue(property.getValue());

            propertiesForDocket.add(propertyForDocket);

        }

        return propertiesForDocket;
    }

    /**
     * Gets all not archvived Processes
     *
     * @return The list of processes.
     */
    public List<Process> getNotArchivedProcesses() {
        return processDAO.getNotArchivedProcesses();
    }

    /**
     * Get not closed processes.
     *
     * @return The list of processes.
     */
    public List<Process> getNotClosedProcesses() {
        return processDAO.getNotClosedProcesses();
    }

    /**
     * Get not closed and not archived processes.
     *
     * @return The list of processes.
     */
    public List<Process> getNotClosedAndNotArchivedProcesses() {
        return processDAO.getNotClosedAndNotArchivedProcesses();
    }

    /**
     * get not archived templates.
     *
     * @return The list of processes.
     */
    public List<Process> getNotArchivedTemplates() {
        return processDAO.getNotArchivedTemplates();
    }

    /**
     * Get all templates.
     *
     * @return The list of processes.
     */
    public List<Process> getAllTemplates() {
        return processDAO.getAllTemplates();
    }

    /**
     * get all processes, which are not a template
     *
     * @return The list of processes.
     */
    public List<Process> getAllWithoutTemplates() {
        return processDAO.getAllWithoutTemplates();
    }

    /**
     * get all not archived processes which are not a template.
     *
     * @return The list of processes.
     */
    public List<Process> getAllNotArchivedWithoutTemplates() {
        return processDAO.getAllNotArchivedWithoutTemplates();
    }

    /**
     * Get all not closed and not archived templates.
     *
     * @return The list of processes.
     */
    public List<Process> getAllNotClosedAndNotArchivedTemplates() {
        return processDAO.getAllNotClosedAndNotArchivedTemplates();
    }

    /**
     * Get all not closed templates.
     *
     * @return The list of processes.
     */
    public List<Process> getAllNotClosedTemplates() {
        return processDAO.getAllNotClosedTemplates();
    }
}
