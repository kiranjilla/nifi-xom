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

    private static volatile Server server;

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

}
