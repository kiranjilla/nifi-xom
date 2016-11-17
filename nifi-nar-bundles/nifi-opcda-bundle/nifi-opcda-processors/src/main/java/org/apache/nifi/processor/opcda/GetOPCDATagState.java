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
package org.apache.nifi.processor.opcda;

import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.behavior.*;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.client.opcda.OPCDAConnection;
import org.apache.nifi.client.opcda.OPCDAGroupCacheObject;
import org.apache.nifi.service.opcda.OPCDAGroupCache;
import org.apache.nifi.util.opcda.OPCDAItemStateValueMapper;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.jinterop.dcom.common.JIException;
import org.openscada.opc.lib.common.ConnectionInformation;
import org.openscada.opc.lib.common.NotConnectedException;
import org.openscada.opc.lib.da.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.util.*;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

@Tags({"opcda opc state tag query"})
@CapabilityDescription("Polls OPC DA Server and create flow file")
@InputRequirement(Requirement.INPUT_ALLOWED)
@SeeAlso({})
@ReadsAttributes({@ReadsAttribute(attribute = "My Property", description = "")})
@WritesAttributes({@WritesAttribute(attribute = "", description = "")})
@SupportsBatching
public class GetOPCDATagState extends AbstractProcessor {

    private OPCDAConnection connection;

    private List<PropertyDescriptor> descriptors;

    private Set<Relationship> relationships;

    private boolean caching = false;

    Collection<OPCDAGroupCacheObject> cache = new ArrayList<>();

    private Integer stateTableRefreshInterval;

    private String DELIMITER;

    public static final PropertyDescriptor OPCDA_GROUP_CACHE_SERVICE = new PropertyDescriptor.Builder()
            .name("OPCDA Group Cache Service")
            .description("Specifies the Controller Service to use for accessing OPCDA Group Caching.")
            .required(true)
            .identifiesControllerService(OPCDAGroupCache.class)
            .build();

    public static final PropertyDescriptor OPCDA_SERVER_IP_NAME = new PropertyDescriptor.Builder()
            .name("OPCDA_SERVER_IP_NAME")
            .description("OPC DA Server Host Name or IP Address")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    public static final PropertyDescriptor OPCDA_WORKGROUP_NAME = new PropertyDescriptor.Builder()
            .name("OPCDA_WORKGROUP_NAME")
            .description("OPC DA Server Workgroup or Domain Name")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    public static final PropertyDescriptor OPCDA_USER_NAME = new PropertyDescriptor.Builder()
            .name("OPCDA_USER_NAME")
            .description("OPC DA User Name")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    public static final PropertyDescriptor OPCDA_PASSWORD_TEXT = new PropertyDescriptor.Builder()
            .name("OPCDA_PASSWORD_TEXT")
            .description("OPC DA Password Text")
            .required(true).sensitive(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    public static final PropertyDescriptor OPCDA_CLASS_ID_NAME = new PropertyDescriptor.Builder()
            .name("OPCDA_CLASS_ID_NAME")
            .description("OPC DA Class ID")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor READ_TIMEOUT_MS_ATTRIBUTE = new PropertyDescriptor.Builder()
            .name("READ_TIMEOUT_MS_ATTRIBUTE")
            .description("Read Timeout for Read operation from OPC DA Server")
            .required(false)
            .defaultValue("600000")
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor OUTPUT_DELIMIITER = new PropertyDescriptor.Builder()
            .name("Output Delimiter")
            .description("Delimiter for formating output")
            .required(true)
            .defaultValue(",")
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
            .build();

    public static final PropertyDescriptor ENABLE_GROUP_CACHE = new PropertyDescriptor.Builder()
            .name("Enable Group Caching")
            .description("Enable Group/Item Caching")
            .required(true)
            .defaultValue("false")
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.BOOLEAN_VALIDATOR)
            .build();

    public static final PropertyDescriptor CACHE_REFRESH_INTERVAL = new PropertyDescriptor.Builder()
            .name("Cache Refresh Interval")
            .description("Time in seconds to refresh groups/items in State Table")
            .required(true)
            .defaultValue("3600").expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("The FlowFile with transformed content will be routed to this relationship")
            .build();

    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("The FlowFile with transformed content has failed to this relationship")
            .build();

    public static final Relationship REL_RETRY = new Relationship.Builder()
            .name("retry")
            .description("The FlowFile with transformed content will be retried to this relationship")
            .build();

    @Override
    protected void init(final ProcessorInitializationContext context) {
        getLogger().info("initializing property descriptors");
        final List<PropertyDescriptor> descriptors = new ArrayList<>();
        descriptors.add(OPCDA_SERVER_IP_NAME);
        descriptors.add(OPCDA_WORKGROUP_NAME);
        descriptors.add(OPCDA_USER_NAME);
        descriptors.add(OPCDA_PASSWORD_TEXT);
        descriptors.add(OPCDA_CLASS_ID_NAME);
        descriptors.add(READ_TIMEOUT_MS_ATTRIBUTE);
        descriptors.add(OUTPUT_DELIMIITER);
        descriptors.add(ENABLE_GROUP_CACHE);
        descriptors.add(CACHE_REFRESH_INTERVAL);
        this.descriptors = Collections.unmodifiableList(descriptors);
        getLogger().info(Arrays.toString(descriptors.toArray()));

        getLogger().info("initializing relationships");
        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_SUCCESS);
        relationships.add(REL_FAILURE);
        relationships.add(REL_RETRY);
        this.relationships = Collections.unmodifiableSet(relationships);
        getLogger().info(Arrays.toString(relationships.toArray()));

    }

    @Override
    public Set<Relationship> getRelationships() {
        getLogger().debug("relationships: " + Arrays.toString(relationships.toArray()));
        return relationships;
    }

    @Override
    public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        getLogger().debug("supported property descriptors: " + Arrays.toString(descriptors.toArray()));
        return descriptors;
    }

    @OnScheduled
    public void onScheduled(final ProcessContext processContext) {
        getLogger().info("esablishing connection from connection information derived from context");
        connection = getConnection(processContext);
        getLogger().info("connection state: " + connection.getServerState());
        caching = Boolean.parseBoolean(processContext.getProperty(ENABLE_GROUP_CACHE).getValue());
        stateTableRefreshInterval = Integer.parseInt(processContext.getProperty(CACHE_REFRESH_INTERVAL).getValue());
        DELIMITER = processContext.getProperty(OUTPUT_DELIMIITER).getValue();
    }

    @OnStopped
    public void onStopped() {
        getLogger().info("stopping");
        connection.disconnect();
        getLogger().info("disconnected");
    }

    @Override
    public void onTrigger(final ProcessContext processContext, final ProcessSession processSession) {

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
                if (caching && existingGroup) {
                    StringBuffer output = new StringBuffer();
                    OPCDAGroupCacheObject cache = getCachedGroup(groupName);
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
                        connection.removeGroup(cache.getGroup(), true);
                        getLogger().info("reconstructing group for cache: " + groupName);
                        group = new OPCDAGroupCacheObject(connection.addGroup(groupName)).getGroup();
                        Item item;
                        for (String itemId : itemIds) {
                            getLogger().info("[" + groupName + "] adding tag: " + itemId);
                            item = group.addItem(itemId);
                            output.append(processItem(item));
                            getLogger().info("[" + groupName + "] adding tag to group cache: " + itemId);
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
                    group = connection.addGroup(groupName);
                    Item item;
                    for (String itemId : itemIds) {
                        getLogger().info("[" + groupName + "] adding tag: " + itemId);
                        item = group.addItem(itemId);
                        String _item = processItem(item);
                        output.append(_item);
                        if (caching) {
                            getLogger().info("[" + groupName + "] adding tag to group cache: " + itemId);
                            items.add(item);
                        }
                    }
                    processGroup(flowfile, output.toString(), processSession);
                    if (caching) {
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
            String value = OPCDAItemStateValueMapper.toJavaType(itemState.getValue()).toString();
            getLogger().info("[" + item.getGroup().getName() + "] " + item.getId() + ": " + value);
            sb.append(item.getId())
                    .append(DELIMITER)
                    .append(OPCDAItemStateValueMapper.toJavaType(itemState.getValue()))
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

            // TODO
            // processSession.putAttribute(flowFile, "filename", key);
            // processSession.getProvenanceReporter().receive(flowFile, "OPC");
            processSession.transfer(flowfile, REL_SUCCESS);
        }
    }

    private boolean ifCached(String groupName) {
        for (OPCDAGroupCacheObject c : cache) {
            try {
                if (cache != null && c.getGroup().getName().equals(groupName)) {
                    getLogger().info("cache contains group: " + groupName);
                    return true;
                }
            } catch (JIException e) {
                e.printStackTrace();
            }
        }
        getLogger().info("group not found in cache: " + groupName);
        return false;
    }

    private OPCDAGroupCacheObject getCachedGroup(String groupName) {
        getLogger().info("retrieving state table for group: " + groupName);
        for (OPCDAGroupCacheObject c: cache) {
            try {
                if (c.getGroup().getName().equals(groupName)) {
                    getLogger().info("group resides in cache: " + groupName);
                    return c;
                } else {
                    getLogger().info("group not found in cache: " + groupName);
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
        //connectionInformation.setProgId(context.getProperty(OPCDA_PROG_ID_NAME).getValue());
        return new OPCDAConnection(connectionInformation, newSingleThreadScheduledExecutor());
    }

}
