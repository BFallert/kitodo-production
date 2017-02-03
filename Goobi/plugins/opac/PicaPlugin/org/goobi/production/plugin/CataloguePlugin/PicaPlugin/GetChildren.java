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
package org.goobi.production.plugin.CataloguePlugin.PicaPlugin;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The class provides the ability to iterate over the children of a DOM element.
 *
 * @author Matthias Ronge
 */
class GetChildren implements Iterable<Node>, Iterator<Node> {
    /**
     * The next child to offer
     */
    private Node next;

    /**
     * Creates a class to iterate over the children of a DOM element.
     *
     * @param parent
     *            parent over whose children shall be iterated
     */
    GetChildren(Element parent) {
        next = parent.getFirstChild();
    }

    /**
     * Returns whether the iterator can return another element.
     *
     * @see java.util.Iterator#hasNext()
     */
    @Override
    public boolean hasNext() {
        return next != null;
    }

    /**
     * Returns an iterator instance, which is this implementation.
     */
    @Override
    public Iterator<Node> iterator() {
        return this;
    }

    /**
     * Returns the next element while iterating.
     */
    @Override
    public Node next() {
        if (next == null) { throw new NoSuchElementException(); }
        Node current = next;
        next = next.getNextSibling();
        return current;
    }

    /**
     * This iterator does not support removal.
     */
    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}