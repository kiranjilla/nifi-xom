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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Before;
import org.junit.Test;



public class TestGetOPCDATagList {

	private Logger log = Logger.getLogger(this.getClass().getName());

	private Properties props = new Properties();

	@Before
	public void init() {
		TestRunners.newTestRunner(GetOPCDATagList.class);
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", java.util.logging.Level.INFO.toString());
		java.util.logging.Logger.getLogger("org.jinterop").setLevel(java.util.logging.Level.OFF);
		InputStream is = ClassLoader.getSystemResourceAsStream("test.properties");
		try {
			props.load(is);
		} catch (IOException e) {
			// Handle exception here
		}
	}

	@Test
	public void testFetchTagList() throws IOException {


		List<MockFlowFile> flowFiles = null;
		final TestRunner runner = TestRunners.newTestRunner(new GetOPCDATagList());

		runner.setValidateExpressionUsage(false);
		runner.setProperty(GetOPCDATagList.OPCDA_SERVER_IP_NAME, (String) props.get("opcda.server.ip.name"));
		runner.setProperty(GetOPCDATagList.OPCDA_WORKGROUP_NAME, (String) props.get("opcda.workgroup.name"));
		runner.setProperty(GetOPCDATagList.OPCDA_USER_NAME, (String) props.get("opcda.user.name"));
		runner.setProperty(GetOPCDATagList.OPCDA_PASSWORD_TEXT, (String) props.get("opcda.password.text"));
		runner.setProperty(GetOPCDATagList.OPCDA_CLASS_ID_NAME, (String) props.get("opcda.class.id.name"));

		runner.setProperty(GetOPCDATagState.READ_TIMEOUT_MS_ATTRIBUTE, (String) props.get("read.timeout.ms.attribute"));
		runner.setThreadCount(1);

		runner.run(1, true, true);

		runner.assertAllFlowFilesTransferred(GetOPCDATagList.REL_SUCCESS, 1);
		flowFiles = runner.getFlowFilesForRelationship(GetOPCDATagList.REL_SUCCESS);
		flowFiles.get(0).assertAttributeEquals("path", "target");

	}

}
