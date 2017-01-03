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
 * @author <a href="mailto:sbabu@hortonworks.com">Sekhar Babu</a>
 * @author <a href="mailto:fdigirolomo@hortonworks.com">Frank DiGirolomo</a>
 * @author <a href="mailto:kerra@hortonworks.com">Kiran Erra</a>
 *
 */

package org.apache.nifi.processor.opcda;

import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.client.opcda.OPCDAConnection;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.domain.opcda.OPCDATag;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.util.opcda.OPCDAItemStateValueMapper;
import org.jinterop.dcom.common.JIException;
import org.openscada.opc.lib.common.ConnectionInformation;
import org.openscada.opc.lib.common.NotConnectedException;
import org.openscada.opc.lib.da.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

@Tags({"opcda opc state tag query"})
@CapabilityDescription("Polls OPC DA Server and create flow file")
@InputRequirement(InputRequirement.Requirement.INPUT_ALLOWED)
@SupportsBatching
public class AsyncGetOPCDATagState extends AbstractProcessor {

    private volatile OPCDAConnection connection;

    private static String DELIMITER;

    private volatile BlockingQueue<OPCDATag> stateQueue = new LinkedBlockingQueue<>();

    private volatile AccessBase access;

    private volatile Collection<String> tags = new ArrayList<>();

    // PROPERTY DESCRIPTORS
    static final PropertyDescriptor SERVER = new PropertyDescriptor.Builder()
            .name("Host")
            .description("OPC DA Server Host Name or IP Address")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    static final PropertyDescriptor WORKGROUP = new PropertyDescriptor.Builder()
            .name("Workgroup")
            .description("OPC DA Server Workgroup or Domain Name")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    static final PropertyDescriptor USERNAME = new PropertyDescriptor.Builder()
            .name("Username")
            .description("OPC DA User Name")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    static final PropertyDescriptor PASSWORD = new PropertyDescriptor.Builder()
            .name("Password")
            .description("OPC DA Password")
            .required(true).sensitive(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    static final PropertyDescriptor CLASS_ID = new PropertyDescriptor.Builder()
            .name("Class ID")
            .description("OPC DA Class ID")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    static final PropertyDescriptor POLL_INTERVAL = new PropertyDescriptor.Builder()
            .name("Tag State Poll Interval")
            .description("Interval (in milliseconds) to poll tag state")
            .required(false)
            .defaultValue("600000")
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    static final PropertyDescriptor OUTPUT_DELIMIITER = new PropertyDescriptor.Builder()
            .name("Output Delimiter")
            .description("Delimiter for formating output")
            .required(true)
            .defaultValue(",")
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
            .build();

    // RELATIONSHIPS
    static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("The FlowFile with transformed content will be routed to this relationship")
            .build();

    static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("The FlowFile with transformed content has failed to this relationship")
            .build();

    private List<PropertyDescriptor> descriptors;
    private Set<Relationship> relationships;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> _descriptors = new ArrayList<>();
        _descriptors.add(SERVER);
        _descriptors.add(WORKGROUP);
        _descriptors.add(USERNAME);
        _descriptors.add(PASSWORD);
        _descriptors.add(CLASS_ID);
        _descriptors.add(POLL_INTERVAL);
        _descriptors.add(OUTPUT_DELIMIITER);
        // _descriptors.add(ENABLE_GROUP_CACHE);
        // _descriptors.add(CACHE_REFRESH_INTERVAL);
        this.descriptors = Collections.unmodifiableList(_descriptors);

        final Set<Relationship> _relationships = new HashSet<>();
        _relationships.add(REL_SUCCESS);
        _relationships.add(REL_FAILURE);
        // _relationships.add(REL_RETRY);
        this.relationships = Collections.unmodifiableSet(_relationships);
    }

    @Override
    public Set<Relationship> getRelationships() {
        getLogger().debug("relationships: " + Arrays.toString(relationships.toArray()));
        return this.relationships;
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        getLogger().debug("supported property descriptors: " + Arrays.toString(descriptors.toArray()));
        return this.descriptors;
    }

    @Override
    public void onPropertyModified(final PropertyDescriptor descriptor, final String oldValue, final String newValue) {
        // if any property is modified, the results are no longer valid. Destroy all messages in the queue.
        stateQueue.clear();
    }

    @OnScheduled
    public void onScheduled(final ProcessContext processContext, final ProcessSession processSession) throws DuplicateGroupException, NotConnectedException, JIException, UnknownHostException, AddFailedException {
        getLogger().info("esablishing connection from connection information derived from context");
        DELIMITER = processContext.getProperty(OUTPUT_DELIMIITER).getValue();
        connection = getConnection(processContext);
        access = new Async20Access(connection, Integer.parseInt(processContext.getProperty(POLL_INTERVAL).getValue()), false);

        FlowFile flowFile = processSession.get();
        processSession.read(flowFile, (InputStream in) -> {
            if (tags.isEmpty()) tags.addAll(IOUtils.readLines(in, "UTF-8"));
        });
        for (final String tag : tags) {
            getLogger().info("adding tag to access base: " + tag);
            access.addItem(tag, (item, itemState) -> {
                getLogger().info("tag state change: " + item.getId());
                stateQueue.add(new OPCDATag(item, itemState));
            });
        }
        access.bind();
    }

    @OnStopped
    public void onStopped() throws JIException {
        getLogger().info("stopping");
        getLogger().info("releasing connection");
        connection.disconnect();
        getLogger().info("releasing access");
        access.unbind();
        getLogger().info("processor stopped");
    }

    public void onTrigger(final ProcessContext processContext, final ProcessSession processSession) {
        FlowFile flowFile = processSession.create();
        getLogger().info("[" + processContext.getName() + "]: triggered");
        final OPCDATag tag = stateQueue.poll();
        if (tag == null) {
            getLogger().info("null tag: yielding context");
            processContext.yield();
            return;
        }

        final String output = processTag(tag);
        getLogger().debug("flowFile output: " + output);
        flowFile = processSession.write(flowFile, (OutputStream stream) -> {
            try {
                stream.write(output.getBytes("UTF-8"));
                stateQueue.remove(tag);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        processSession.transfer(flowFile, REL_SUCCESS);
        processSession.getProvenanceReporter().receive(flowFile, connection.getConnectionInformation().getHost());
    }

    private String processTag(final OPCDATag tag) {
        final String tagId = tag.getItem().getId();
        getLogger().info("processing tag: " + tagId);
        StringBuilder sb = new StringBuilder();
        try {
            final ItemState itemState = tag.getItem().read(false);
            if (itemState != null) {
                sb.append(tagId)
                        .append(DELIMITER)
                        .append(OPCDAItemStateValueMapper.toJavaType(itemState.getValue()))
                        .append(DELIMITER)
                        .append(itemState.getTimestamp().getTimeInMillis())
                        .append(DELIMITER)
                        .append(itemState.getQuality())
                        .append(DELIMITER)
                        .append(itemState.getErrorCode())
                        .append("\n");
                getLogger().debug("item output [" + tagId + "] " + sb.toString());
            } else {
                throw new Exception(tagId + "item state is null");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    private OPCDAConnection getConnection(final ProcessContext processContext) {
        getLogger().info("aggregating connection information from context");
        ConnectionInformation connectionInformation = new ConnectionInformation();
        connectionInformation.setHost(processContext.getProperty(SERVER).getValue());
        connectionInformation.setDomain(processContext.getProperty(WORKGROUP).getValue());
        connectionInformation.setUser(processContext.getProperty(USERNAME).getValue());
        connectionInformation.setPassword(processContext.getProperty(PASSWORD).getValue());
        connectionInformation.setClsid(processContext.getProperty(CLASS_ID).getValue());
        return new OPCDAConnection(connectionInformation, newSingleThreadScheduledExecutor());

        // TODO : add program name as acceptable attribute value
        //connectionInformation.setProgId(context.getProperty(OPCDA_PROG_ID_NAME).getValue());
    }

}
