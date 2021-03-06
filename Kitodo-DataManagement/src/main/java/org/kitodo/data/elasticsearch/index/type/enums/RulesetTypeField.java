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

public enum RulesetTypeField {

    ID("id"),
    TITLE("title"),
    FILE("file"),
    ORDER_METADATA_BY_RULESET("orderMetadataByRuleset"),
    FILE_CONTENT("fileContent"),
    ACTIVE("active"),
    CLIENT_ID("client.id"),
    CLIENT_NAME("client.name");

    private String name;

    RulesetTypeField(String name) {
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
