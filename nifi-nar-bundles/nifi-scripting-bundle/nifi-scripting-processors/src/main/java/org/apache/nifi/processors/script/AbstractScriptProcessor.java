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

import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationContext;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.Validator;
import org.apache.nifi.logging.ProcessorLog;
import org.apache.nifi.processor.AbstractSessionFactoryProcessor;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.util.StringUtils;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class contains variables and methods common to scripting processors
 */
public abstract class AbstractScriptProcessor extends AbstractSessionFactoryProcessor {

    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("FlowFiles that were successfully processed")
            .build();

    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("FlowFiles that failed to be processed")
            .build();

    public static PropertyDescriptor SCRIPT_ENGINE;

    public static final PropertyDescriptor SCRIPT_FILE = new PropertyDescriptor.Builder()
            .name("Script File")
            .required(false)
            .description("Path to script file to execute. Only one of Script File or Script Body may be used")
            .addValidator(new StandardValidators.FileExistsValidator(true))
            .expressionLanguageSupported(true)
            .build();

    public static final PropertyDescriptor SCRIPT_BODY = new PropertyDescriptor.Builder()
            .name("Script Body")
            .required(false)
            .description("Body of script to execute. Only one of Script File or Script Body may be used")
            .addValidator(Validator.VALID)
            .expressionLanguageSupported(false)
            .build();

    public static final PropertyDescriptor MODULES = new PropertyDescriptor.Builder()
            .name("Module Directory")
            .description("Path to a directory which contains modules required by the script.")
            .required(false)
            .expressionLanguageSupported(true)
            .addValidator(new StandardValidators.DirectoryExistsValidator(true, false))
            .build();

    // A map from engine name to a custom configurator for that engine
    protected final Map<String, ScriptEngineConfigurator> scriptEngineConfiguratorMap = new ConcurrentHashMap<>();
    protected final AtomicBoolean isInitialized = new AtomicBoolean(false);

    protected Map<String, ScriptEngineFactory> scriptEngineFactoryMap;
    protected String scriptEngineName;
    protected String scriptPath;
    protected String scriptBody;
    protected String modulePath;
    protected List<PropertyDescriptor> descriptors;
    protected ScriptEngine scriptEngine;

    /**
     * Custom validation for ensuring exactly one of Script File or Script Body is populated
     *
     * @param validationContext provides a mechanism for obtaining externally
     *                          managed values, such as property values and supplies convenience methods
     *                          for operating on those values
     * @return A collection of validation results
     */
    @Override
    protected Collection<ValidationResult> customValidate(ValidationContext validationContext) {
        Set<ValidationResult> results = new HashSet<>();

        // Verify that exactly one of "script file" or "script body" is set
        Map<PropertyDescriptor, String> propertyMap = validationContext.getProperties();
        if (StringUtils.isEmpty(propertyMap.get(SCRIPT_FILE)) == StringUtils.isEmpty(propertyMap.get(SCRIPT_BODY))) {
            results.add(new ValidationResult.Builder().valid(false).explanation(
                    "Exactly one of Script File or Script Body must be set").build());
        }

        return results;
    }

    /**
     * This method creates all resources needed for the script processor to function, such as script engines,
     * script file reloader threads, etc.
     */
    protected void createResources() {
        descriptors = new ArrayList<>();
        // The following is required for JRuby, should be transparent to everything else.
        // Note this is not done in a ScriptEngineConfigurator, as it is too early in the lifecycle. The
        // setting must be there before the factories/engines are loaded.
        System.setProperty("org.jruby.embed.localvariable.behavior", "persistent");

        // Create list of available engines
        ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
        List<ScriptEngineFactory> scriptEngineFactories = scriptEngineManager.getEngineFactories();
        if (scriptEngineFactories != null) {
            scriptEngineFactoryMap = new HashMap<>(scriptEngineFactories.size());
            List<AllowableValue> engineList = new LinkedList<>();
            for (ScriptEngineFactory factory : scriptEngineFactories) {
                engineList.add(new AllowableValue(factory.getLanguageName()));
                scriptEngineFactoryMap.put(factory.getLanguageName(), factory);
            }

            // Sort the list by name so the list always looks the same.
            Collections.sort(engineList, new Comparator<AllowableValue>() {
                @Override
                public int compare(AllowableValue o1, AllowableValue o2) {
                    if (o1 == null) {
                        return o2 == null ? 0 : 1;
                    }
                    if (o2 == null) {
                        return -1;
                    }
                    return o1.getValue().compareTo(o2.getValue());
                }
            });

            AllowableValue[] engines = engineList.toArray(new AllowableValue[engineList.size()]);

            SCRIPT_ENGINE = new PropertyDescriptor.Builder()
                    .name("Script Engine")
                    .required(true)
                    .description("The engine to execute scripts")
                    .allowableValues(engines)
                    .defaultValue(engines[0].getValue())
                    .required(true)
                    .expressionLanguageSupported(false)
                    .build();
            descriptors.add(SCRIPT_ENGINE);
        }

        descriptors.add(SCRIPT_FILE);
        descriptors.add(SCRIPT_BODY);
        descriptors.add(MODULES);

        isInitialized.set(true);
    }

    /**
     * Determines whether the given path refers to a valid file
     *
     * @param path a path to a file
     * @return true if the path refers to a valid file, false otherwise
     */
    protected boolean isFile(final String path) {
        return path != null && Files.isRegularFile(Paths.get(path));
    }

    /**
     * Performs common setup operations when the processor is scheduled to run. This method assumes the member
     * variables associated with properties have been filled.
     *
     */
    public void setup() {

        if (scriptEngineConfiguratorMap.isEmpty()) {
            ServiceLoader<ScriptEngineConfigurator> configuratorServiceLoader =
                    ServiceLoader.load(ScriptEngineConfigurator.class);
            for (ScriptEngineConfigurator configurator : configuratorServiceLoader) {
                String configuratorScriptEngineName = configurator.getScriptEngineName();
                if (configuratorScriptEngineName != null
                        && configuratorScriptEngineName.equals(scriptEngineName)) {
                    scriptEngineConfiguratorMap.put(configurator.getScriptEngineName(), configurator);
                }
            }
        }
        setupEngine();
    }

    /**
     * Configures the specified script engine. First, the engine is loaded and instantiated using the JSR-223
     * javax.script APIs. Then, if any script configurators have been defined for this engine, their init() method is
     * called, and the configurator is saved for future calls.
     *
     * @see org.apache.nifi.processors.script.ScriptEngineConfigurator
     */
    protected void setupEngine() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            ProcessorLog log = getLogger();

            // Need the right classloader when the engine is created. This ensures the NAR's execution class loader
            // (plus the module path) becomes the parent for the script engine
            ClassLoader scriptEngineModuleClassLoader = createScriptEngineModuleClassLoader(modulePath);
            if (scriptEngineModuleClassLoader != null) {
                Thread.currentThread().setContextClassLoader(scriptEngineModuleClassLoader);
            }
            scriptEngine = createScriptEngine();
            ServiceLoader<ScriptEngineConfigurator> configuratorServiceLoader =
                    ServiceLoader.load(ScriptEngineConfigurator.class);
            for (ScriptEngineConfigurator configurator : configuratorServiceLoader) {
                String configuratorScriptEngineName = configurator.getScriptEngineName();
                try {
                    if (configuratorScriptEngineName != null
                            && configuratorScriptEngineName.equalsIgnoreCase(scriptEngineName)) {
                        configurator.init(scriptEngine, modulePath);
                        scriptEngineConfiguratorMap.put(configurator.getScriptEngineName(), configurator);
                    }
                } catch (ScriptException se) {
                    log.error("Error initializing script engine configurator {}",
                            new Object[]{configuratorScriptEngineName});
                    if (log.isDebugEnabled()) {
                        log.error("Error initializing script engine configurator", se);
                    }
                }
            }

        } finally {
            // Restore original context class loader
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    /**
     * Provides a ScriptEngine corresponding to the currently selected script engine name.
     * ScriptEngineManager.getEngineByName() doesn't use find ScriptEngineFactory.getName(), which
     * is what we used to populate the list. So just search the list of factories until a match is
     * found, then create and return a script engine.
     *
     * @return a Script Engine corresponding to the currently specified name, or null if none is found.
     */
    protected ScriptEngine createScriptEngine() {
        //
        ScriptEngineFactory factory = scriptEngineFactoryMap.get(scriptEngineName);
        if (factory == null) {
            return null;
        }
        return factory.getScriptEngine();
    }

    /**
     * Creates a classloader to be used by the selected script engine and the provided script file. This
     * classloader has this class's classloader as a parent (versus the current thread's context
     * classloader) and also adds the specified module directory to the classpath. This enables scripts
     * to use other scripts, modules, etc. without having to build them into the scripting NAR.
     * If the parameter is null or empty, this class's classloader is returned
     *
     * @param modulePath The path to a directory containing modules to be used by the script(s)
     */
    protected ClassLoader createScriptEngineModuleClassLoader(String modulePath) {
        URLClassLoader newModuleClassLoader = null;
        ClassLoader thisClassLoader = this.getClass().getClassLoader();
        if (StringUtils.isEmpty(modulePath)) {
            return thisClassLoader;
        }
        try {
            newModuleClassLoader =
                    new URLClassLoader(
                            new URL[]{new File(modulePath).toURI().toURL()}, thisClassLoader);
        } catch (MalformedURLException mue) {
            getLogger().error("Couldn't find modules directory at " + modulePath, mue);
        }
        return newModuleClassLoader;
    }
}