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

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.client.opcda.OPCDAConnection;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.StreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.util.opcda.OPCDAItemStateValueMapper;
import org.openscada.opc.lib.common.ConnectionInformation;
import org.openscada.opc.lib.da.Group;
import org.openscada.opc.lib.da.Item;
import org.openscada.opc.lib.da.ItemState;

@Tags({"opcda opc state tag query"})
@CapabilityDescription("Polls OPC DA Server and create flow file")
@InputRequirement(Requirement.INPUT_ALLOWED)
@SupportsBatching
public class GetOPCDATagState extends AbstractProcessor {



//    protected volatile Collection<OPCDAGroupCacheObject> cache = new ArrayList<>();

//    static boolean caching = false;
//
//    static Integer cacheExpirationInterval;

    static String DELIMITER;

    // PROPERTY DESCRIPTORS

/*    public static final PropertyDescriptor OPCDA_GROUP_CACHE_SERVICE = new PropertyDescriptor.Builder()
            .name("OPCDA Group Cache Service")
            .description("Specifies the Controller Service to use for accessing OPCDA Group Caching.")
            .required(true)
            .identifiesControllerService(OPCDAGroupCache.class)
            .build();*/

    static final PropertyDescriptor OPCDA_SERVER_IP_NAME = new PropertyDescriptor.Builder()
            .name("OPCDA_SERVER_IP_NAME")
            .description("OPC DA Server Host Name or IP Address")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    static final PropertyDescriptor OPCDA_WORKGROUP_NAME = new PropertyDescriptor.Builder()
            .name("OPCDA_WORKGROUP_NAME")
            .description("OPC DA Server Workgroup or Domain Name")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    static final PropertyDescriptor OPCDA_USER_NAME = new PropertyDescriptor.Builder()
            .name("OPCDA_USER_NAME")
            .description("OPC DA User Name")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    static final PropertyDescriptor OPCDA_PASSWORD_TEXT = new PropertyDescriptor.Builder()
            .name("OPCDA_PASSWORD_TEXT")
            .description("OPC DA Password Text")
            .required(true).sensitive(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    static final PropertyDescriptor OPCDA_CLASS_ID_NAME = new PropertyDescriptor.Builder()
            .name("OPCDA_CLASS_ID_NAME")
            .description("OPC DA Class ID")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    static final PropertyDescriptor READ_TIMEOUT_MS_ATTRIBUTE = new PropertyDescriptor.Builder()
            .name("READ_TIMEOUT_MS_ATTRIBUTE")
            .description("Read Timeout for Read operation from OPC DA Server")
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
    
    static final PropertyDescriptor ENABLE_OPC_DEVICE_CACHE = new PropertyDescriptor.Builder()
            .name("OPC Device Cache Enabled")
            .description("OPC Device Cache Enabled?")
            .required(false)
            .defaultValue("false")
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.BOOLEAN_VALIDATOR)
            .build();

//    static final PropertyDescriptor ENABLE_GROUP_CACHE = new PropertyDescriptor.Builder()
//            .name("Enable Group Caching")
//            .description("Enable Group/Item Caching")
//            .required(true)
//            .defaultValue("false")
//            .expressionLanguageSupported(false)
//            .addValidator(StandardValidators.BOOLEAN_VALIDATOR)
//            .build();

//    static final PropertyDescriptor CACHE_REFRESH_INTERVAL = new PropertyDescriptor.Builder()
//            .name("Cache Refresh Interval In Milliseconds")
//            .description("Time in seconds to refresh groups/items in State Table")
//            .required(true)
//            .defaultValue("3600").expressionLanguageSupported(false)
//            .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
//            .build();

    // RELATIONSHIPS
    static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("The FlowFile with transformed content will be routed to this relationship")
            .build();

    static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("The FlowFile with transformed content has failed to this relationship")
            .build();

//    static final Relationship REL_RETRY = new Relationship.Builder()
//            .name("retry")
//            .description("The FlowFile with transformed content will be retried to this relationship")
//            .build();

    static final List<PropertyDescriptor> DESCRIPTORS;
    static Set<Relationship> RELATIONSHIPS;

    static {
        final List<PropertyDescriptor> _descriptors = new ArrayList<>();
        _descriptors.add(OPCDA_SERVER_IP_NAME);
        _descriptors.add(OPCDA_WORKGROUP_NAME);
        _descriptors.add(OPCDA_USER_NAME);
        _descriptors.add(OPCDA_PASSWORD_TEXT);
        _descriptors.add(OPCDA_CLASS_ID_NAME);
        _descriptors.add(READ_TIMEOUT_MS_ATTRIBUTE);
        _descriptors.add(OUTPUT_DELIMIITER);
        _descriptors.add(ENABLE_OPC_DEVICE_CACHE);
        // _descriptors.add(ENABLE_GROUP_CACHE);
        // _descriptors.add(CACHE_REFRESH_INTERVAL);
        DESCRIPTORS = Collections.unmodifiableList(_descriptors);

        final Set<Relationship> _relationships = new HashSet<>();
        _relationships.add(REL_SUCCESS);
        _relationships.add(REL_FAILURE);
        // _relationships.add(REL_RETRY);
        RELATIONSHIPS = Collections.unmodifiableSet(_relationships);
    }

    @Override
    public Set<Relationship> getRelationships() {
        getLogger().debug("relationships: " + Arrays.toString(RELATIONSHIPS.toArray()));
        return RELATIONSHIPS;
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        getLogger().debug("supported property descriptors: " + Arrays.toString(DESCRIPTORS.toArray()));
        return DESCRIPTORS;
    }

    @OnScheduled
    public void onScheduled(final ProcessContext processContext) {
//      caching = Boolean.parseBoolean(processContext.getProperty(ENABLE_GROUP_CACHE).getValue());
//        if (caching) {
//            cacheExpirationInterval = Integer.parseInt(processContext.getProperty(CACHE_REFRESH_INTERVAL).getValue());
//        }
        getLogger().info("esablishing connection from connection information derived from context");
        //connection = getConnection(processContext);
        //getLogger().info("connection state: " + connection.getServerState());
        DELIMITER = processContext.getProperty(OUTPUT_DELIMIITER).getValue();
    }

    @OnStopped
    public void onStopped() {
        getLogger().info("stopping");
//        if (caching) {
//            for (OPCDAGroupCacheObject o : cache) {
//                try {
//                    getLogger().info("releasing cache");
//                    o.getGroup().remove();
//                    cache = null;
//                } catch (JIException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
        getLogger().info("releasing connection");
        //connection.disconnect();
        getLogger().info("processor stopped");
    }

    public void onTrigger1(final ProcessContext processContext, final ProcessSession processSession) {
    	long startTime = System.currentTimeMillis();
    	OPCDAConnection connection = null;
    	Group group = null;
        getLogger().info("[" + processContext.getName() + "]: triggered");
        FlowFile flowfile = processSession.get();
        if (flowfile == null) {
        	return;
        }
        getLogger().info("flowfile obtained from session: " + flowfile.getId());
        String groupName = flowfile.getAttribute("groupName");
        getLogger().info("processing group: " + groupName);
        
        // If scheduler is set to 0, there is a chance of connection getting to null. 
        if(connection == null) {
        	connection = getConnection(processContext);
        }
        getLogger().info("New Connection obtained");

        try {
            group = connection.addGroup(groupName);
            getLogger().info("Group "+groupName+" added to connection");
            Collection<String> itemIds = Collections.synchronizedList(new ArrayList<String>());
            StringBuffer output = new StringBuffer();

//                if (caching && ifCached(groupName)) {
//                    OPCDAGroupCacheObject _cache = getCachedGroup(groupName);
//                    if (_cache != null && !_cache.isExpired(cacheExpirationInterval)) {
//                        getLogger().info("utilizing cache for group: " + groupName);
//                        for (Item i : _cache.getItems()) {
//                            i = _cache.getItem(i);
//                            final String item = processItem(i);
//                            if (!item.isEmpty()) {
//                                output.append(item);
//                            }
//                        }
//                    } else {
//                        getLogger().info("refreshing group cache: " + groupName);
//                        assert _cache != null;
//                        connection.removeGroup(_cache.getGroup(), true);
//                        _cache.getGroup().remove();
//                        Group group = new OPCDAGroupCacheObject(connection.addGroup(groupName)).getGroup();
//                        processSession.read(flowfile, in -> {
//                            if (itemIds.isEmpty()) {
//                                itemIds.addAll(IOUtils.readLines(in, "UTF-8"));
//                            }
//                        });
//                        for (String itemId : itemIds) {
//                            getLogger().info("[" + groupName + "] adding tag to group: " + itemId);
//                            final Item item = group.addItem(itemId);
//                            final String _item = processItem(item);
//                            if (!_item.isEmpty()) {
//                                output.append(_item);
//                            }
//                        }
//                        cache.add(new OPCDAGroupCacheObject(group, items));
//                    }
//                } else if (caching && !ifCached(groupName)) {
//                    Group group = connection.addGroup(groupName);
//                    getLogger().info("caching group: " + groupName);
//                    processSession.read(flowfile, in -> {
//                        if (itemIds.isEmpty()) {
//                            itemIds.addAll(IOUtils.readLines(in, "UTF-8"));
//                        }
//                    });
//                    for (String itemId : itemIds) {
//                        getLogger().info("[" + groupName + "] adding tag to group: " + itemId);
//                        final Item item = group.addItem(itemId);
//                        final String _item = processItem(item);
//                        if (!_item.isEmpty()) {
//                            output.append(_item);
//                            items.add(item);
//                        }
//                    }
//                    getLogger().info("adding group to cache: " + groupName);
//                    cache.add(new OPCDAGroupCacheObject(group, items));
//                } else {

            processSession.read(flowfile, (InputStream in) -> {
                if (itemIds.isEmpty()) itemIds.addAll(IOUtils.readLines(in, "UTF-8"));
            });
            getLogger().info("flowfile information read to get itemIds");
            Collection<Item> items = new ArrayList<>();
            
            Map<String, Item> addedItemMap = group.addItems(itemIds.toArray(new String[itemIds.size()]));
            getLogger().info("group.addItems complete");
            items = addedItemMap.values();
            
//            for (final String itemId : itemIds) {
//                getLogger().info("[" + groupName + "] adding tag to group: " + itemId);
//                Item item = group.addItem(itemId);
//                items.add(item);
                
                
//                final String _item = processItem(item);
//                if (!_item.isEmpty()) {
//                    output.append(_item);
//                }
//            }
            boolean opccaching = Boolean.parseBoolean(processContext.getProperty(ENABLE_OPC_DEVICE_CACHE).getValue());
            
            Map<Item, ItemState> retrievedItemMap = group.read(opccaching, items.toArray(new Item[items.size()]));
            

//                    if (caching) {
//                        cache.add(new OPCDAGroupCacheObject(group, items));
//                    }
//              }
            Iterator<Item> iter = retrievedItemMap.keySet().iterator();
            while(iter.hasNext()) {
            	Item nextItem = iter.next();
            	ItemState itemState = retrievedItemMap.get(nextItem);
            	final String _item = processItem(nextItem, itemState);
            	if(!_item.isEmpty()) {
            		output.append(_item);
            	}
            	
            }
            
            flowfile = processGroup(flowfile, output.toString(), processSession);
            group.remove();
        } catch (final Exception e) {
            e.printStackTrace();
            processSession.transfer(flowfile, REL_FAILURE);
        } finally {
                connection.disconnect();
        }
        long endTime = System.currentTimeMillis();
        getLogger().info("Total time per invocation for group ["+groupName+"] is "+ (endTime-startTime)/1000 +" seconds");
    }

   @Override 
    public void onTrigger(ProcessContext processContext, ProcessSession processSession) throws ProcessException {
	   	getLogger().info("onTrigger invoked");
    	long startTime = System.currentTimeMillis();
        FlowFile flowFile = processSession.get();
        if (flowFile == null) {
            return;
        }
        final boolean opccaching = Boolean.parseBoolean(processContext.getProperty(ENABLE_OPC_DEVICE_CACHE).getValue());
       	final String groupName = flowFile.getAttribute("groupName");
        
        
        try {
            flowFile = processSession.write(flowFile, new StreamCallback() {
                @Override
                public void process(final InputStream rawIn, final OutputStream rawOut) throws IOException {

                	OPCDAConnection connection = null;
                	try{
                		if(connection == null) {
                			getLogger().info("Connection object is null. getConnection method called");
                			connection = getConnection(processContext);
                		}
                	Group group = null;
                	group = connection.addGroup(groupName);
                    getLogger().info("Group "+groupName+" added to connection");
                    Collection<String> itemIds = Collections.synchronizedList(new ArrayList<String>());
                    StringBuffer output = new StringBuffer();
                    
                    final OutputStream out = new BufferedOutputStream(rawOut);
                    itemIds.addAll(IOUtils.readLines(rawIn,"UTF-8"));
                    
                    Map<String, Item> addedItemMap = group.addItems(itemIds.toArray(new String[itemIds.size()]));
                    Collection<Item> items = Collections.synchronizedList(new ArrayList<Item>());
                    
                    items = addedItemMap.values();
                    
                    
                    Map<Item, ItemState> retrievedItemMap = group.read(opccaching, items.toArray(new Item[items.size()]));
            
                    Iterator<Item> iter = retrievedItemMap.keySet().iterator();
                    while(iter.hasNext()) {
                    	Item nextItem = iter.next();
                    	ItemState itemState = retrievedItemMap.get(nextItem);
                    	final String _item = processItem(nextItem, itemState);
                    	if(!_item.isEmpty()) {
                    		output.append(_item);
                    	}
                    	
                    }
                    
                    out.write(output.toString().getBytes());
                    
                    group.remove();
                	}
                	catch(Exception e) {
                		
                	}
                	finally {
                		connection.disconnect();
                	}
                }
            });
        } catch (Exception pe) {
            getLogger().error("Test Error", new Object[]{flowFile, pe});
            processSession.transfer(flowFile, REL_FAILURE);
            return;
        }

        flowFile = processSession.putAttribute(flowFile, CoreAttributes.MIME_TYPE.key(), "application/text");
        processSession.transfer(flowFile, REL_SUCCESS);
        long endTime = System.currentTimeMillis();
        getLogger().info("Total time per invocation is "+ (endTime-startTime)/1000 +" seconds");
    }

    
    private String processItem(final Item item, final ItemState itemState) {
        //getLogger().info("processing tag: " + item.getId());
        StringBuffer sb = new StringBuffer();
        try {           
            if (itemState != null) {
                String value = OPCDAItemStateValueMapper.toJavaType(itemState.getValue()).toString();
                //getLogger().info("[" + item.getGroup().getName() + "] " + item.getId() + ": " + value);
                sb.append(item.getId())
                        .append(DELIMITER)
                        .append(value)
                        .append(DELIMITER)
                        .append(itemState.getTimestamp().getTimeInMillis())
                        .append(DELIMITER)
                        .append(itemState.getQuality())
                        .append(DELIMITER)
                        .append(itemState.getErrorCode())
                        .append("\n");
                //getLogger().info("item output [" + item.getId() + "] " + sb.toString());
            } else {
                throw new Exception(item.getId() + "item state is null");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }
    
    
    private String processItem(final Item item) {
        getLogger().info("processing tag: " + item.getId());
        StringBuffer sb = new StringBuffer();
        try {
            ItemState itemState = item.read(false);
            if (itemState != null) {
                String value = OPCDAItemStateValueMapper.toJavaType(itemState.getValue()).toString();
                getLogger().info("[" + item.getGroup().getName() + "] " + item.getId() + ": " + value);
                sb.append(item.getId())
                        .append(DELIMITER)
                        .append(value)
                        .append(DELIMITER)
                        .append(itemState.getTimestamp().getTimeInMillis())
                        .append(DELIMITER)
                        .append(itemState.getQuality())
                        .append(DELIMITER)
                        .append(itemState.getErrorCode())
                        .append("\n");
                getLogger().info("item output [" + item.getId() + "] " + sb.toString());
            } else {
                throw new Exception(item.getId() + "item state is null");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    private  FlowFile processGroup(FlowFile flowfile, final String output, ProcessSession processSession) throws Exception {
        FlowFile flowfileOut = flowfile; 
    	getLogger().info("processing output: " + output);
        if (output.isEmpty()) {
            getLogger().info("releasing flow file");
            processSession.transfer(flowfileOut, REL_FAILURE);
            //throw new Exception("output empty");
        } else {
            getLogger().info("writing flow file");
            flowfileOut = processSession.write(flowfile, stream -> {
                try {
                    stream.write(output.getBytes("UTF-8"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            // TODO : add provenance support
            // processSession.getProvenanceReporter().receive(flowFile, "OPC");
            processSession.transfer(flowfileOut, REL_SUCCESS);
        }
            return flowfileOut;
    }
    
    
    private  FlowFile processGroup1(FlowFile flowfile, final String output, ProcessSession processSession) throws Exception {
        FlowFile flowfileOut = flowfile; 
    	getLogger().info("processing output: " + output);
        if (output.isEmpty()) {
            getLogger().info("releasing flow file");
            processSession.transfer(flowfileOut, REL_FAILURE);
            //throw new Exception("output empty");
        } else {
            getLogger().info("writing flow file");
            flowfileOut = processSession.write(flowfile, stream -> {
                try {
                    stream.write(output.getBytes("UTF-8"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            // TODO : add provenance support
            // processSession.getProvenanceReporter().receive(flowFile, "OPC");
            processSession.transfer(flowfileOut, REL_SUCCESS);
        }
            return flowfileOut;
    }

//    private boolean ifCached(String groupName) {
//        for (OPCDAGroupCacheObject c : cache) {
//            try {
//                if (cache != null && c.getGroup().getName().equals(groupName)) {
//                    getLogger().info("cache contains group: " + groupName);
//                    return true;
//                }
//            } catch (JIException e) {
//                e.printStackTrace();
//            }
//        }
//        getLogger().info("group not found in cache: " + groupName);
//        return false;
//    }
//    private OPCDAGroupCacheObject getCachedGroup(String groupName) {
//        getLogger().info("retrieving cache for group: " + groupName);
//        for (OPCDAGroupCacheObject c : cache) {
//            try {
//                if (c.getGroup().getName().equals(groupName)) {
//                    getLogger().info("group resides in cache: " + groupName);
//                    return c;
//                } else {
//                    getLogger().info("group not found in cache: " + groupName);
//                }
//            } catch (JIException e) {
//                e.printStackTrace();
//            }
//        }
//        return null;
//    }

    private OPCDAConnection getConnection(final ProcessContext processContext) {
        getLogger().info("aggregating connection information from context");
        ConnectionInformation connectionInformation = new ConnectionInformation();
        connectionInformation.setHost(processContext.getProperty(OPCDA_SERVER_IP_NAME).getValue());
        connectionInformation.setDomain(processContext.getProperty(OPCDA_WORKGROUP_NAME).getValue());
        connectionInformation.setUser(processContext.getProperty(OPCDA_USER_NAME).getValue());
        connectionInformation.setPassword(processContext.getProperty(OPCDA_PASSWORD_TEXT).getValue());
        connectionInformation.setClsid(processContext.getProperty(OPCDA_CLASS_ID_NAME).getValue());
        return new OPCDAConnection(connectionInformation, newSingleThreadScheduledExecutor());

        // TODO : add program name as acceptable attribute value
        //connectionInformation.setProgId(context.getProperty(OPCDA_PROG_ID_NAME).getValue());
    }

}
