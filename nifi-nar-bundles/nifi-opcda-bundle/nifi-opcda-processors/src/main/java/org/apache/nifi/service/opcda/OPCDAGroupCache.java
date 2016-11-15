package org.apache.nifi.service.opcda;

import org.apache.nifi.client.opcda.OPCDAGroupCacheObject;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ControllerService;
import org.apache.nifi.controller.ControllerServiceInitializationContext;
import org.apache.nifi.reporting.InitializationException;
import org.openscada.opc.lib.da.Group;

import java.util.Collection;
import java.util.List;

/**
 * Created by fdigirolomo on 10/27/16.
 */
public class OPCDAGroupCache extends AbstractControllerService implements OPCDAGroupCacheService {

    @Override
    public OPCDAGroupCacheObject get(String groupName) {
        return null;
    }

    @Override
    public void put(Group group) {

    }
}


