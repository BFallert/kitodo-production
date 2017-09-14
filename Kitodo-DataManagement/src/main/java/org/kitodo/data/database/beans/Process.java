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

package org.kitodo.data.database.beans;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.NONE)
// This annotation is to instruct the Jersey API not to generate arbitrary XML
// elements. Further XML elements can be
// added as needed by annotating with @XmlElement, but their respective names
// should be wisely chosen according to
// the Coding Guidelines (e.g. *english* names).
@Entity
@Table(name = "process")
public class Process extends BaseIndexedBean {

    private static final long serialVersionUID = -6503348094655786275L;

    @Column(name = "title")
    private String title;

    @Column(name = "outputName")
    private String outputName;

    @Column(name = "template")
    private Boolean template;

    @Column(name = "inChoiceListShown")
    private Boolean inChoiceListShown;

    @Column(name = "creationDate")
    private Date creationDate;

    @Column(name = "sortHelperStatus")
    private String sortHelperStatus;

    @Column(name = "sortHelperImages")
    private Integer sortHelperImages;

    @Column(name = "sortHelperArticles")
    private Integer sortHelperArticles;

    @Column(name = "sortHelperMetadata")
    private Integer sortHelperMetadata;

    @Column(name = "sortHelperDocstructs")
    private Integer sortHelperDocstructs;

    @Column(name = "wikiField", columnDefinition = "longtext")
    private String wikiField = "";

    @Column(name = "processBaseUri")
    private URI processBaseUri;

    @ManyToOne
    @JoinColumn(name = "docket_id", foreignKey = @ForeignKey(name = "FK_process_docket_id"))
    private Docket docket;

    @ManyToOne
    @JoinColumn(name = "project_id", foreignKey = @ForeignKey(name = "FK_process_project_id"))
    private Project project;

    @ManyToOne
    @JoinColumn(name = "ruleset_id", foreignKey = @ForeignKey(name = "FK_process_ruleset_id"))
    private Ruleset ruleset;

    @OneToMany(mappedBy = "process", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Task> tasks;

    @OneToMany(mappedBy = "process", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("date ASC")
    private List<History> history;

    @OneToMany(mappedBy = "process", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Workpiece> workpieces;

    @OneToMany(mappedBy = "process", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Template> templates;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "process_x_property", joinColumns = {
            @JoinColumn(name = "process_id", foreignKey = @ForeignKey(name = "FK_process_x_property_process_id")) }, inverseJoinColumns = {
                    @JoinColumn(name = "property_id", foreignKey = @ForeignKey(name = "FK_process_x_property_property_id")) })
    private List<Property> properties;

    @ManyToMany(mappedBy = "processes")
    private List<Batch> batches = new ArrayList<>();

    @Transient
    private Boolean panelShown = false;

    @Transient
    private Boolean selected = false;

    public static String DIRECTORY_SUFFIX = "images";

    /**
     * Constructor.
     */
    public Process() {
        this.title = "";
        this.template = false;
        this.inChoiceListShown = false;
        this.properties = new ArrayList<>();
        this.tasks = new ArrayList<>();
        this.creationDate = new Date();

    }

    @XmlAttribute(name = "key")
    public String getTitle() {
        return this.title;
    }

    public void setTitle(String inputTitle) {
        this.title = inputTitle.trim();
    }

    public String getOutputName() {
        return this.outputName;
    }

    public void setOutputName(String outputName) {
        this.outputName = outputName;
    }

    public boolean isTemplate() {
        if (this.template == null) {
            this.template = Boolean.FALSE;
        }
        return this.template;
    }

    public void setTemplate(boolean template) {
        this.template = template;
    }

    public boolean isInChoiceListShown() {
        return this.inChoiceListShown;
    }

    public void setInChoiceListShown(boolean inChoiceListShown) {
        this.inChoiceListShown = inChoiceListShown;
    }

    public String getSortHelperStatus() {
        return this.sortHelperStatus;
    }

    public void setSortHelperStatus(String sortHelperStatus) {
        this.sortHelperStatus = sortHelperStatus;
    }

    public Integer getSortHelperImages() {
        if (this.sortHelperImages == null) {
            this.sortHelperImages = 0;
        }
        return this.sortHelperImages;
    }

    public void setSortHelperImages(Integer sortHelperImages) {
        this.sortHelperImages = sortHelperImages;
    }

    public Integer getSortHelperArticles() {
        if (this.sortHelperArticles == null) {
            this.sortHelperArticles = 0;
        }
        return this.sortHelperArticles;
    }

    public void setSortHelperArticles(Integer sortHelperArticles) {
        this.sortHelperArticles = sortHelperArticles;
    }

    public Integer getSortHelperDocstructs() {
        if (this.sortHelperDocstructs == null) {
            this.sortHelperDocstructs = 0;
        }
        return this.sortHelperDocstructs;
    }

    public void setSortHelperDocstructs(Integer sortHelperDocstructs) {
        this.sortHelperDocstructs = sortHelperDocstructs;
    }

    public Integer getSortHelperMetadata() {
        if (this.sortHelperMetadata == null) {
            this.sortHelperMetadata = 0;
        }
        return this.sortHelperMetadata;
    }

    public void setSortHelperMetadata(Integer sortHelperMetadata) {
        this.sortHelperMetadata = sortHelperMetadata;
    }

    public Date getCreationDate() {
        return this.creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public String getWikiField() {
        return this.wikiField;
    }

    public void setWikiField(String wikiField) {
        this.wikiField = wikiField;
    }

    /**
     * Gets the process base URI.
     */
    public URI getProcessBaseUri() {
        return processBaseUri;
    }

    /**
     * Sets the process base URI.
     *
     * @param processBaseUri
     *            the given process base URI
     */
    public void setProcessBaseUri(URI processBaseUri) {
        this.processBaseUri = processBaseUri;
    }

    public Project getProject() {
        return this.project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Ruleset getRuleset() {
        return this.ruleset;
    }

    public void setRuleset(Ruleset ruleset) {
        this.ruleset = ruleset;
    }

    public Docket getDocket() {
        return docket;
    }

    public void setDocket(Docket docket) {
        this.docket = docket;
    }

    public List<Task> getTasks() {
        if (this.tasks == null) {
            this.tasks = new ArrayList<>();
        }
        return this.tasks;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }

    public List<History> getHistory() {
        if (this.history == null) {
            this.history = new ArrayList<>();
        }
        return this.history;
    }

    public void setHistory(List<History> history) {

        this.history = history;
    }

    public List<Template> getTemplates() {
        if (this.templates == null) {
            this.templates = new ArrayList<>();
        }
        return this.templates;

    }

    public void setTemplates(List<Template> templates) {
        this.templates = templates;
    }

    public List<Workpiece> getWorkpieces() {
        if (this.workpieces == null) {
            this.workpieces = new ArrayList<>();
        }
        return this.workpieces;
    }

    public void setWorkpieces(List<Workpiece> workpieces) {
        this.workpieces = workpieces;
    }

    public List<Batch> getBatches() {
        if (this.batches == null) {
            this.batches = new ArrayList<>();
        }
        return this.batches;
    }

    public void setBatches(List<Batch> batches) {
        this.batches = batches;
    }

    public List<Property> getProperties() {
        if (this.properties == null) {
            this.properties = new ArrayList<>();
        }
        return this.properties;
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }

    public boolean isPanelShown() {
        return this.panelShown;
    }

    public void setPanelShown(boolean panelShown) {
        this.panelShown = panelShown;
    }

    public boolean isSelected() {
        return (this.selected == null ? false : this.selected);
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    // Here will be methods which should be in ProcessService but are used by
    // jsp files

    /**
     * TODO: find way to set up this value from service.
     * @return null
     */
    public User getBlockedUsers() {
        User result = null;
        // too much dependencies on kitodo core
        return result;
    }

    public int getPropertiesSize() {
        return this.getProperties().size();
    }

    public int getWorkpiecesSize() {
        return this.getWorkpieces().size();
    }

    public int getTemplatesSize() {
        return this.getTemplates().size();
    }

    /**
     * The function getBatchID returns the batches the process is associated
     * with as readable text as read-only property "batchID".
     *
     * @return the batches the process is in
     */
    public String getBatchID() {
        if (this.getBatches().size() == 0) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        for (Batch batch : this.getBatches()) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(batch.getLabel());
        }
        return result.toString();
    }
}
