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

    private ConnectionInformation connectionInformation;

    private ScheduledExecutorService executorService;

    private AutoReconnectController controller;

    private Server server;

    private String status;

    public OPCDAConnection(final ConnectionInformation connectionInformation,
                           ScheduledExecutorService executorService) {
        super(connectionInformation, executorService);
        log.info("initiating OPCDA server connection");
        log.info("host: " + connectionInformation.getHost());
        log.info("workgroup or domain: " + connectionInformation.getClsid());
        log.info("class or program id: " + connectionInformation.getDomain());
        this.connectionInformation = connectionInformation;
        this.executorService = executorService;
        this.controller = new AutoReconnectController(this);
        this.status = OPCDAConnectionStatus.AVAILABLE;
        try {
            connect();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (JIException e) {
            e.printStackTrace();
        } catch (AlreadyConnectedException e) {
            e.printStackTrace();
        }
        controller.connect();
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
