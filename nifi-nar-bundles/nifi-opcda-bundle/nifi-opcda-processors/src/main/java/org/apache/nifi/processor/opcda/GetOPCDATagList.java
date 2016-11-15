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

import org.apache.nifi.annotation.behavior.*;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.client.opcda.OPCDAConnection;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.jinterop.dcom.common.JIException;
import org.openscada.opc.lib.common.ConnectionInformation;
import org.openscada.opc.lib.da.AddFailedException;
import org.openscada.opc.lib.da.browser.Branch;
import org.openscada.opc.lib.da.browser.Leaf;
import org.openscada.opc.lib.da.browser.TreeBrowser;

import java.io.IOException;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

@Tags({"opcda client tags"})
@CapabilityDescription("Polls OPC DA Server and create tag list file")
@InputRequirement(Requirement.INPUT_FORBIDDEN)
public class GetOPCDATagList extends AbstractProcessor {

    private Logger log = Logger.getLogger(this.getClass().getName());

    private OPCDAConnection connection;

    private List<PropertyDescriptor> descriptors;

    private Set<Relationship> relationships;

    private String filter;

    // PROPERTIES
    public static final PropertyDescriptor OPCDA_SERVER_IP_NAME = new PropertyDescriptor
            .Builder().name("OPCDA_SERVER_IP_NAME")
            .description("OPC DA Server Host Name or IP Address")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    public static final PropertyDescriptor OPCDA_WORKGROUP_NAME = new PropertyDescriptor
            .Builder().name("OPCDA_WORKGROUP_NAME")
            .description("OPC DA Server Domain or Workgroup Name")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    public static final PropertyDescriptor OPCDA_USER_NAME = new PropertyDescriptor
            .Builder().name("OPCDA_USER_NAME")
            .description("OPC DA User Name")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    public static final PropertyDescriptor OPCDA_PASSWORD_TEXT = new PropertyDescriptor
            .Builder().name("OPCDA_PASSWORD_TEXT")
            .description("OPC DA Password")
            .required(true)
            .sensitive(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    public static final PropertyDescriptor OPCDA_CLASS_ID_NAME = new PropertyDescriptor
            .Builder().name("OPCDA_CLASS_ID_NAME")
            .description("OPC DA Class or Application ID")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    public static final PropertyDescriptor TAG_FILTER = new PropertyDescriptor
            .Builder().name("Tag Filter")
            .description("OPT Tag Filter to limit or constrain tags to a particular group")
            .required(true)
            .defaultValue("Tag10")
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.REGULAR_EXPRESSION_VALIDATOR)
            .build();


    public static final PropertyDescriptor READ_TIMEOUT_MS_ATTRIBUTE = new PropertyDescriptor
            .Builder().name("READ_TIMEOUT_MS_ATTRIBUTE")
            .description("Read Timeout for Read operation from OPC DA Server")
            .required(true)
            .defaultValue("10000")
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    // RELATIONSHIPS
    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("The FlowFile with transformed content will be routed to this relationship")
            .build();

    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("The FlowFile with transformed content has failed to this relationship")
            .build();


    @Override
    protected void init(final ProcessorInitializationContext context) {
        getLogger().info("initializing property descriptors");
        final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
        descriptors.add(OPCDA_SERVER_IP_NAME);
        descriptors.add(OPCDA_WORKGROUP_NAME);
        descriptors.add(OPCDA_USER_NAME);
        descriptors.add(OPCDA_PASSWORD_TEXT);
        descriptors.add(OPCDA_CLASS_ID_NAME);
        descriptors.add(TAG_FILTER);
        descriptors.add(READ_TIMEOUT_MS_ATTRIBUTE);
        descriptors.add(TAG_FILTER);
        this.descriptors = Collections.unmodifiableList(descriptors);
        getLogger().info(Arrays.toString(descriptors.toArray()));

        getLogger().info("initializing relationships");
        final Set<Relationship> relationships = new HashSet<Relationship>();
        relationships.add(REL_SUCCESS);
        relationships.add(REL_FAILURE);
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
        getLogger().debug("property descriptors: " + Arrays.toString(descriptors.toArray()));
        return descriptors;
    }

    @OnScheduled
    public void onScheduled(final ProcessContext context) {
        getLogger().info("esablishing connection from connection information derived from context");
        connection = getConnection(context);
        filter = context.getProperty(TAG_FILTER).getValue();
    }

    @OnStopped
    public void onStopped(final ProcessContext context) {
        getLogger().info("disconnecting");
        connection.disconnect();
    }

    @Override
    public void onTrigger(ProcessContext processContext, ProcessSession processSession) throws ProcessException {
        processTags(getTags(), processSession, processContext);
    }

    private void processTags(List<String> itemIds, ProcessSession processSession, ProcessContext processContext) {
        FlowFile flowfile = processSession.create();
        flowfile = processSession.write(flowfile, new OutputStreamCallback() {
            @Override
            public void process(final OutputStream outStream) throws IOException {
                try {
                    StringBuffer output = new StringBuffer();
                    for (String item : itemIds) {
                        output.append(item.toString() + "\n");
                    }
                    outStream.write(output.toString().getBytes("UTF-8"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        String fileName = "file-" + processContext.getProperty(OPCDA_SERVER_IP_NAME).getValue();
        if (fileName != null) {
            flowfile = processSession.putAttribute(flowfile, "filename", fileName);
        }
        processSession.getProvenanceReporter().receive(flowfile, fileName);
        processSession.transfer(flowfile, REL_SUCCESS);
    }

    public List<String> getTags() {
        log.info("getting tags matching filter: " + filter);
        List<String> itemIds = new ArrayList<String>();
        try {
            // connection.getController().connect();
            Branch branch = connection.getTreeBrowser().browse();
            ArrayList<String> matches = new ArrayList<String>();
            for (Branch b : branch.getBranches()) {
                if (b.getName().matches(filter)) {
                    log.info("matching tag for branch: " + b.getName());
                    matches.add(String.format("%n[B] %s", b.getName()));
                }
            }
            for (Leaf l : branch.getLeaves()) {
                if (l.getName().matches(filter)) {
                    log.info("matching tag for leaf: " + l.getName());
                    matches.add(String.format("%n[T] %s", l.getName()));
                }
            }
            populateItemsMapRecursive(branch, itemIds);
        } catch (JIException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        return itemIds;

    }

    public void populateItemsMapRecursive(Branch parent, List<String> itemIds) {
        for (Leaf l : parent.getLeaves()) {
            try {
                registerLeaf(l, itemIds);
                for (Branch child : parent.getBranches()) {
                    populateItemsMapRecursive(child, itemIds);
                }
            } catch (JIException e) {
                e.printStackTrace();
            } catch (AddFailedException e) {
                e.printStackTrace();
            }
        }
    }

    private void registerLeaf(Leaf l, List<String> itemIds) throws JIException, AddFailedException {
        String itemId = l.getItemId();
        itemIds.add(itemId);
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
