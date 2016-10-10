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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Before;
import org.junit.Test;


public class QueryTagStateTest {

    private TestRunner testRunner;

    @Before
    public void init() {
        testRunner = TestRunners.newTestRunner(QueryTagState.class);
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", java.util.logging.Level.INFO.toString());
        java.util.logging.Logger.getLogger("org.jinterop").setLevel(java.util.logging.Level.OFF);
    }

    
    @Test
    public void testQueryTagState() throws IOException {

    	List<MockFlowFile> flowFiles  = null;
        final TestRunner runner = TestRunners.newTestRunner(new QueryTagState());

        runner.setProperty(QueryTagState.OPCDA_SERVER_IP_NAME, "targetopc.l3network.local");
        runner.setProperty(QueryTagState.OPCDA_WORKGROUP_NAME, "WORKGROUP");
        runner.setProperty(QueryTagState.OPCDA_USER_NAME, "bamboo");
        runner.setProperty(QueryTagState.OPCDA_PASSWORD_TEXT, "paper");
        runner.setProperty(QueryTagState.OPCDA_CLASS_ID_NAME, "B3AF0BF6-4C0C-4804-A1222-6F3B160F4397");
        runner.setProperty(QueryTagState.READ_TIMEOUT_MS_ATTRIBUTE, "60000");
        runner.setProperty(QueryTagState.POLL_REPEAT_MS_ATTRIBUTE, "3600000");
        runner.setProperty(QueryTagState.IS_ASYNC_ATTRIBUTE,"Y");
        Map<String, String> attributes1 = new HashMap<String,String>();
        attributes1.put("groupName", "FU-13");
        //attributes1.put("fragment.index", "1");
        runner.enqueue("Channel1.Device1.Tag10\n_System._ProjectTitle\n_System._TotalTagCount\n_System._DateTime\n_System._ActiveTagCount\n", attributes1);
        Map<String, String> attributes2 = new HashMap<String,String>();
        attributes2.put("groupName", "FU-13");
        runner.enqueue("Channel1.Device1.Tag1000\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1001\nChannel1.Device1.Tag10001\n",attributes2);
        Map<String, String> attributes3 = new HashMap<String,String>();
        attributes3.put("groupName", "FU-15");
        runner.enqueue("Channel1.Device1.Tag102\nChannel1.Device1.Tag10002\nChannel1.Device1.Tag10003\n",attributes3);
        Map<String, String> attributes4 = new HashMap<String,String>();
        attributes4.put("groupName", "FU-16");
        runner.enqueue("Channel1.Device1.Tag102\nChannel1.Device1.Tag10000\n",attributes4);
        Map<String, String> attributes5 = new HashMap<String,String>();
        attributes5.put("groupName", "FU-17");
        runner.enqueue("Channel1.Device1.Tag103\nChannel1.Device1.Tag10000\n",attributes5);
        Map<String, String> attributes6 = new HashMap<String,String>();
        attributes6.put("groupName", "FU-18");
        runner.enqueue("Channel1.Device1.Tag104\nChannel1.Device1.Tag10000\n",attributes6);
        Map<String, String> attributes7 = new HashMap<String,String>();
        attributes7.put("groupName", "FU-19");
        runner.enqueue("Channel1.Device1.Tag105\nChannel1.Device1.Tag10000\n",attributes7);
        Map<String, String> attributes8 = new HashMap<String,String>();
        attributes8.put("groupName", "FU-20");
        runner.enqueue("Channel1.Device1.Tag106\nChannel1.Device1.Tag10000\n",attributes8);
        Map<String, String> attributes9 = new HashMap<String,String>();
        attributes9.put("groupName", "FU-21");
        runner.enqueue("Channel1.Device1.Tag1001\nChannel1.Device1.Tag10000\n",attributes9);
        Map<String, String> attributes10 = new HashMap<String,String>();
        attributes10.put("groupName", "FU-22");
        runner.enqueue("Channel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1007\nChannel1.Device1.Tag10007\nChannel1.Device1.Tag1008\nChannel1.Device1.Tag10008\nChannel1.Device1.Tag1009\nChannel1.Device1.Tag10009\nChannel1.Device1.Tag1003\nChannel1.Device1.Tag10003\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10003\nChannel1.Device1.Tag1003\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1006\nChannel1.Device1.Tag10006\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\n",attributes10);
        //runner.setProperty(PollOpcUaProcessor.NODE_ID_ATTRIBUTE, "ns=2;s=MyObjectsFolder");
        //Server ns=0 -> ServerStatus (2256 parent) -> CurrentTime NodeId=2258  
        //runner.setProperty(PollOpcUaProcessor.NODE_ID_ATTRIBUTE, "ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258");
        
        runner.setThreadCount(22);
        runner.run(40,true,true);
        
        //runner.assertQueueEmpty();
        flowFiles = runner.getFlowFilesForRelationship(QueryTagState.REL_SUCCESS);
        //runner.assertAllFlowFilesTransferred(QueryTagState.REL_SUCCESS, 4);
        runner.assertTransferCount(QueryTagState.REL_SUCCESS, 10);
        flowFiles.get(0).assertAttributeEquals("path", "target");

    }
    
    


}