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

package org.kitodo.dto;

/**
 * Ruleset DTO object.
 */
public class RulesetDTO extends BaseDTO {

    private String file;
    private String title;
    private String fileContent;
    private Boolean orderMetadataByRuleset = false;
    private Boolean active = true;

    /**
     * Get file.
     *
     * @return file as String
     */
    public String getFile() {
        return file;
    }

    /**
     * Set file.
     *
     * @param file
     *            as String
     */
    public void setFile(String file) {
        this.file = file;
    }

    /**
     * Get title.
     *
     * @return title as String
     */
    public String getTitle() {
        return title;
    }

    /**
     * Set title.
     *
     * @param title
     *            as String
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Get file content.
     * 
     * @return file content as String
     */
    public String getFileContent() {
        return fileContent;
    }

    /**
     * Set file content.
     * 
     * @param fileContent
     *            as String
     */
    public void setFileContent(String fileContent) {
        this.fileContent = fileContent;
    }

    /**
     * Check if order metadata by ruleset.
     * 
     * @return true or false
     */
    public boolean isOrderMetadataByRuleset() {
        return this.orderMetadataByRuleset;
    }

    /**
     * Set order metadata by ruleset.
     * 
     * @param orderMetadataByRuleset
     *            true or false
     */
    public void setOrderMetadataByRuleset(boolean orderMetadataByRuleset) {
        this.orderMetadataByRuleset = orderMetadataByRuleset;
    }

    /**
     * Check if ruleset is active.
     *
     * @return whether ruleset is active or not
     */
    public Boolean isActive() {
        return this.active;
    }

    /**
     * Set if ruleset is active.
     *
     * @param active
     *            whether ruleset is active or not
     */
    public void setActive(boolean active) {
        this.active = active;
    }
}
