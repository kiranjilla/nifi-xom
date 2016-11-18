package org.apache.nifi.service.opcda;

import org.apache.nifi.client.opcda.OPCDAGroupCacheObject;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.processor.util.StandardValidators;
import org.jinterop.dcom.common.JIException;
import org.openscada.opc.lib.da.Group;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by fdigirolomo on 10/27/16.
 */
public class OPCDAGroupCache extends AbstractControllerService implements OPCDAGroupCacheService {

    private volatile Collection<Group> cache = new ConcurrentLinkedQueue<>();

    public static final PropertyDescriptor OPCDA_CACHE_GROUP_NAME = new PropertyDescriptor.Builder()
            .name("OPCDA Cache Group Name")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    @Override
    public Group get(String groupName) throws JIException {
        Iterator<Group> i = cache.iterator();
        while (i.hasNext()) {
            Group g = i.next();
            if (g.getName().equals(groupName)) {
                return g;
            }
        }
        return null;
    }

    @Override
    public void put(Group group) throws JIException {
        OPCDAGroupCacheObject object = new OPCDAGroupCacheObject(group);
    }

    public void release(Group group) throws JIException {
       cache.remove(group);
       group.remove();
    }

}


