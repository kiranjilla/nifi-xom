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
import java.util.List;

import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Before;
import org.junit.Test;


public class FetchTagListTest {

    private TestRunner testRunner;

    @Before
    public void init() {
        testRunner = TestRunners.newTestRunner(FetchTagList.class);
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", java.util.logging.Level.INFO.toString());
        java.util.logging.Logger.getLogger("org.jinterop").setLevel(java.util.logging.Level.OFF);
    }

    
    
    @Test
    public void testFetchTagList() throws IOException {

    	List<MockFlowFile> flowFiles  = null;
        final TestRunner runner = TestRunners.newTestRunner(new FetchTagList());

        runner.setProperty(FetchTagList.OPCDA_SERVER_IP_NAME, "targetopc.l3network.local");
        runner.setProperty(FetchTagList.OPCDA_WORKGROUP_NAME, "WORKGROUP");
        runner.setProperty(FetchTagList.OPCDA_USER_NAME, "bamboo");
        runner.setProperty(FetchTagList.OPCDA_PASSWORD_TEXT, "paper");
        runner.setProperty(FetchTagList.OPCDA_CLASS_ID_NAME, "B3AF0BF6-4C0C-4804-A1222-6F3B160F4397");
        runner.setProperty(FetchTagList.READ_TIMEOUT_MS_ATTRIBUTE, "1000");
        runner.setThreadCount(1);
        
        runner.run(1,true,true);
        
        runner.assertAllFlowFilesTransferred(FetchTagList.REL_SUCCESS, 1);
        flowFiles = runner.getFlowFilesForRelationship(FetchTagList.REL_SUCCESS);
        flowFiles.get(0).assertAttributeEquals("path", "target");

    }
    
    


}