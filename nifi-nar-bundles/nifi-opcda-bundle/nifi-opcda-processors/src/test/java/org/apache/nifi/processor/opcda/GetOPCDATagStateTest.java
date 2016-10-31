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

import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

public class GetOPCDATagStateTest {

    private Logger log = Logger.getLogger(this.getClass().getName());

    private Properties props = new Properties();

    @Before
    public void init() {
        TestRunners.newTestRunner(GetOPCDATagState.class);
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", java.util.logging.Level.INFO.toString());
        java.util.logging.Logger.getLogger("org.jinterop").setLevel(java.util.logging.Level.OFF);
        java.util.logging.Logger.getLogger("org.jinterop.dcom.core").setLevel(java.util.logging.Level.OFF);
        java.util.logging.Logger.getLogger("org.openscada.opc.lib.da").setLevel(java.util.logging.Level.OFF);
        InputStream is = ClassLoader.getSystemResourceAsStream("test.properties");
        try {
            props.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testQueryOPCDATagState() throws IOException {

        List<MockFlowFile> flowFiles = null;
        final TestRunner runner = TestRunners.newTestRunner(new GetOPCDATagState());

        runner.setProperty(GetOPCDATagList.OPCDA_SERVER_IP_NAME, (String) props.get("opcda.server.ip.name"));
        runner.setProperty(GetOPCDATagList.OPCDA_WORKGROUP_NAME, (String) props.get("opcda.workgroup.name"));
        runner.setProperty(GetOPCDATagList.OPCDA_USER_NAME, (String) props.get("opcda.user.name"));
        runner.setProperty(GetOPCDATagList.OPCDA_PASSWORD_TEXT, (String) props.get("opcda.password.text"));
        runner.setProperty(GetOPCDATagList.OPCDA_CLASS_ID_NAME, (String) props.get("opcda.class.id.name"));

        runner.setProperty(GetOPCDATagState.READ_TIMEOUT_MS_ATTRIBUTE, (String) props.get("read.timeout.ms.attribute"));
        runner.setProperty(GetOPCDATagState.ENABLE_GROUP_CACHE, (String) props.get("enable.group.cache"));
        runner.setProperty(GetOPCDATagState.CACHE_REFRESH_INTERVAL, (String) props.get("group.cache.interval.ms"));
        runner.setProperty(GetOPCDATagState.OUTPUT_DELIMIITER, (String) props.get("output.delimiter"));

        Map<String, String> attributes1 = new HashMap<String, String>();
        attributes1.put("groupName", "FU-13");
        // attributes1.put("fragment.index", "1");
        runner.enqueue(
                "Channel1.Device1.Tag10\n_System._ProjectTitle\n_System._TotalTagCount\n_System._DateTime\n_System._ActiveTagCount\n",
                attributes1);
        Map<String, String> attributes2 = new HashMap<String, String>();
        attributes2.put("groupName", "FU-14");
        runner.enqueue(
                "Channel1.Device1.Tag1\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1001\nChannel1.Device1.Tag10001\n",
                attributes2);
        Map<String, String> attributes3 = new HashMap<String, String>();
        attributes3.put("groupName", "FU-15");
        runner.enqueue("Channel1.Device1.Tag1\nChannel1.Device1.Tag10002\nChannel1.Device1.Tag10003\n", attributes3);
        Map<String, String> attributes4 = new HashMap<String, String>();
        attributes4.put("groupName", "FU-16");
        runner.enqueue("Channel1.Device1.Tag1\nChannel1.Device1.Tag10000\n", attributes4);
        Map<String, String> attributes5 = new HashMap<String, String>();
        attributes5.put("groupName", "FU-17");
        runner.enqueue("Channel1.Device1.Tag1\nChannel1.Device1.Tag10000\n", attributes5);
        Map<String, String> attributes6 = new HashMap<String, String>();
        attributes6.put("groupName", "FU-18");
        runner.enqueue("Channel1.Device1.Tag1\nChannel1.Device1.Tag10000\n", attributes6);
        Map<String, String> attributes7 = new HashMap<String, String>();
        attributes7.put("groupName", "FU-19");
        runner.enqueue("Channel1.Device1.Tag1\nChannel1.Device1.Tag10000\n", attributes7);
        Map<String, String> attributes8 = new HashMap<String, String>();
        attributes8.put("groupName", "FU-20");
        runner.enqueue("Channel1.Device1.Tag1\nChannel1.Device1.Tag10000\n", attributes8);
        Map<String, String> attributes9 = new HashMap<String, String>();
        attributes9.put("groupName", "FU-21");
        runner.enqueue("Channel1.Device1.Tag1\nChannel1.Device1.Tag10000\n", attributes9);
        Map<String, String> attributes10 = new HashMap<String, String>();
        attributes10.put("groupName", "FU-22");
        runner.enqueue(
                "Channel1.Device1.Tag1\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1007\nChannel1.Device1.Tag10007\nChannel1.Device1.Tag1008\nChannel1.Device1.Tag10008\nChannel1.Device1.Tag1009\nChannel1.Device1.Tag10009\nChannel1.Device1.Tag1003\nChannel1.Device1.Tag10003\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10003\nChannel1.Device1.Tag1003\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1006\nChannel1.Device1.Tag10006\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\nChannel1.Device1.Tag1002\nChannel1.Device1.Tag10000\n",
                attributes10);
        // runner.setProperty(PollOpcUaProcessor.NODE_ID_ATTRIBUTE,
        // "ns=2;s=MyObjectsFolder");
        // Server ns=0 -> ServerStatus (2256 parent) -> CurrentTime NodeId=2258
        // runner.setProperty(PollOpcUaProcessor.NODE_ID_ATTRIBUTE,
        // "ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258,ns=0;i=2259,ns=0;i=2258");

        // runner.setThreadCount(1);
        // runner.run(1, true, true);
        runner.setThreadCount(22);
        runner.run(40, true, true);

        // runner.assertQueueEmpty();
        flowFiles = runner.getFlowFilesForRelationship(GetOPCDATagState.REL_SUCCESS);
        // runner.assertAllFlowFilesTransferred(QueryOPCDATagState.REL_SUCCESS, 4);
        runner.assertTransferCount(GetOPCDATagState.REL_SUCCESS, 10);
        flowFiles.get(0).assertAttributeEquals("path", "target");

    }

}
