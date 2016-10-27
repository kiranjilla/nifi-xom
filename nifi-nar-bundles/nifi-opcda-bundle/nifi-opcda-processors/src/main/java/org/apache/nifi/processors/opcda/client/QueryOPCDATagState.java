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
package org.apache.nifi.processors.opcda.client;

import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.behavior.*;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.processors.opcda.client.domain.OPCDAGroupCacheObject;
import org.apache.nifi.processors.opcda.client.domain.OPCDAConnection;
import org.apache.nifi.processors.opcda.client.util.OPCDAObjectMapper;
import org.jinterop.dcom.common.JIException;
import org.openscada.opc.lib.common.ConnectionInformation;
import org.openscada.opc.lib.common.NotConnectedException;
import org.openscada.opc.lib.da.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;

@Tags({"opcda opc state tag query"})
@CapabilityDescription("Polls OPC DA Server and create flow file")
@InputRequirement(Requirement.INPUT_ALLOWED)
@SeeAlso({})
@ReadsAttributes({@ReadsAttribute(attribute = "My Property", description = "")})
@WritesAttributes({@WritesAttribute(attribute = "", description = "")})
@SupportsBatching
public class QueryOPCDATagState extends AbstractProcessor {

    private OPCDAConnection server;

    private List<PropertyDescriptor> descriptors;

    private Set<Relationship> relationships;

    boolean enableStateTable = false;

    Collection<OPCDAGroupCacheObject> cache = new ArrayList<>();

    private Integer stateTableRefreshInterval;

    private String DELIMITER;

    public static final PropertyDescriptor OPCDA_SERVER_IP_NAME = new PropertyDescriptor.Builder()
            .name("OPCDA_SERVER_IP_NAME").description("OPC DA Server Host Name or IP Address").required(true)
            .expressionLanguageSupported(false).addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR).build();

    public static final PropertyDescriptor OPCDA_WORKGROUP_NAME = new PropertyDescriptor.Builder()
            .name("OPCDA_WORKGROUP_NAME").description("OPC DA Server Work Group Name").required(true)
            .expressionLanguageSupported(false).addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR).build();

    public static final PropertyDescriptor OPCDA_USER_NAME = new PropertyDescriptor.Builder().name("OPCDA_USER_NAME")
            .description("OPC DA User Name").required(true).expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR).addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    public static final PropertyDescriptor OPCDA_PASSWORD_TEXT = new PropertyDescriptor.Builder()
            .name("OPCDA_PASSWORD_TEXT").description("OPC DA Password Text").required(true).sensitive(true)
            .expressionLanguageSupported(false).addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR).build();

    public static final PropertyDescriptor OPCDA_CLASS_ID_NAME = new PropertyDescriptor.Builder()
            .name("OPCDA_CLASS_ID_NAME").description("OPC DA Class ID Name").required(true)
            .expressionLanguageSupported(false).addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR).build();

    public static final PropertyDescriptor POLL_REPEAT_MS_ATTRIBUTE = new PropertyDescriptor.Builder()
            .name("POLL_REPEAT_MS_ATTRIBUTE").description("No of times to Poll for Read operation from OPC DA Server")
            .required(true).defaultValue("60000").expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

    public static final PropertyDescriptor IS_ASYNC_ATTRIBUTE = new PropertyDescriptor.Builder()
            .name("IS_ASYNC_ATTRIBUTE").description("Is Read operation Async to OPC DA Server").required(true)
            .defaultValue("Y").expressionLanguageSupported(false).addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor READ_TIMEOUT_MS_ATTRIBUTE = new PropertyDescriptor.Builder()
            .name("READ_TIMEOUT_MS_ATTRIBUTE").description("Read Timeout for Read operation from OPC DA Server")
            .required(true).defaultValue("600000").expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

    public static final PropertyDescriptor OUTPUT_DELIMIITER = new PropertyDescriptor.Builder()
            .name("Output Delimiter").description("Delimiter for formating output")
            .required(true).defaultValue(",").expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_BLANK_VALIDATOR).build();

    public static final PropertyDescriptor ENABLE_STATE_TABLE = new PropertyDescriptor.Builder()
            .name("Enable State Table").description("Enable Stateful Group/Item Reconciliation")
            .required(true).defaultValue("false").expressionLanguageSupported(false)
            .addValidator(StandardValidators.BOOLEAN_VALIDATOR).build();

    public static final PropertyDescriptor STATE_TABLE_REFRESH_INTERVAL = new PropertyDescriptor.Builder()
            .name("State Table Refresh Interval").description("Time in seconds to refresh groups/items in State Table")
            .required(true).defaultValue("3600").expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_BLANK_VALIDATOR).build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder().name("success")
            .description("The FlowFile with transformed content will be routed to this relationship").build();

    public static final Relationship REL_FAILURE = new Relationship.Builder().name("failure")
            .description("The FlowFile with transformed content has failed to this relationship").build();

    public static final Relationship REL_RETRY = new Relationship.Builder().name("retry")
            .description("The FlowFile with transformed content will be retried to this relationship").build();

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> descriptors = new ArrayList<>();
        descriptors.add(OPCDA_SERVER_IP_NAME);
        descriptors.add(OPCDA_WORKGROUP_NAME);
        descriptors.add(OPCDA_USER_NAME);
        descriptors.add(OPCDA_PASSWORD_TEXT);
        descriptors.add(OPCDA_CLASS_ID_NAME);
        descriptors.add(READ_TIMEOUT_MS_ATTRIBUTE);
        descriptors.add(POLL_REPEAT_MS_ATTRIBUTE);
        descriptors.add(OUTPUT_DELIMIITER);
        descriptors.add(IS_ASYNC_ATTRIBUTE);
        descriptors.add(ENABLE_STATE_TABLE);
        descriptors.add(STATE_TABLE_REFRESH_INTERVAL);
        this.descriptors = Collections.unmodifiableList(descriptors);

        if (getLogger().isInfoEnabled()) {
            getLogger().info("[ PROPERTY DESCRIPTORS INITIALIZED ]");
            for (PropertyDescriptor i : descriptors) {
                getLogger().info(i.getName());
            }
        }

        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_SUCCESS);
        relationships.add(REL_FAILURE);
        relationships.add(REL_RETRY);
        this.relationships = Collections.unmodifiableSet(relationships);

        if (getLogger().isInfoEnabled()) {
            getLogger().info("[ RELATIONSHIPS INITIALIZED ]");
            for (Relationship i : relationships) {
                getLogger().info(i.getName());
            }
        }
    }

    @Override
    public Set<Relationship> getRelationships() {
        return this.relationships;
    }

    @Override
    public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return descriptors;
    }

    @OnScheduled
    public void onScheduled(final ProcessContext processContext) {
        getLogger().info("esablishing connection from connection information derived from context");
        //server = new Server(ci, Executors.newSingleThreadScheduledExecutor());
        server = getConnection(processContext);
        getLogger().info("server state: " + server.getServerState());

        enableStateTable = Boolean.parseBoolean(processContext.getProperty(ENABLE_STATE_TABLE).getValue());
        stateTableRefreshInterval = Integer.parseInt(processContext.getProperty(STATE_TABLE_REFRESH_INTERVAL).getValue());
        DELIMITER = processContext.getProperty(OUTPUT_DELIMIITER).getValue();
    }

    @OnStopped
    public void onStopped(final ProcessContext processContext) {
        server.disconnect();
        getLogger().info("disconnected");
    }

    @Override
    public void onTrigger(final ProcessContext processContext, final ProcessSession processSession) throws ProcessException {

        String groupName = null;
        Collection<String> itemIds = new ArrayList<String>();
        Collection<Item> items = new ArrayList<Item>();

        getLogger().info("obtaining flowfile from session");
        FlowFile flowfile = processSession.get();
        Group group;

        try {
            if (flowfile != null) {
                getLogger().info("flowfile id: " + flowfile.getId());
                groupName = flowfile.getAttribute("groupName");
                getLogger().info("processing group: " + groupName);
                final boolean existingGroup = ifCached(groupName);
                if (enableStateTable && existingGroup) {
                    StringBuffer output = new StringBuffer();
                    OPCDAGroupCacheObject cache = getCacheForGroup(groupName);
                    if (!cache.isExpired(stateTableRefreshInterval)) {
                        getLogger().info("utilizing cache for group: " + groupName);
                        for (Item i : cache.getItems()) {
                            i = cache.getItem(i);
                            final String item = processItem(i);
                            output.append(item);
                        }
                        processGroup(flowfile, output.toString(), processSession);
                    } else {
                        getLogger().info("removing group from expired cache: " + groupName);
                        server.removeGroup(cache.getGroup(), true);
                        getLogger().info("reconstructing group for cache: " + groupName);
                        group = new OPCDAGroupCacheObject(server.addGroup(groupName)).getGroup();
                        Item item;
                        for (String itemId : itemIds) {
                            getLogger().info("[" + groupName + "] adding item: " + itemId);
                            item = group.addItem(itemId);
                            output.append(processItem(item));
                            getLogger().info("[" + groupName + "] adding item to group cache object: " + itemId);
                        }
                        getLogger().info("adding group to state table: " + groupName);
                        processGroup(flowfile, output.toString(), processSession);
                    }
                } else {
                    StringBuffer output = new StringBuffer();
                    getLogger().info("reading flowfile from session");
                    processSession.read(flowfile, new InputStreamCallback() {
                        public void process(InputStream in) throws IOException {
                            if (itemIds.isEmpty()) {
                                itemIds.addAll(IOUtils.readLines(in));
                            }
                        }
                    });
                    getLogger().info("creating group: " + groupName);
                    group = server.addGroup(groupName);
                    Item item;
                    for (String itemId : itemIds) {
                        getLogger().info("[" + groupName + "] adding item: " + itemId);
                        item = group.addItem(itemId);
                        String _item = processItem(item);
                        output.append(_item);
                        if (enableStateTable) {
                            getLogger().info("[" + groupName + "] adding item to group cache object: " + itemId);
                            items.add(item);
                        }
                    }
                    processGroup(flowfile, output.toString(), processSession);
                    if (enableStateTable) {
                        getLogger().info("adding group to cache: " + groupName);
                        cache.add(new OPCDAGroupCacheObject(group, items));
                    }
                }
            }
        } catch (NotConnectedException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (JIException e) {
            e.printStackTrace();
        } catch (DuplicateGroupException e) {
            e.printStackTrace();
        } catch (AddFailedException e) {
            e.printStackTrace();
        }
    }

    private String processItem(Item item) {
        getLogger().info("processing item: " + item.getId());
        StringBuffer sb = new StringBuffer();
        try {
            getLogger().info("[" + item.getGroup().getName() + "] obtaining item state: " + item.getId());
            ItemState itemState = item.read(false);
            String value = OPCDAObjectMapper.toJavaType(itemState.getValue()).toString();
            getLogger().info("[" + item.getGroup().getName() + "] " + item.getId() + ": " + value);
            sb.append(item.getId())
                    .append(DELIMITER)
                    .append(OPCDAObjectMapper.toJavaType(itemState.getValue()))
                    .append(DELIMITER)
                    .append(itemState.getTimestamp().getTimeInMillis())
                    .append(DELIMITER)
                    .append(itemState.getQuality())
                    .append(DELIMITER)
                    .append(itemState.getErrorCode())
                    .append("\n");
            getLogger().info("item output [" + item.getId() + "] " + sb.toString());
        } catch (JIException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    private void processGroup(FlowFile flowfile, String output, ProcessSession processSession) {
        getLogger().info("processing output: " + output);
        if (output.isEmpty()) {
            getLogger().info("releasing flow file");
            processSession.transfer(flowfile, REL_FAILURE);
        } else {
            getLogger().debug("writing flow file");
            flowfile = processSession.write(flowfile, new OutputStreamCallback() {
                @Override
                public void process(OutputStream stream) throws IOException {
                    try {
                        stream.write(output.getBytes("UTF-8"));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            // ToDo
            // processSession.putAttribute(flowFile, "filename", key);
            // processSession.getProvenanceReporter().receive(flowFile, "OPC");
            processSession.transfer(flowfile, REL_SUCCESS);
        }
    }

    private boolean ifCached(String groupName) {
        for (OPCDAGroupCacheObject c : cache) {
            try {
                if (c.getGroup().getName().equals(groupName)) {
                    getLogger().info("state table contains group: " + groupName);
                    return true;
                }
            } catch (JIException e) {
                e.printStackTrace();
            }
        }
        getLogger().info("group not found in state table: " + groupName);
        return false;
    }

    private OPCDAGroupCacheObject getCacheForGroup(String groupName) {
        getLogger().info("retrieving state table for group: " + groupName);
        for (OPCDAGroupCacheObject c: cache) {
            try {
                if (c.getGroup().getName().equals(groupName)) {
                    getLogger().info("state table exists for group: " + groupName);
                    return c;
                } else {
                    getLogger().info("state table for group not found: " + groupName);
                }
            } catch (JIException e) {
                e.printStackTrace();
            }
        }
        return null;
    }


    private OPCDAConnection getConnection(final ProcessContext processContext) {
        getLogger().info("aggregating connection information from context");
        ConnectionInformation connectionInformation = new ConnectionInformation();
        connectionInformation.setHost(processContext.getProperty(OPCDA_SERVER_IP_NAME).getValue());
        connectionInformation.setDomain(processContext.getProperty(OPCDA_WORKGROUP_NAME).getValue());
        connectionInformation.setUser(processContext.getProperty(OPCDA_USER_NAME).getValue());
        connectionInformation.setPassword(processContext.getProperty(OPCDA_PASSWORD_TEXT).getValue());
        connectionInformation.setClsid(processContext.getProperty(OPCDA_CLASS_ID_NAME).getValue());
        return new OPCDAConnection(connectionInformation, Executors.newSingleThreadScheduledExecutor());
    }

}
