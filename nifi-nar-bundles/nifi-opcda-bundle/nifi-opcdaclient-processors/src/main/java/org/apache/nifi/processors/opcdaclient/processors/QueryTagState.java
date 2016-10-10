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
package org.apache.nifi.processors.opcdaclient.processors;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.rmi.activation.UnknownGroupException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.processors.opcdaclient.util.OPCInitialTagConfig;
import org.jinterop.dcom.common.JIException;
import org.openscada.opc.lib.common.ConnectionInformation;
import org.openscada.opc.lib.common.NotConnectedException;
import org.openscada.opc.lib.da.AutoReconnectController;
import org.openscada.opc.lib.da.Group;
import org.openscada.opc.lib.da.Item;
import org.openscada.opc.lib.da.ItemState;
import org.openscada.opc.lib.da.Server;

@Tags({"opc da tag state query"})
@CapabilityDescription("Polls OPC DA Server and create flow file")
@InputRequirement(Requirement.INPUT_ALLOWED)
@SeeAlso({})
@ReadsAttributes({@ReadsAttribute(attribute="My Property", description="")})
@WritesAttributes({@WritesAttribute(attribute="", description="")})
@SupportsBatching
public class QueryTagState extends AbstractProcessor {
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
            .description("OPC DA Server Work Group Name")
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
            .description("OPC DA Password Text")
            .required(true)
            .sensitive(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();
    
    public static final PropertyDescriptor OPCDA_CLASS_ID_NAME = new PropertyDescriptor
            .Builder().name("OPCDA_CLASS_ID_NAME")
            .description("OPC DA Class ID Name")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();
    
    public static final PropertyDescriptor POLL_REPEAT_MS_ATTRIBUTE = new PropertyDescriptor
    .Builder().name("POLL_REPEAT_MS_ATTRIBUTE")
    .description("No of times to Poll for Read operation from OPC DA Server")
    .required(true)
    .defaultValue("60000")
    .expressionLanguageSupported(false)
    .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
    .build();

    public static final PropertyDescriptor IS_ASYNC_ATTRIBUTE = new PropertyDescriptor
    .Builder().name("IS_ASYNC_ATTRIBUTE")
    .description("Is Read operation Async to OPC DA Server")
    .required(true)
    .defaultValue("Y")
    .expressionLanguageSupported(false)
    .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
    .build();
    
    public static final PropertyDescriptor READ_TIMEOUT_MS_ATTRIBUTE = new PropertyDescriptor
            .Builder().name("READ_TIMEOUT_MS_ATTRIBUTE")
            .description("Read Timeout for Read operation from OPC DA Server")
            .required(true)
            .defaultValue("600000")
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
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
    
    private  List<PropertyDescriptor> descriptors;

    private  Set<Relationship> relationships;
    
    private  Server server;
    	        
    private  AutoReconnectController controller;	
    
    private int clientId;

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
        descriptors.add(IS_ASYNC_ATTRIBUTE);
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
	        this.getLogger().info("*****OnScheduled creating Client and connecting it "+opcServerURI);

            // create connection information
            final ConnectionInformation ci = new ConnectionInformation();
            ci.setHost(context.getProperty(OPCDA_SERVER_IP_NAME).getValue());
            ci.setDomain(context.getProperty(OPCDA_WORKGROUP_NAME).getValue());
            ci.setUser(context.getProperty(OPCDA_USER_NAME).getValue());
            ci.setPassword(context.getProperty(OPCDA_PASSWORD_TEXT).getValue());
            //ci.setProgId(context.getProperty(OPCDA_PROG_ID_NAME).getValue());
            ci.setClsid(context.getProperty(OPCDA_CLASS_ID_NAME).getValue());
            // if ProgId is not working, try it using the Clsid instead
            //ci.setClsid("B3AF0BF6-4C0C-4804-A1222-6F3B160F4397");
    		server = new Server(ci, Executors.newScheduledThreadPool(1000));
    		controller = new AutoReconnectController(server);

    		// connect to server
			server.connect();
			controller.connect();
				  	      					    
		} catch (Exception e) {
	        this.getLogger().error("*****OnScheduled creating Client error {} [{}]",new Object[]{e.getMessage(),e.getStackTrace()});
			context.yield();
		}

    }
   

    @OnStopped
    public void onStopped(final ProcessContext context) {
    	try {
    		this.getLogger().info("*****OnStopped disconnecting client ");
    		//opcTags.clear();
    		//OPCInitialTagConfig.getInstance().unregisterGroup(server,groupName,this.getLogger());
	        controller.disconnect();
	        server.disconnect();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    }    
    
	@Override
	public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {

		FlowFile flowfile = session.get();
		try {

			if (flowfile != null) {
				Map<String, Item> opcTags = new HashMap<String, Item>();
				List<String> itemIds = new ArrayList<String>();
				String groupName = flowfile.getAttribute("groupName");
				try {
					Group opcGroup = server.findGroup(groupName);
					
					if (opcGroup.isActive()){
						this.getLogger().info("existing Group");						
					}
					Item item1 = opcGroup.findItemByClientHandle(clientId);
					ItemState is = item1.read(false);
				} catch (org.openscada.opc.lib.da.UnknownGroupException e) {
				session.read(flowfile, new InputStreamCallback() {
						public void process(InputStream in) throws IOException {
							try {
								if (itemIds.isEmpty()) {
									itemIds.addAll(IOUtils.readLines(in));
								}
							} catch (Exception ex) {
								ex.printStackTrace();
								throw ex;
							}
						}
					});

					this.getLogger().info("*****Creating a new group with itemIds-{}",
							new Object[] { flowfile.getAttribute("groupName"), itemIds.size() });
					opcTags = OPCInitialTagConfig.getInstance().fetchSpecificTagsMap(server,
							groupName, opcTags, itemIds, this.getLogger());
					clientId = opcTags.get(itemIds.get(0)).getClientHandle();
				
				}
				String output = OPCInitialTagConfig.getInstance().fetchTagState(opcTags, this.getLogger());

				this.getLogger().info("***** for group {} Output-\n{}",
						new Object[] { groupName, output });
				if (output.isEmpty()) {
					session.transfer(flowfile, REL_FAILURE);
				} else {
					session.remove(flowfile);
					// session.transfer(flowfile,REL_SINK);
					createFlowFile(groupName, output, session, context);
				}

			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			this.getLogger().error("*****Ontrigger error {} [{}]", new Object[] { e.getMessage(), e.getStackTrace() });
			if (flowfile != null){
				//session.remove(flowfile);
				session.transfer(flowfile,REL_FAILURE);
			}
			context.yield();
		}

	}
    
	private void createFlowFile(String key, Object value,ProcessSession session,ProcessContext context) throws JIException {
		this.getLogger().debug("---->Response from Server - node/group info [{}] received {}", new Object[]{key,value});
		FlowFile flowFile = session.create(); 

		flowFile = session.write(flowFile, new OutputStreamCallback() {
		    @Override
		    public void process(final OutputStream outStream) throws IOException {
		        try {
					outStream.write(value.toString().getBytes("UTF-8"));
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		    }
		});
		
		//ToDo
		//session.getProvenanceReporter().receive(flowFile, "OPC");
	
		session.transfer(flowFile,REL_SUCCESS);

	}
	
	
		



}
