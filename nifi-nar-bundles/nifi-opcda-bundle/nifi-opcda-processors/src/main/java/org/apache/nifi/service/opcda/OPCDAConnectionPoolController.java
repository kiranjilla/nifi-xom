package org.apache.nifi.service.opcda;

import lombok.Data;
import org.apache.nifi.client.opcda.OPCDAConnection;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.controller.ControllerService;
import org.apache.nifi.controller.ControllerServiceInitializationContext;
import org.apache.nifi.reporting.InitializationException;
import org.openscada.opc.lib.da.Server;

import java.util.Collection;
import java.util.List;

/**
 * Created by fdigirolomo on 10/27/16.
 */
@Data
public class OPCDAConnectionPoolController implements ControllerService {

    private Collection<OPCDAConnection> servers;

    private Integer max;

    private Integer min;

    private Integer increment;

    private Integer interval;

    public OPCDAConnectionPoolController(OPCDAConnection connection) {

    }

    @Override
    public Collection<ValidationResult> validate(ValidationContext context) {
        return null;
    }

    @Override
    public PropertyDescriptor getPropertyDescriptor(String name) {
        return null;
    }

    @Override
    public void onPropertyModified(PropertyDescriptor descriptor, String oldValue, String newValue) {

    }

    @Override
    public List<PropertyDescriptor> getPropertyDescriptors() {
        return null;
    }

    @Override
    public String getIdentifier() {
        return null;
    }

    @Override
    public void initialize(ControllerServiceInitializationContext context) throws InitializationException {

    }
}
