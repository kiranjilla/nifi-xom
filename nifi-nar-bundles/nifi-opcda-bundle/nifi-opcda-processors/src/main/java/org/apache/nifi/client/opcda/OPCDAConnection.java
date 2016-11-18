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
package org.apache.nifi.client.opcda;

import lombok.Data;
import org.jinterop.dcom.common.JIException;
import org.openscada.opc.lib.common.AlreadyConnectedException;
import org.openscada.opc.lib.common.ConnectionInformation;
import org.openscada.opc.lib.da.AutoReconnectController;
import org.openscada.opc.lib.da.Server;

import java.net.UnknownHostException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

/**
 * Created by fdigirolomo on 10/26/16.
 */
@Data
public class OPCDAConnection extends Server {

    private Logger log = Logger.getLogger(getClass().getName());

    private Server server;

    private String status;

    public OPCDAConnection(final ConnectionInformation connectionInformation,
                           ScheduledExecutorService executorService) {
        super(connectionInformation, executorService);
        log.info("initiating OPCDA connection");
        log.info("host: " + connectionInformation.getHost());
        log.info("workgroup/domain: " + connectionInformation.getClsid());
        log.info("class/program id: " + connectionInformation.getDomain());
        try {
            super.connect();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (JIException e) {
            e.printStackTrace();
        } catch (AlreadyConnectedException e) {
            e.printStackTrace();
        }
    }

//    @Override
//    public void connect() {
//        try {
//            super.connect();
//            this.status = OPCDAConnectionStatus.UNAVAILABLE;
//        } catch (UnknownHostException e) {
//            e.printStackTrace();
//        } catch (JIException e) {
//            e.printStackTrace();
//        } catch (AlreadyConnectedException e) {
//            e.printStackTrace();
//        }
//    }
//
//    @Override
//    public void disconnect() {
//        super.disconnect();
//        this.status = OPCDAConnectionStatus.AVAILABLE;
//    }

}
