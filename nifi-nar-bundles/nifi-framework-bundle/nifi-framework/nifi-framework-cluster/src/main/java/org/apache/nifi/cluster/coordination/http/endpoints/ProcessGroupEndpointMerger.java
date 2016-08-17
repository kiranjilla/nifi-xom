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

package org.apache.nifi.cluster.coordination.http.endpoints;

import org.apache.nifi.cluster.coordination.http.EndpointResponseMerger;
import org.apache.nifi.cluster.manager.NodeResponse;
import org.apache.nifi.cluster.manager.ProcessGroupEntityMerger;
import org.apache.nifi.cluster.protocol.NodeIdentifier;
import org.apache.nifi.web.api.entity.ProcessGroupEntity;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class ProcessGroupEndpointMerger extends AbstractSingleEntityEndpoint<ProcessGroupEntity> implements EndpointResponseMerger {
    public static final Pattern PROCESS_GROUP_URI_PATTERN = Pattern.compile("/nifi-api/process-groups/(?:(?:root)|(?:[a-f0-9\\-]{36}))");
    public static final Pattern CONTROLLER_ARCHIVE_URI_PATTERN = Pattern.compile("/nifi-api/controller/archive");
    private ProcessGroupEntityMerger processGroupEntityMerger = new ProcessGroupEntityMerger();

    @Override
    public boolean canHandle(final URI uri, final String method) {
        if ("GET".equalsIgnoreCase(method) && (PROCESS_GROUP_URI_PATTERN.matcher(uri.getPath()).matches() || CONTROLLER_ARCHIVE_URI_PATTERN.matcher(uri.getPath()).matches())) {
            return true;
        } else if ("PUT".equalsIgnoreCase(method) && PROCESS_GROUP_URI_PATTERN.matcher(uri.getPath()).matches()) {
            return true;
        }
        return false;
    }

    @Override
    protected Class<ProcessGroupEntity> getEntityClass() {
        return ProcessGroupEntity.class;
    }

    @Override
    protected void mergeResponses(ProcessGroupEntity clientEntity, Map<NodeIdentifier, ProcessGroupEntity> entityMap, Set<NodeResponse> successfulResponses, Set<NodeResponse> problematicResponses) {
        processGroupEntityMerger.merge(clientEntity, entityMap);
    }
}
