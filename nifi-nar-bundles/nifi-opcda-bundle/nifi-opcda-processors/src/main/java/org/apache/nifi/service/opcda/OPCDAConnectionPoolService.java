package org.apache.nifi.service.opcda;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.client.opcda.OPCDAConnection;

/**
 * Created by fdigirolomo on 11/1/16.
 */
@Tags({"opcda", "connection", "pooling", "store"})
@CapabilityDescription("Provides OPCDA Connection Pooling Service for OPCDA Servers. Connections can be asked from pool and returned after usage.")
public interface OPCDAConnectionPoolService {

    public OPCDAConnection getConnection();

    public void releaseConnection(OPCDAConnection connection);
}
