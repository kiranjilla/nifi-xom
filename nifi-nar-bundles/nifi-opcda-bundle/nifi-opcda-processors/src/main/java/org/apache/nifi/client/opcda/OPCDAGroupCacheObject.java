/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
        for (final Item i : items) {
            log.info("[" + groupName + "]: " + i.getId());
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
        Item i;
        try {
            i = group.addItem(itemId);
        } catch (JIException e) {
            e.printStackTrace();
        } catch (AddFailedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean isExpired(Integer refreshInterval) {
        log.info("checking group expiry: " + groupName);
        boolean expired = false;
        if (!refreshTimestamp.plus(refreshInterval).isBeforeNow()) {
            log.info("group exprired: " + groupName);
            expired = true;
        }
        log.info("group remain relevant: " + groupName);
        return expired;
    }

}
