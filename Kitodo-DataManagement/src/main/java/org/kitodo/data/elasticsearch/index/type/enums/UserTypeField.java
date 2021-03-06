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

package org.kitodo.data.elasticsearch.index.type.enums;

public enum UserTypeField {

    ID("id"),
    NAME("name"),
    SURNAME("surname"),
    LOGIN("login"),
    LDAP_LOGIN("ldapLogin"),
    ACTIVE("active"),
    LOCATION("location"),
    METADATA_LANGUAGE("metadataLanguage"),
    USER_GROUPS("userGroups"),
    FILTERS("filters"),
    PROJECTS("projects"),
    PROCESSING_TASKS("processingTasks"),
    TASKS("tasks");

    private String name;

    UserTypeField(String name) {
        this.name = name;
    }

    /**
     * Get name.
     *
     * @return value of name
     */
    public String getName() {
        return name;
    }
}
