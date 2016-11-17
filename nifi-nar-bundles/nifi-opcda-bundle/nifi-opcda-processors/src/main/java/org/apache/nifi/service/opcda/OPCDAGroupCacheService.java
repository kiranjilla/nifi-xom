package org.apache.nifi.service.opcda;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.jinterop.dcom.common.JIException;
import org.openscada.opc.lib.da.Group;

/**
 * Created by fdigirolomo on 11/1/16.
 */
@Tags({"opcda", "cache" })
@CapabilityDescription("Provides caching for OPCDA groups.")
public interface OPCDAGroupCacheService {

    public Group get(final String groupName) throws JIException;

    public void put(final Group group) throws JIException;

    public void release (final Group group) throws JIException;
}
