package org.apache.nifi.service.opcda;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.controller.ControllerService;
import org.apache.nifi.controller.ControllerServiceInitializationContext;
import org.apache.nifi.reporting.InitializationException;

import java.util.Collection;
import java.util.List;

/**
 * Created by fdigirolomo on 10/27/16.
 */
public class OPCDAGroupCacheController implements ControllerService {


    @Override
    public void initialize(ControllerServiceInitializationContext controllerServiceInitializationContext) throws InitializationException {

    }

    @Override
    public Collection<ValidationResult> validate(ValidationContext validationContext) {
        return null;
    }

    @Override
    public PropertyDescriptor getPropertyDescriptor(String s) {
        return null;
    }

    @Override
    public void onPropertyModified(PropertyDescriptor propertyDescriptor, String s, String s1) {

    }

    @Override
    public List<PropertyDescriptor> getPropertyDescriptors() {
        return null;
    }

    @Override
    public String getIdentifier() {
        return null;
    }
}
