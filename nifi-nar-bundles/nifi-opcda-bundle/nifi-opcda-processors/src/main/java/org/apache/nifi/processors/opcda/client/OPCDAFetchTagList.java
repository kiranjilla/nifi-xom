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

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
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
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.processors.opcda.client.util.OPCInitialTagConfig;
import org.jinterop.dcom.common.JIException;
import org.openscada.opc.lib.common.ConnectionInformation;
import org.openscada.opc.lib.da.Server;

@Tags({"opc da client fetch tag list"})
@CapabilityDescription("Polls OPC DA Server and create tag list file")
@InputRequirement(Requirement.INPUT_FORBIDDEN)
@SeeAlso({})
@ReadsAttributes({@ReadsAttribute(attribute="My Property", description="")})
@WritesAttributes({@WritesAttribute(attribute="", description="")})
public class OPCDAFetchTagList extends AbstractProcessor {
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

    public static final PropertyDescriptor MAX_ITEMS_IN_GROUP_ATTRIBUTE = new PropertyDescriptor
    	    .Builder().name("MAX_ITEMS_IN_GROUP_ATTRIBUTE")
    	    .description("No of maximum items to be part of group for Read operation from OPC DA Server")
    	    .required(true)
    	    .defaultValue("3000")
    	    .expressionLanguageSupported(false)
    	    .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
    	    .build();
    
    
    public static final PropertyDescriptor READ_TIMEOUT_MS_ATTRIBUTE = new PropertyDescriptor
            .Builder().name("READ_TIMEOUT_MS_ATTRIBUTE")
            .description("Read Timeout for Read operation from OPC DA Server")
            .required(true)
            .defaultValue("10000")
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
    
    
    private  List<PropertyDescriptor> descriptors;

    private  Set<Relationship> relationships;
    
    private  Server server;                    
    
    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
        descriptors.add(OPCDA_SERVER_IP_NAME);
        descriptors.add(OPCDA_WORKGROUP_NAME);
        descriptors.add(OPCDA_USER_NAME);
        descriptors.add(OPCDA_PASSWORD_TEXT);
        descriptors.add(OPCDA_CLASS_ID_NAME);
        descriptors.add(READ_TIMEOUT_MS_ATTRIBUTE);
        descriptors.add(MAX_ITEMS_IN_GROUP_ATTRIBUTE);
        this.descriptors = Collections.unmodifiableList(descriptors);

        final Set<Relationship> relationships = new HashSet<Relationship>();
        relationships.add(REL_SUCCESS);
        relationships.add(REL_FAILURE);
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
    		server = new Server(ci, Executors.newSingleThreadScheduledExecutor());

    		// connect to server
    		server.connect();
	        	      					    
		} catch (Exception e) {
	        this.getLogger().error("*****OnScheduled creating Client error {} [{}]",new Object[]{e.getMessage(),e.getStackTrace()});
			context.yield();
		}

    }
   

    @OnStopped
    public void onStopped(final ProcessContext context) {
    	try {
    		this.getLogger().info("*****OnStopped disconnecting client ");
	        server.disconnect();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    }    
    
    @Override
    public void onTrigger( ProcessContext context,  ProcessSession session) throws ProcessException {
            
    	try {
	        			        		
	        		createFlowFile( OPCInitialTagConfig.getInstance().fetchAllTags(server,this.getLogger()),session,context);
    		    	   			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
	        this.getLogger().error("*****Ontrigger error {} [{}]",new Object[]{e.getMessage(),e.getStackTrace()});
	        context.yield();
		}

    }

	private void createFlowFile(List<String> itemIds,ProcessSession session,ProcessContext context) throws JIException {
		FlowFile flowFile = session.create();
		flowFile = session.write(flowFile, new OutputStreamCallback() {
		    @Override
		    public void process(final OutputStream outStream) throws IOException {
		        try {
		        	StringBuffer output = new StringBuffer();
		        	for (String item:itemIds) {
		        		output.append(item.toString()+"\n");
		        	}
		        	outStream.write(output.toString().getBytes("UTF-8"));
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		    }
		});

		String fileName = "file-"+context.getProperty(OPCDA_SERVER_IP_NAME).getValue();
		if(fileName != null) { flowFile = session.putAttribute(flowFile, "filename", fileName); }
		session.getProvenanceReporter().receive(flowFile, fileName);
		session.transfer(flowFile, REL_SUCCESS);
	}

}
