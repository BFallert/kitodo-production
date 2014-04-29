/**
 * This file is part of the Goobi Application - a Workflow tool for the support
 * of mass digitization.
 * 
 * (c) 2014 Goobi. Digialisieren im Verein e.V. &lt;contact@goobi.org&gt;
 * 
 * Visit the websites for more information.
 *     		- http://www.goobi.org/en/
 *     		- https://github.com/goobi
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.goobi.production.plugin.CataloguePlugin.PicaPlugin;

import java.util.ArrayList;

/**
 * die OpacBeautifier dienen zur Manipulation des Ergebnisses, was als Treffer
 * einer Opacabfrage zurückgegeben wird. Dabei soll die Eigenschaft eines Wertes
 * gesetzt werden, wenn bestimmte Werte in dem opac-Ergebnis auftreten.
 */
class ConfigOpacCatalogueBeautifier {
	private final ConfigOpacCatalogueBeautifierElement tagElementToChange;
	private final ArrayList<ConfigOpacCatalogueBeautifierElement> tagElementsToProof;

	ConfigOpacCatalogueBeautifier(ConfigOpacCatalogueBeautifierElement inChangeElement,
			ArrayList<ConfigOpacCatalogueBeautifierElement> inProofElements) {
		this.tagElementToChange = inChangeElement;
		this.tagElementsToProof = inProofElements;
	}

	ConfigOpacCatalogueBeautifierElement getTagElementToChange() {
		return this.tagElementToChange;
	}

	ArrayList<ConfigOpacCatalogueBeautifierElement> getTagElementsToProof() {
		return this.tagElementsToProof;
	}
}
