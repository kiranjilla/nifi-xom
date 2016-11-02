package org.apache.nifi.service.opcda;

import lombok.Data;
import org.apache.nifi.annotation.lifecycle.OnDisabled;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.client.opcda.OPCDAConnection;
import org.apache.nifi.client.opcda.OPCDAConnectionStatus;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.InitializationException;
import org.openscada.opc.lib.common.ConnectionInformation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Created by fdigirolomo on 10/27/16.
 */
public class OPCDAConnectionPool extends AbstractControllerService implements OPCDAConnectionPoolService {

    private Logger log = Logger.getLogger(getClass().getName());

    private volatile List<OPCDAConnection> connections;

    private List<PropertyDescriptor> descriptors;

    private ConnectionInformation connectionInformation;

    private int initialConnectionCount;

    private int maxConnectionCount;

    private int minConnectionCount;

    private int connectionGrowthIncrement;

    private int connectionRelaseInterval;

    // PROPERTIES
    public static final PropertyDescriptor OPCDA_SERVER_IP_NAME = new PropertyDescriptor
            .Builder().name("OPCDA_SERVER_IP_NAME")
            .description("OPC DA Server Host Name or IP Address")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    public static final PropertyDescriptor OPCDA_WORKGROUP_NAME = new PropertyDescriptor
            .Builder().name("OPCDA_WORKGROUP_NAME")
            .description("OPC DA Server Domain or Workgroup Name")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    public static final PropertyDescriptor OPCDA_USER_NAME = new PropertyDescriptor
            .Builder().name("OPCDA_USER_NAME")
            .description("OPC DA User Name")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    public static final PropertyDescriptor OPCDA_PASSWORD_TEXT = new PropertyDescriptor
            .Builder().name("OPCDA_PASSWORD_TEXT")
            .description("OPC DA Password")
            .required(true)
            .sensitive(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    public static final PropertyDescriptor OPCDA_CLASS_ID_NAME = new PropertyDescriptor
            .Builder().name("OPCDA_CLASS_ID_NAME")
            .description("OPC DA Class or Application ID")
            .required(true)
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .addValidator(StandardValidators.URI_VALIDATOR)
            .build();

    public static final PropertyDescriptor TAG_FILTER = new PropertyDescriptor
            .Builder().name("Tag Filter")
            .description("OPT Tag Filter to limit or constrain tags to a particular group")
            .required(true)
            .defaultValue("*")
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.REGULAR_EXPRESSION_VALIDATOR)
            .build();


    public static final PropertyDescriptor READ_TIMEOUT_MS_ATTRIBUTE = new PropertyDescriptor
            .Builder().name("READ_TIMEOUT_MS_ATTRIBUTE")
            .description("Read Timeout for Read operation from OPC DA Server")
            .required(true)
            .defaultValue("10000")
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor INITIAL_CONNECTION_COUNT = new PropertyDescriptor
            .Builder().name("Initial Connection Count")
            .description("Number of connection to initially establish with OPCDA Server")
            .required(true)
            .defaultValue("10")
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor MAX_CONNECTION_COUNT = new PropertyDescriptor
            .Builder().name("Maximum Connection Count")
            .description("Maxinumber Number of connection to maintain with OPCDA Server")
            .required(true)
            .defaultValue("20")
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor MIN_AVAILABLE_CONNECTION_COUNT = new PropertyDescriptor
            .Builder().name("Minimum Available Count")
            .description("Minimum number of available connections before adding additional connections to pool")
            .required(true)
            .defaultValue("20")
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor CONNECTION_GROWTH_INCREMENT = new PropertyDescriptor
            .Builder().name("Connection Count Growth Increment")
            .description("Number of connections to add to the pool upon minimum available is reached")
            .required(true)
            .defaultValue("20")
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor CONNECTION_RELEASE_INTERVAL = new PropertyDescriptor
            .Builder().name("Connection Count Growth Increment")
            .description("Number of connections to add to the pool upon minimum available is reached")
            .required(true)
            .defaultValue("20")
            .expressionLanguageSupported(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
        descriptors.add(OPCDA_SERVER_IP_NAME);
        descriptors.add(OPCDA_WORKGROUP_NAME);
        descriptors.add(OPCDA_USER_NAME);
        descriptors.add(OPCDA_PASSWORD_TEXT);
        descriptors.add(OPCDA_CLASS_ID_NAME);
        descriptors.add(TAG_FILTER);
        descriptors.add(READ_TIMEOUT_MS_ATTRIBUTE);
        descriptors.add(TAG_FILTER);
        descriptors.add(INITIAL_CONNECTION_COUNT);
        descriptors.add(MAX_CONNECTION_COUNT);
        descriptors.add(MIN_AVAILABLE_CONNECTION_COUNT);
        descriptors.add(CONNECTION_GROWTH_INCREMENT);
        descriptors.add(CONNECTION_RELEASE_INTERVAL);
        this.descriptors = Collections.unmodifiableList(descriptors);

        if (getLogger().isInfoEnabled()) {
            getLogger().info("[ PROPERTY DESCRIPTORS INITIALIZED ]");
            for (PropertyDescriptor i : descriptors) {
                getLogger().info(i.getName());
            }
        }
    }

    @OnEnabled
    public void onConfigured(final ConfigurationContext context) throws InitializationException {
        connectionInformation = new ConnectionInformation();
        connectionInformation.setHost(context.getProperty(OPCDA_SERVER_IP_NAME).getValue());
        connectionInformation.setDomain(context.getProperty(OPCDA_WORKGROUP_NAME).getValue());
        connectionInformation.setUser(context.getProperty(OPCDA_USER_NAME).getValue());
        connectionInformation.setPassword(context.getProperty(OPCDA_PASSWORD_TEXT).getValue());
        connectionInformation.setClsid(context.getProperty(OPCDA_CLASS_ID_NAME).getValue());

        initialConnectionCount = Integer.parseInt(context.getProperty(INITIAL_CONNECTION_COUNT).getValue());
        maxConnectionCount = Integer.parseInt(context.getProperty(MAX_CONNECTION_COUNT).getValue());
        minConnectionCount = Integer.parseInt(context.getProperty(MIN_AVAILABLE_CONNECTION_COUNT).getValue());
        connectionGrowthIncrement = Integer.parseInt(context.getProperty(CONNECTION_GROWTH_INCREMENT).getValue());
        connectionRelaseInterval = Integer.parseInt(context.getProperty(CONNECTION_RELEASE_INTERVAL).getValue());

        for (int i = 0; i < initialConnectionCount; i++) {
            connections.add(new OPCDAConnection(connectionInformation, Executors.newSingleThreadScheduledExecutor()));
        }
    }

    @OnDisabled
    public void shutdown() {
        getLogger().info("releasing connections in pool");
        connections.forEach(connection->{
            connection.disconnect();
        });
        getLogger().info(connections.size() + "connections released");
    }

    public OPCDAConnection getConnection() {
        for (OPCDAConnection connection : connections) {
            if (connection.getStatus().equals(OPCDAConnectionStatus.AVAILABLE)) {
                return connection;
            }
        }
        incrementPoolSize();
        return getConnection();
    }

    private void incrementPoolSize() {
        getLogger().info("incrementing pool size");
        if (connections.size() < maxConnectionCount) {
            if ((maxConnectionCount - connections.size()) <= connectionGrowthIncrement) {
                for (int i = 0; i <= connectionGrowthIncrement; i++) {
                    connections.add(new OPCDAConnection(connectionInformation,
                            Executors.newSingleThreadScheduledExecutor()));
                }
            } else {
                for (int i = 0; i <= maxConnectionCount - connections.size(); i++) {
                    connections.add(new OPCDAConnection(connectionInformation,
                            Executors.newSingleThreadScheduledExecutor()));
                }
            }
        }
    }
}
