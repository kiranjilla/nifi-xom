package org.apache.nifi.client.opcda;

import lombok.Data;
import org.jinterop.dcom.common.JIException;

import org.joda.time.DateTime;

import org.openscada.opc.lib.da.AddFailedException;
import org.openscada.opc.lib.da.Group;
import org.openscada.opc.lib.da.Item;

import java.util.*;
import java.util.logging.Logger;

/**
 * Created by fdigirolomo on 10/18/16.
 */
@Data
public class OPCDAGroupCacheObject {

    private Logger log = Logger.getLogger(getClass().getName());

    private Group group;

    private String groupName;

    private Collection<Item> items;

    private DateTime refreshTimestamp = new DateTime();

    public OPCDAGroupCacheObject(Group group) throws JIException {
        log.info("instantiating state table for group: " + groupName);
        this.groupName = group.getName();
        this.group = group;
    }

    public OPCDAGroupCacheObject(Group group, Collection<Item> items) throws JIException {
        this.groupName = group.getName();
        log.info("instantiating state table for group: " + groupName);
        for (final Item item : items) {
            log.info("[" + groupName + "]: " + item.getId());
        }
        this.group = group;
        this.items = items;
    }

    public Item getItem(Item item) {
        log.info("get item: " + item.getId());
       return group.findItemByClientHandle(item.getClientHandle());
    }

    public Item addItem(final String itemId) {
        log.info("get item: " + itemId);
        Item item;
        try {
            item = group.addItem(itemId);
        } catch (JIException e) {
            e.printStackTrace();
        } catch (AddFailedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean isExpired(Integer refreshInterval) {
        log.info("checking group expiry: " + groupName);
        if (!refreshTimestamp.plus(refreshInterval).isBeforeNow()) {
            log.info("group expired: " + groupName);
            return true;
        }
        log.info("group remain relevant: " + groupName);
        return false;
    }

}
