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

package org.apache.nifi.service.opcda;

import org.apache.nifi.client.opcda.OPCDAGroupCacheObject;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.processor.util.StandardValidators;
import org.jinterop.dcom.common.JIException;
import org.openscada.opc.lib.da.Group;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by fdigirolomo on 10/27/16.
 */
public class OPCDAGroupCache extends AbstractControllerService implements OPCDAGroupCacheService {

    private volatile Collection<Group> cache = new ArrayList<>();

    public static final PropertyDescriptor OPCDA_CACHE_GROUP_NAME = new PropertyDescriptor.Builder()
            .name("OPCDA Cache Group Name")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    @Override
    public Group get(String groupName) throws JIException {
        for (Group group : cache) {
            if (group.getName().equals(groupName)) {
                return group;
            }
        }
        return null;
    }

    @Override
    public void put(Group group) throws JIException {
        OPCDAGroupCacheObject groupCacheObject = new OPCDAGroupCacheObject(group);
        //TODO Add other relevant details
    }

    public void release(Group group) throws JIException {
       cache.remove(group);
       group.remove();
    }

}


