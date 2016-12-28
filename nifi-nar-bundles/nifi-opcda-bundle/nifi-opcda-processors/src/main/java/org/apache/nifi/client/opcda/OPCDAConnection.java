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

import lombok.Data;
import org.openscada.opc.lib.common.ConnectionInformation;
import org.openscada.opc.lib.da.*;

import java.sql.Connection;
import java.util.Collection;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

@Data
public class OPCDAConnection extends Server {

    private Logger log = Logger.getLogger(getClass().getName());

    static volatile Server server;

    static volatile AccessBase accessBase;

    private ConnectionInformation connectionInformation;

    public OPCDAConnection(final ConnectionInformation connectionInformation,
                           final ScheduledExecutorService executorService) {
        super(connectionInformation, executorService);
        this.connectionInformation = connectionInformation;
        log.info("initiating OPCDA connection");
        log.info("host: " + connectionInformation.getHost());
        log.info("workgroup/domain: " + connectionInformation.getClsid());
        log.info("class/program id: " + connectionInformation.getDomain());
        try {
            super.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
