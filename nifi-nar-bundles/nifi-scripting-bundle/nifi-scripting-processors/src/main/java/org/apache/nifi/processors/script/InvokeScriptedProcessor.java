/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.processors.script;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.annotation.behavior.DynamicProperty;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.controller.ControllerServiceLookup;
import org.apache.nifi.controller.NodeTypeProvider;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSessionFactory;
import org.apache.nifi.processor.Processor;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

@Tags({"script", "invoke", "groovy", "python", "jython", "jruby", "ruby", "javascript", "js", "lua", "luaj"})
@CapabilityDescription("Experimental - Invokes a script engine for a Processor defined in the given script. The script must define "
        + "a valid class that implements the Processor interface, and it must set a variable 'processor' to an instance of "
        + "the class. Processor methods such as onTrigger() will be delegated to the scripted Processor instance. Also any "
        + "Relationships or PropertyDescriptors defined by the scripted processor will be added to the configuration dialog.  "
        + "Experimental: Impact of sustained usage not yet verified.")
@DynamicProperty(name = "A script engine property to update", value = "The value to set it to", supportsExpressionLanguage = true,
        description = "Updates a script engine property specified by the Dynamic Property's key with the value specified by the Dynamic Property's value")
@SeeAlso({ExecuteScript.class})
public class InvokeScriptedProcessor extends AbstractScriptProcessor {

    private final AtomicReference<Processor> processor = new AtomicReference<>();
    private final AtomicReference<Collection<ValidationResult>> validationResults = new AtomicReference<>(new ArrayList<>());

    private AtomicBoolean scriptNeedsReload = new AtomicBoolean(true);

    private ScriptEngine scriptEngine = null;
    private volatile String kerberosServicePrincipal = null;
    private volatile File kerberosConfigFile = null;
    private volatile File kerberosServiceKeytab = null;

    /**
     * Returns the valid relationships for this processor as supplied by the
     * script itself.
     *
     * @return a Set of Relationships supported by this processor
     */
    @Override
    public Set<Relationship> getRelationships() {
        final Set<Relationship> relationships = new HashSet<>();
        final Processor instance = processor.get();
        if (instance != null) {
            try {
                final Set<Relationship> rels = instance.getRelationships();
                if (rels != null && !rels.isEmpty()) {
                    relationships.addAll(rels);
                }
            } catch (final Throwable t) {
                final ComponentLog logger = getLogger();
                final String message = "Unable to get relationships from scripted Processor: " + t;

                logger.error(message);
                if (logger.isDebugEnabled()) {
                    logger.error(message, t);
                }
            }
        }
        return Collections.unmodifiableSet(relationships);
    }

    @Override
    protected void init(final ProcessorInitializationContext context) {
        kerberosServicePrincipal = context.getKerberosServicePrincipal();
        kerberosConfigFile = context.getKerberosConfigurationFile();
        kerberosServiceKeytab = context.getKerberosServiceKeytab();
    }

    /**
     * Returns a list of property descriptors supported by this processor. The
     * list always includes properties such as script engine name, script file
     * name, script body name, script arguments, and an external module path. If
     * the scripted processor also defines supported properties, those are added
     * to the list as well.
     *
     * @return a List of PropertyDescriptor objects supported by this processor
     */
    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {

        synchronized (isInitialized) {
            if (!isInitialized.get()) {
                createResources();
            }
        }
        List<PropertyDescriptor> supportedPropertyDescriptors = new ArrayList<>();
        supportedPropertyDescriptors.addAll(descriptors);

        final Processor instance = processor.get();
        if (instance != null) {
            try {
                final List<PropertyDescriptor> instanceDescriptors = instance.getPropertyDescriptors();
                if (instanceDescriptors != null) {
                    supportedPropertyDescriptors.addAll(instanceDescriptors);
                }
            } catch (final Throwable t) {
                final ComponentLog logger = getLogger();
                final String message = "Unable to get property descriptors from Processor: " + t;

                logger.error(message);
                if (logger.isDebugEnabled()) {
                    logger.error(message, t);
                }
            }
        }

        return Collections.unmodifiableList(supportedPropertyDescriptors);
    }

    /**
     * Returns a PropertyDescriptor for the given name. This is for the user to
     * be able to define their own properties which will be available as
     * variables in the script
     *
     * @param propertyDescriptorName used to lookup if any property descriptors
     * exist for that name
     * @return a PropertyDescriptor object corresponding to the specified
     * dynamic property name
     */
    @Override
    protected PropertyDescriptor getSupportedDynamicPropertyDescriptor(final String propertyDescriptorName) {
        return new PropertyDescriptor.Builder()
                .name(propertyDescriptorName)
                .required(false)
                .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
                .expressionLanguageSupported(true)
                .dynamic(true)
                .build();
    }

    /**
     * Performs setup operations when the processor is scheduled to run. This
     * includes evaluating the processor's properties, as well as reloading the
     * script (from file or the "Script Body" property)
     *
     * @param context the context in which to perform the setup operations
     */
    @OnScheduled
    public void setup(final ProcessContext context) {
        scriptEngineName = context.getProperty(SCRIPT_ENGINE).getValue();
        scriptPath = context.getProperty(SCRIPT_FILE).evaluateAttributeExpressions().getValue();
        scriptBody = context.getProperty(SCRIPT_BODY).getValue();
        String modulePath = context.getProperty(MODULES).getValue();
        if (!StringUtils.isEmpty(modulePath)) {
            modules = modulePath.split(",");
        } else {
            modules = new String[0];
        }
        setup();
    }

    public void setup() {
        // Create a single script engine, the Processor object is reused by each task
        super.setup(1);
        scriptEngine = engineQ.poll();
        if (scriptEngine == null) {
            throw new ProcessException("No script engine available!");
        }

        if (scriptNeedsReload.get() || processor.get() == null) {
            if (isFile(scriptPath)) {
                reloadScriptFile(scriptPath);
            } else {
                reloadScriptBody(scriptBody);
            }
            scriptNeedsReload.set(false);
        }
    }

    /**
     * Handles changes to this processor's properties. If changes are made to
     * script- or engine-related properties, the script will be reloaded.
     *
     * @param descriptor of the modified property
     * @param oldValue non-null property value (previous)
     * @param newValue the new property value or if null indicates the property
     */
    @Override
    public void onPropertyModified(final PropertyDescriptor descriptor, final String oldValue, final String newValue) {
        final ComponentLog logger = getLogger();
        final Processor instance = processor.get();

        if (SCRIPT_FILE.equals(descriptor)
                || SCRIPT_BODY.equals(descriptor)
                || MODULES.equals(descriptor)
                || SCRIPT_ENGINE.equals(descriptor)) {
            scriptNeedsReload.set(true);
        } else if (instance != null) {
            // If the script provides a Processor, call its onPropertyModified() method
            try {
                instance.onPropertyModified(descriptor, oldValue, newValue);
            } catch (final Exception e) {
                final String message = "Unable to invoke onPropertyModified from script Processor: " + e;
                logger.error(message, e);
            }
        }
    }

    /**
     * Reloads the script located at the given path
     *
     * @param scriptPath the path to the script file to be loaded
     * @return true if the script was loaded successfully; false otherwise
     */
    private boolean reloadScriptFile(final String scriptPath) {
        final Collection<ValidationResult> results = new HashSet<>();

        try (final FileInputStream scriptStream = new FileInputStream(scriptPath)) {
            return reloadScript(IOUtils.toString(scriptStream));

        } catch (final Exception e) {
            final ComponentLog logger = getLogger();
            final String message = "Unable to load script: " + e;

            logger.error(message, e);
            results.add(new ValidationResult.Builder()
                    .subject("ScriptValidation")
                    .valid(false)
                    .explanation("Unable to load script due to " + e)
                    .input(scriptPath)
                    .build());
        }

        // store the updated validation results
        validationResults.set(results);

        // return whether there was any issues loading the configured script
        return results.isEmpty();
    }

    /**
     * Reloads the script defined by the given string
     *
     * @param scriptBody the contents of the script to be loaded
     * @return true if the script was loaded successfully; false otherwise
     */
    private boolean reloadScriptBody(final String scriptBody) {
        final Collection<ValidationResult> results = new HashSet<>();
        try {
            return reloadScript(scriptBody);

        } catch (final Exception e) {
            final ComponentLog logger = getLogger();
            final String message = "Unable to load script: " + e;

            logger.error(message, e);
            results.add(new ValidationResult.Builder()
                    .subject("ScriptValidation")
                    .valid(false)
                    .explanation("Unable to load script due to " + e)
                    .input(scriptPath)
                    .build());
        }

        // store the updated validation results
        validationResults.set(results);

        // return whether there was any issues loading the configured script
        return results.isEmpty();
    }

    /**
     * Reloads the script Processor. This must be called within the lock.
     *
     * @param scriptBody An input stream associated with the script content
     * @return Whether the script was successfully reloaded
     */
    private boolean reloadScript(final String scriptBody) {
        // note we are starting here with a fresh listing of validation
        // results since we are (re)loading a new/updated script. any
        // existing validation results are not relevant
        final Collection<ValidationResult> results = new HashSet<>();

        try {
            // get the engine and ensure its invocable
            if (scriptEngine instanceof Invocable) {
                final Invocable invocable = (Invocable) scriptEngine;

                // Find a custom configurator and invoke their eval() method
                ScriptEngineConfigurator configurator = scriptEngineConfiguratorMap.get(scriptEngineName.toLowerCase());
                if (configurator != null) {
                    configurator.eval(scriptEngine, scriptBody, modules);
                } else {
                    // evaluate the script
                    scriptEngine.eval(scriptBody);
                }

                // get configured processor from the script (if it exists)
                final Object obj = scriptEngine.get("processor");
                if (obj != null) {
                    final ComponentLog logger = getLogger();

                    try {
                        // set the logger if the processor wants it
                        invocable.invokeMethod(obj, "setLogger", logger);
                    } catch (final NoSuchMethodException nsme) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Configured script Processor does not contain a setLogger method.");
                        }
                    }

                    // record the processor for use later
                    final Processor scriptProcessor = invocable.getInterface(obj, Processor.class);
                    processor.set(scriptProcessor);

                    if (scriptProcessor != null) {
                        try {
                            scriptProcessor.initialize(new ProcessorInitializationContext() {
                                @Override
                                public String getIdentifier() {
                                    return InvokeScriptedProcessor.this.getIdentifier();
                                }

                                @Override
                                public ComponentLog getLogger() {
                                    return logger;
                                }

                                @Override
                                public ControllerServiceLookup getControllerServiceLookup() {
                                    return InvokeScriptedProcessor.super.getControllerServiceLookup();
                                }

                                @Override
                                public NodeTypeProvider getNodeTypeProvider() {
                                    return InvokeScriptedProcessor.super.getNodeTypeProvider();
                                }

                                @Override
                                public String getKerberosServicePrincipal() {
                                    return InvokeScriptedProcessor.this.kerberosServicePrincipal;
                                }

                                @Override
                                public File getKerberosServiceKeytab() {
                                    return InvokeScriptedProcessor.this.kerberosServiceKeytab;
                                }

                                @Override
                                public File getKerberosConfigurationFile() {
                                    return InvokeScriptedProcessor.this.kerberosConfigFile;
                                }
                            });
                        } catch (final Exception e) {
                            logger.error("Unable to initialize scripted Processor: " + e.getLocalizedMessage(), e);
                            throw new ProcessException(e);
                        }
                    }
                } else {
                    throw new ScriptException("No processor was defined by the script.");
                }
            }

        } catch (final Exception ex) {
            final ComponentLog logger = getLogger();
            final String message = "Unable to load script: " + ex.getLocalizedMessage();

            logger.error(message, ex);
            results.add(new ValidationResult.Builder()
                    .subject("ScriptValidation")
                    .valid(false)
                    .explanation("Unable to load script due to " + ex.getLocalizedMessage())
                    .input(scriptPath)
                    .build());
        }

        // store the updated validation results
        validationResults.set(results);

        // return whether there was any issues loading the configured script
        return results.isEmpty();
    }

    /**
     * Invokes the validate() routine provided by the script, allowing for
     * custom validation code. This method assumes there is a valid Processor
     * defined in the script and it has been loaded by the
     * InvokeScriptedProcessor processor
     *
     * @param context The validation context to be passed into the custom
     * validate method
     * @return A collection of ValidationResults returned by the custom validate
     * method
     */
    @Override
    protected Collection<ValidationResult> customValidate(final ValidationContext context) {

        Collection<ValidationResult> commonValidationResults = super.customValidate(context);
        if (!commonValidationResults.isEmpty()) {
            return commonValidationResults;
        }

        scriptEngineName = context.getProperty(SCRIPT_ENGINE).getValue();
        scriptPath = context.getProperty(SCRIPT_FILE).evaluateAttributeExpressions().getValue();
        scriptBody = context.getProperty(SCRIPT_BODY).getValue();
        String modulePath = context.getProperty(MODULES).getValue();
        if (!StringUtils.isEmpty(modulePath)) {
            modules = modulePath.split(",");
        } else {
            modules = new String[0];
        }
        setup();

        // Now that InvokeScriptedProcessor is validated, we can call validate on the scripted processor
        final Processor instance = processor.get();
        final Collection<ValidationResult> currentValidationResults = validationResults.get();

        // if there was existing validation errors and the processor loaded successfully
        if (currentValidationResults.isEmpty() && instance != null) {
            try {
                // defer to the underlying processor for validation
                final Collection<ValidationResult> instanceResults = instance.validate(context);
                if (instanceResults != null && instanceResults.size() > 0) {
                    // return the validation results from the underlying instance
                    return instanceResults;
                }
            } catch (final Exception e) {
                final ComponentLog logger = getLogger();
                final String message = "Unable to validate the script Processor: " + e;
                logger.error(message, e);

                // return a new validation message
                final Collection<ValidationResult> results = new HashSet<>();
                results.add(new ValidationResult.Builder()
                        .subject("Validation")
                        .valid(false)
                        .explanation("An error occurred calling validate in the configured script Processor.")
                        .input(context.getProperty(SCRIPT_FILE).getValue())
                        .build());
                return results;
            }
        }

        return currentValidationResults;
    }

    /**
     * Invokes the onTrigger() method of the scripted processor. If the script
     * failed to reload, the processor yields until the script can be reloaded
     * successfully. If the scripted processor's onTrigger() method throws an
     * exception, a ProcessException will be thrown. If no processor is defined
     * by the script, an error is logged with the system.
     *
     * @param context provides access to convenience methods for obtaining
     * property values, delaying the scheduling of the processor, provides
     * access to Controller Services, etc.
     * @param sessionFactory provides access to a {@link ProcessSessionFactory},
     * which can be used for accessing FlowFiles, etc.
     * @throws ProcessException if the scripted processor's onTrigger() method
     * throws an exception
     */
    @Override
    public void onTrigger(ProcessContext context, ProcessSessionFactory sessionFactory) throws ProcessException {

        // Initialize the rest of the processor resources if we have not already done so
        synchronized (isInitialized) {
            if (!isInitialized.get()) {
                super.createResources();
            }
        }

        ComponentLog log = getLogger();

        // ensure the processor (if it exists) is loaded
        final Processor instance = processor.get();

        // ensure the processor did not fail to reload at some point
        final Collection<ValidationResult> results = validationResults.get();
        if (!results.isEmpty()) {
            log.error(String.format("Unable to run because the Processor is not valid: [%s]",
                    StringUtils.join(results, ", ")));
            context.yield();
            return;
        }
        if (instance != null) {
            try {
                // run the processor
                instance.onTrigger(context, sessionFactory);
            } catch (final ProcessException e) {
                final String message = String.format("An error occurred executing the configured Processor [%s]: %s", context.getProperty(SCRIPT_FILE).getValue(), e);
                log.error(message);
                throw e;
            }
        } else {
            log.error("There is no processor defined by the script");
        }
    }

    @OnStopped
    @Override
    public void stop() {
        super.stop();
        processor.set(null);
    }
}
