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

import java.io.IOException;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Property;
import org.kitodo.data.database.beans.Template;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.beans.Workpiece;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.database.persistence.PropertyDAO;
import org.kitodo.data.elasticsearch.exceptions.CustomResponseException;
import org.kitodo.data.elasticsearch.index.Indexer;
import org.kitodo.data.elasticsearch.index.type.PropertyType;
import org.kitodo.data.elasticsearch.search.Searcher;
import org.kitodo.services.ServiceManager;
import org.kitodo.services.data.base.TitleSearchService;

public class PropertyService extends TitleSearchService<Property> {

    private PropertyDAO propertyDAO = new PropertyDAO();
    private PropertyType propertyType = new PropertyType();
    private Indexer<Property, PropertyType> indexer = new Indexer<>(Property.class);
    private final ServiceManager serviceManager = new ServiceManager();
    private static final Logger logger = LogManager.getLogger(PropertyService.class);

    /**
     * Constructor with searcher's assigning.
     */
    public PropertyService() {
        super(new Searcher(Property.class));
    }

    /**
     * Method saves property object to database.
     *
     * @param property
     *            object
     */
    public void saveToDatabase(Property property) throws DAOException {
        propertyDAO.save(property);
    }

    /**
     * Method saves property document to the index of Elastic Search.
     *
     * @param property
     *            object
     */
    public void saveToIndex(Property property) throws CustomResponseException, IOException {
        indexer.setMethod(HTTPMethods.PUT);
        indexer.performSingleRequest(property, propertyType);
    }

    /**
     * Method saves processes related to modified batch.
     *
     * @param property
     *            object
     */
    protected void saveDependenciesToIndex(Property property) throws CustomResponseException, IOException {
        for (Process process : property.getProcesses()) {
            serviceManager.getProcessService().saveToIndex(process);
        }
        for (User user : property.getUsers()) {
            serviceManager.getUserService().saveToIndex(user);
        }
        for (Template template : property.getTemplates()) {
            serviceManager.getTemplateService().saveToIndex(template);
        }
        for (Workpiece workpiece : property.getWorkpieces()) {
            serviceManager.getWorkpieceService().saveToIndex(workpiece);
        }
    }

    /**
     * Find in database.
     * 
     * @param id
     *            as Integer
     * @return Property
     */
    public Property find(Integer id) throws DAOException {
        return propertyDAO.find(id);
    }

    /**
     * Find all properties in database.
     * 
     * @return list of all properties
     */
    public List<Property> findAll() throws DAOException {
        return propertyDAO.findAll();
    }

    /**
     * Search by query in database.
     * 
     * @param query
     *            as String
     * @return list of properties
     */
    public List<Property> search(String query) throws DAOException {
        return propertyDAO.search(query);
    }

    /**
     * Method removes property object from database.
     *
     * @param property
     *            object
     */
    public void removeFromDatabase(Property property) throws DAOException {
        propertyDAO.remove(property);
    }

    /**
     * Method removes property object from database.
     *
     * @param id
     *            of property object
     */
    public void removeFromDatabase(Integer id) throws DAOException {
        propertyDAO.remove(id);
    }

    /**
     * Method removes property object from index of Elastic Search.
     *
     * @param property
     *            object
     */
    public void removeFromIndex(Property property) throws CustomResponseException, IOException {
        indexer.setMethod(HTTPMethods.DELETE);
        indexer.performSingleRequest(property, propertyType);
    }

    /**
     * Get normalized title.
     * 
     * @param property
     *            object
     * @return normalized title
     */
    public String getNormalizedTitle(Property property) {
        return property.getTitle().replace(" ", "_").trim();
    }

    /**
     * Get normalized value.
     * 
     * @param property
     *            object
     * @return normalized value
     */
    public String getNormalizedValue(Property property) {
        return property.getValue().replace(" ", "_").trim();
    }
}
