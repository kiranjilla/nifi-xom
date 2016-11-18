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
package org.apache.nifi.service.opcda;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.jinterop.dcom.common.JIException;
import org.openscada.opc.lib.da.Group;

import java.io.IOException;

/**
 * Created by fdigirolomo on 11/1/16.
 */
@Tags({"opcda", "cache" })
@CapabilityDescription("Provides caching for OPCDA groups.")
public interface OPCDAGroupCacheService {

    /**
     * Gets the group from the cache with the matching group name
     *
     * @param groupName the name of the group for the collection of tags and the connection
     * @throws JIException thrown when there are communication errors with the OPC server
     */
    public Group get(final String groupName) throws JIException;

    /**
     * Puts the given group in to the group cache
     *
     * @param group the group for the collection of tags and the connection
     * @throws JIException thrown when there are communication errors with the OPC server
     */
    public void put(final Group group) throws JIException;

    /**
     * Releases the given group object from the group cache, expireation
     *
     * @param group the group for the collection of tags and the connection
     * @throws JIException thrown when there are communication errors with the OPC server
     */
    public void release (final Group group) throws JIException;
}
