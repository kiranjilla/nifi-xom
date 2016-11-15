package org.apache.nifi.service.opcda;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.client.opcda.OPCDAGroupCacheObject;
import org.openscada.opc.lib.da.Group;

/**
 * Created by fdigirolomo on 11/1/16.
 */
@Tags({"opcda", "cache" })
@CapabilityDescription("Provides caching for OPCDA groups.")
public interface OPCDAGroupCacheService {
    public OPCDAGroupCacheObject get(final String groupName);
    public void put(final Group group);
}
