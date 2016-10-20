package org.apache.nifi.services.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnDisabled;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.controller.ControllerService;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.InitializationException;

import java.io.IOException;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by fdigirolomo on 10/19/16.
 */

@CapabilityDescription("Provide Hazelcast Map")
@Tags({ "grid", "hazelcast", "database", "connection" })
public abstract class HazelcastMapService implements ControllerService {

    private volatile HazelcastInstance hazelcast;

    public static final PropertyDescriptor MAP_NAME = new PropertyDescriptor
            .Builder().name("Map Name")
            .description("Name of Hazelcast Map")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    @OnEnabled
    public void onConfigured(final ConfigurationContext context) throws InitializationException {
        Config config = new Config();
        hazelcast = Hazelcast.newHazelcastInstance(config);
        ConcurrentMap<String, String> map = hazelcast.getMap(context.getProperty(MAP_NAME).getValue());
    }

    @OnDisabled
    public void shutdownServer() throws IOException {
        if (hazelcast != null) {
            hazelcast.shutdown();
        }
        hazelcast = null;
    }

    @Override
    protected void finalize() throws Throwable {
        shutdownServer();
    }

}
