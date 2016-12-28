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
 * @author <a href="mailto:fdigirolomo@hortonworks.com">Frank DiGirolomo</a>
 */

package org.apache.nifi.client.opcda;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openscada.opc.lib.common.ConnectionInformation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

/**
 * Created by fdigirolomo on 10/27/16.
 */
public class OPCDAConnectionTest {

    Properties properties = new Properties();

    @Before
    public void init() {
        InputStream is = ClassLoader.getSystemResourceAsStream("test.properties");
        try {
            properties.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testOPCDAConnection() {
        ConnectionInformation connectionInformation = new ConnectionInformation();
        connectionInformation.setHost((String) properties.get("opcda.server.ip.name"));
        connectionInformation.setDomain((String) properties.get("opcda.workgroup.name"));
        connectionInformation.setUser((String) properties.get("opcda.user.name"));
        connectionInformation.setPassword((String) properties.get("opcda.password.text"));
        connectionInformation.setClsid((String) properties.get("opcda.class.id.name"));
        //connectionInformation.setProgId(context.getProperty(OPCDA_PROG_ID_NAME).getValue());
        OPCDAConnection connection = new OPCDAConnection(connectionInformation, newSingleThreadScheduledExecutor());
        Assert.assertNotNull(connection);
    }

}