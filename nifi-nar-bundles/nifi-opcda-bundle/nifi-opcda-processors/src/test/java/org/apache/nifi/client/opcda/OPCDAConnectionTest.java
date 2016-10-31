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
