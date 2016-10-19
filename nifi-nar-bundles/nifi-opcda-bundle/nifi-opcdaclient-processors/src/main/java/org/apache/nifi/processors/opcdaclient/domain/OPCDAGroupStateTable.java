package org.apache.nifi.processors.opcdaclient.domain;

import org.jinterop.dcom.common.JIException;

import org.joda.time.DateTime;

import org.openscada.opc.lib.da.AddFailedException;
import org.openscada.opc.lib.da.Group;
import org.openscada.opc.lib.da.Item;
import org.openscada.opc.lib.da.Server;

import java.util.*;
import java.util.logging.Logger;

/**
 * Created by fdigirolomo on 10/18/16.
 */
public class OPCDAGroupStateTable {

    private Logger log = Logger.getLogger(this.getClass().getName());

    private Group group;

    private Collection<Item> items;

    private DateTime refreshTimestamp = new DateTime();

    public OPCDAGroupStateTable(final Group group, final Collection<Item> items) throws JIException {
        log.info("instantiating state table for group: " + group.getName());
        this.group = group;
        this.items = items;
    }

    public Group getGroup() {
        return this.group;
    }

    public Collection<Item> getItems() {
        return items;
    }

    public void addItem(final Item item) {
        items.add(item);
    }

    public Item getItem(final Item item) {
       return group.findItemByClientHandle(item.getClientHandle());
    }

    public boolean isExpired(Integer refreshInterval) {
        boolean expired = false;
        if (!refreshTimestamp.plus(refreshInterval).isBeforeNow()) {
            expired = true;
        }
        return expired;
    }

}
