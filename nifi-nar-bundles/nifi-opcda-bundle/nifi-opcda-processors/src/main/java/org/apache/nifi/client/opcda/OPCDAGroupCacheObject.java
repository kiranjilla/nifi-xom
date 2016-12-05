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
 *
 * @author <a href="mailto:fdigirolomo@hortonworks.com">Frank DiGirolomo</a>
 */

package org.apache.nifi.client.opcda;

import lombok.Data;
import org.jinterop.dcom.common.JIException;
import org.joda.time.DateTime;
import org.openscada.opc.lib.da.AddFailedException;
import org.openscada.opc.lib.da.Group;
import org.openscada.opc.lib.da.Item;

import java.util.Collection;
import java.util.logging.Logger;

@Data
public class OPCDAGroupCacheObject {

    private Logger log = Logger.getLogger(getClass().getName());

    private volatile Group group;

    private static String groupName;

    private volatile Collection<Item> items;

    private DateTime refreshTimestamp = new DateTime();

    public OPCDAGroupCacheObject(Group group) throws JIException {
        log.info("instantiating cache for group: " + groupName);
        this.groupName = group.getName();
        this.group = group;
    }

    public OPCDAGroupCacheObject(final Group group, final Collection<Item> items) throws JIException {
        this.groupName = group.getName();
        log.info("instantiating cache for group: " + groupName);
        for (final Item item : items) {
            log.info("[" + groupName + "]: " + item.getId());
        }
        this.group = group;
        this.items = items;
    }

    public Item getItem(final Item item) {
        log.info("get item: " + item.getId());
        return group.findItemByClientHandle(item.getClientHandle());
    }

    public Item addItem(final String itemId) {
        log.info("add item: " + itemId);
        Item item;
        try {
            item = group.addItem(itemId);
            return item;
        } catch (JIException e) {
            e.printStackTrace();
        } catch (AddFailedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean isExpired(final Integer refreshInterval) {
        log.info("checking group expiry: " + groupName);
        if (!refreshTimestamp.plus(refreshInterval).isBeforeNow()) {
            log.info("cached group expired: " + groupName);
            return true;
        }
        log.info("cached group remains relevant: " + groupName);
        return false;
    }

}
