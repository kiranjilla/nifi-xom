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

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.jinterop.dcom.common.JIException;
import org.openscada.opc.lib.da.Group;

/**
 * Created by fdigirolomo on 11/1/16.
 */
@Tags({"opcda", "cache" })
@CapabilityDescription("Provides caching for OPCDA groups.")
public interface OPCDAGroupCacheService {

    public Group get(final String groupName) throws JIException;

    public void put(final Group group) throws JIException;

    public void release (final Group group) throws JIException;
}
