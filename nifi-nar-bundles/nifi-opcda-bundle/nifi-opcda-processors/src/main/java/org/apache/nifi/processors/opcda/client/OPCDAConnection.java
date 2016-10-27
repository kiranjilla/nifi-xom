package org.apache.nifi.processors.opcda.client;

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

    private Logger log = Logger.getLogger(this.getClass().getName());

    private ConnectionInformation connection;

    private ScheduledExecutorService executors;

    private AutoReconnectController controller;

    private Server server;

    public OPCDAConnection(final ConnectionInformation connection, ScheduledExecutorService executors) {
        super(connection, executors);
        log.info("initiating OPCDA server connection");
        log.info("host: " + connection.getHost());
        log.info("workgroup or domain: " + connection.getClsid());
        log.info("class or program id: " + connection.getDomain());
        this.connection = connection;
        this.executors = executors;
        this.controller = new AutoReconnectController(this);
        this.controller.connect();
    }

    public Server getServer() {
        return this.getServer();
    }

}
