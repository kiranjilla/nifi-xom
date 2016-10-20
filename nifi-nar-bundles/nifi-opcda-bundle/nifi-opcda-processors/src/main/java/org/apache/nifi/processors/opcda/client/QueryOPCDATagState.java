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
import org.apache.nifi.processors.opcda.client.util.JIVariantMarshaller;
import org.apache.nifi.processors.opcda.client.domain.OPCDAGroupStateTable;
import org.jinterop.dcom.common.JIException;
import org.joda.time.DateTime;
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

@Tags({"opc da tag state query"})
@CapabilityDescription("Polls OPC DA Server and create flow file")
@InputRequirement(Requirement.INPUT_ALLOWED)
@SeeAlso({})
@ReadsAttributes({@ReadsAttribute(attribute = "My Property", description = "")})
@WritesAttributes({@WritesAttribute(attribute = "", description = "")})
@SupportsBatching
public class QueryOPCDATagState extends AbstractProcessor {

    private List<PropertyDescriptor> descriptors;

    private Set<Relationship> relationships;

    private Server server;

    private AutoReconnectController controller;

    boolean enableStateTable = false;

    Collection<OPCDAGroupStateTable> stateTables = new ArrayList<OPCDAGroupStateTable>();

    private Integer stateTableRefreshInterval;

    private String DELIMITER = ",";

    TimeZone timeZone = TimeZone.getTimeZone("UTC");
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EE MMM dd HH:mm:ss zzz yyyy", Locale.US);


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
        final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
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

        final Set<Relationship> relationships = new HashSet<Relationship>();
        relationships.add(REL_SUCCESS);
        relationships.add(REL_FAILURE);
        relationships.add(REL_RETRY);
        this.relationships = Collections.unmodifiableSet(relationships);

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
    public void onScheduled(final ProcessContext context) {
        try {
            final String opcServerURI = context.getProperty(OPCDA_SERVER_IP_NAME).getValue();
            this.getLogger().info("*****OnScheduled creating Client and connecting it " + opcServerURI);

            // create connection information
            final ConnectionInformation ci = new ConnectionInformation();
            ci.setHost(context.getProperty(OPCDA_SERVER_IP_NAME).getValue());
            ci.setDomain(context.getProperty(OPCDA_WORKGROUP_NAME).getValue());
            ci.setUser(context.getProperty(OPCDA_USER_NAME).getValue());
            ci.setPassword(context.getProperty(OPCDA_PASSWORD_TEXT).getValue());
            ci.setClsid(context.getProperty(OPCDA_CLASS_ID_NAME).getValue());

            server = new Server(ci, Executors.newScheduledThreadPool(1000));
            server.connect();

            controller = new AutoReconnectController(server);
            controller.connect();

            enableStateTable = Boolean.parseBoolean(context.getProperty(ENABLE_STATE_TABLE).getValue());
            stateTableRefreshInterval = Integer.parseInt(context.getProperty(STATE_TABLE_REFRESH_INTERVAL).getValue());
            DELIMITER = context.getProperty(OUTPUT_DELIMIITER).getValue();

        } catch (Exception e) {
            this.getLogger().error("*****OnScheduled creating Client error {} [{}]",
                    new Object[]{e.getMessage(), e.getStackTrace()});
            context.yield();
        }

    }

    @OnStopped
    public void onStopped(final ProcessContext context) {
        try {
            this.getLogger().info("*****OnStopped disconnecting client ");
            controller.disconnect();
            server.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {

        String groupName = null;
        Collection<String> itemIds = new ArrayList<String>();
        Collection<Item> items = new ArrayList<Item>();

        FlowFile flowfile = session.get();
        Group group = null;
        try {
            if (flowfile != null) {
                groupName = flowfile.getAttribute("groupName");
                StringBuffer output = new StringBuffer();

                if (enableStateTable && ifStateTablesContainGroup(groupName)) {
                    OPCDAGroupStateTable stateTable = getStateTableForGroup(groupName);
                    if (!stateTable.isExpired(stateTableRefreshInterval)) {
                        this.getLogger().info("utilizing state table for group: " + groupName);
                        for (Item i : stateTable.getItems()) {
                            i = stateTable.getItem(i);
                            output = processItem(output, i);
                        }
                        processGroup(flowfile, output.toString(), session);
                    } else {
                        getLogger().info("removing expired group: " + groupName);
                        server.removeGroup(stateTable.getGroup(), true);
                        try {
                            getLogger().info("recreating group: " + groupName);
                            group = server.addGroup(groupName);
                            Item item = null;
                            for (String itemId : itemIds) {
                                getLogger().info("[" + groupName + "] adding item: " + itemId);
                                item = group.addItem(itemId);
                                output = processItem(output, item);
                                if (enableStateTable) {
                                    this.getLogger().info("[" + groupName + "] adding item to state table: " + itemId);
                                    items.add(item);
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
                        if (enableStateTable) {
                            this.getLogger().info("adding group to state table: " + groupName);
                            stateTables.add(new OPCDAGroupStateTable(group, items));
                        }
                        processGroup(flowfile, output.toString(), session);

                    }
                } else {
                    session.read(flowfile, new InputStreamCallback() {
                        public void process(InputStream in) throws IOException {
                            try {
                                if (itemIds.isEmpty()) {
                                    itemIds.addAll(IOUtils.readLines(in));
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });

                    try {
                        getLogger().info("creating group: " + groupName);
                        group = server.addGroup(groupName);
                        Item item = null;
                        for (String itemId : itemIds) {
                            getLogger().info("[" + groupName + "] adding item: " + itemId);
                            item = group.addItem(itemId);
                            output = processItem(output, item);
                            if (enableStateTable) {
                                this.getLogger().info("[" + groupName + "] adding item to state table: " + itemId);
                                items.add(item);
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
                    if (enableStateTable) {
                        this.getLogger().info("adding group to state table: " + groupName);
                        stateTables.add(new OPCDAGroupStateTable(group, items));
                    }
                    processGroup(flowfile, output.toString(), session);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (flowfile != null) {
                session.transfer(flowfile, REL_FAILURE);
            }
        }
    }

    private StringBuffer processItem(StringBuffer output, Item item) {
        TimeZone timeZone = TimeZone.getTimeZone("UTC");
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EE MMM dd HH:mm:ss zzz yyyy", Locale.US);
        simpleDateFormat.setTimeZone(timeZone);
        try {
            this.getLogger().info("[" + item.getGroup().getName() + "] obtaining item state: " + item.getId());
            final ItemState itemState = item.read(false);
            final String value = JIVariantMarshaller.toJavaType(itemState.getValue()).toString();
            this.getLogger().info("[" + item.getGroup().getName() + "] " + item.getId() + ": " + value);
            output.append(item.getId())
                    .append(DELIMITER)
                    .append(JIVariantMarshaller.toJavaType(itemState.getValue()))
                    .append(DELIMITER)
                    .append(simpleDateFormat.format(itemState.getTimestamp()))
                    .append(DELIMITER)
                    .append(itemState.getQuality())
                    .append(DELIMITER)
                    .append(itemState.getErrorCode())
                    .append("\n");
        } catch (JIException e) {
            e.printStackTrace();
        }
        return output;
    }

    private void processGroup(FlowFile flowfile, String output, ProcessSession session) {

        if (output.isEmpty()) {
            this.getLogger().info("releasing flow file");
            session.transfer(flowfile, REL_FAILURE);
        } else {
            getLogger().debug("writing flow file");
            flowfile = session.write(flowfile, new OutputStreamCallback() {
                @Override
                public void process(final OutputStream stream) throws IOException {
                    try {
                        stream.write(output.getBytes("UTF-8"));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            // ToDo
            // session.putAttribute(flowFile, "filename", key);
            // session.getProvenanceReporter().receive(flowFile, "OPC");
            session.transfer(flowfile, REL_SUCCESS);
        }
    }

    private boolean ifStateTablesContainGroup(String groupName) {
        for (OPCDAGroupStateTable st : stateTables) {
            try {
                if (st.getGroup().getName().equals(groupName)) {
                    return true;
                }
            } catch (JIException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private OPCDAGroupStateTable getStateTableForGroup(String groupName) {
        for (OPCDAGroupStateTable st : stateTables) {
            try {
                if (st.getGroup().getName().equals(groupName)) {
                    return st;
                }
            } catch (JIException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

}
