
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
package org.apache.nifi.controller.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.nifi.components.state.StateManager;
import org.apache.nifi.components.state.StateManagerProvider;
import org.apache.nifi.controller.FlowController;
import org.apache.nifi.controller.ProcessScheduler;
import org.apache.nifi.controller.ProcessorNode;
import org.apache.nifi.controller.ScheduledState;
import org.apache.nifi.controller.StandardProcessorNode;
import org.apache.nifi.controller.scheduling.StandardProcessScheduler;
import org.apache.nifi.controller.service.mock.DummyProcessor;
import org.apache.nifi.controller.service.mock.MockProcessGroup;
import org.apache.nifi.controller.service.mock.ServiceA;
import org.apache.nifi.controller.service.mock.ServiceB;
import org.apache.nifi.controller.service.mock.ServiceC;
import org.apache.nifi.groups.ProcessGroup;
import org.apache.nifi.groups.StandardProcessGroup;
import org.apache.nifi.processor.StandardValidationContextFactory;
import org.apache.nifi.registry.VariableRegistry;
import org.apache.nifi.util.NiFiProperties;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

public class TestStandardControllerServiceProvider {

    private static StateManagerProvider stateManagerProvider = new StateManagerProvider() {
        @Override
        public StateManager getStateManager(final String componentId) {
            return Mockito.mock(StateManager.class);
        }

        @Override
        public void shutdown() {
        }

        @Override
        public void enableClusterProvider() {
        }

        @Override
        public void disableClusterProvider() {
        }

        @Override
        public void onComponentRemoved(final String componentId) {
        }
    };

    private static VariableRegistry variableRegistry = VariableRegistry.ENVIRONMENT_SYSTEM_REGISTRY;

    @BeforeClass
    public static void setNiFiProps() {
        System.setProperty(NiFiProperties.PROPERTIES_FILE_PATH, TestStandardControllerServiceProvider.class.getResource("/conf/nifi.properties").getFile());
    }

    private StandardProcessScheduler createScheduler() {
        return new StandardProcessScheduler(null, null, stateManagerProvider, variableRegistry, NiFiProperties.createBasicNiFiProperties(null, null));
    }

    @Test
    public void testDisableControllerService() {
        final ProcessGroup procGroup = new MockProcessGroup();
        final FlowController controller = Mockito.mock(FlowController.class);
        Mockito.when(controller.getGroup(Mockito.anyString())).thenReturn(procGroup);

        final ProcessScheduler scheduler = createScheduler();
        final StandardControllerServiceProvider provider =
                new StandardControllerServiceProvider(controller, scheduler, null, stateManagerProvider, variableRegistry, NiFiProperties.createBasicNiFiProperties(null, null));

        final ControllerServiceNode serviceNode = provider.createControllerService(ServiceB.class.getName(), "B", false);
        provider.enableControllerService(serviceNode);
        provider.disableControllerService(serviceNode);
    }

    @Test(timeout = 10000)
    public void testEnableDisableWithReference() {
        final ProcessGroup group = new MockProcessGroup();
        final FlowController controller = Mockito.mock(FlowController.class);
        Mockito.when(controller.getGroup(Mockito.anyString())).thenReturn(group);

        final ProcessScheduler scheduler = createScheduler();
        final StandardControllerServiceProvider provider =
                new StandardControllerServiceProvider(controller, scheduler, null, stateManagerProvider, variableRegistry, NiFiProperties.createBasicNiFiProperties(null, null));

        final ControllerServiceNode serviceNodeB = provider.createControllerService(ServiceB.class.getName(), "B", false);
        final ControllerServiceNode serviceNodeA = provider.createControllerService(ServiceA.class.getName(), "A", false);
        group.addControllerService(serviceNodeA);
        group.addControllerService(serviceNodeB);

        serviceNodeA.setProperty(ServiceA.OTHER_SERVICE.getName(), "B");

        try {
            provider.enableControllerService(serviceNodeA);
            Assert.fail("Was able to enable Service A but Service B is disabled.");
        } catch (final IllegalStateException expected) {
        }

        provider.enableControllerService(serviceNodeB);
        provider.enableControllerService(serviceNodeA);

        try {
            provider.disableControllerService(serviceNodeB);
            Assert.fail("Was able to disable Service B but Service A is enabled and references B");
        } catch (final IllegalStateException expected) {
        }

        provider.disableControllerService(serviceNodeA);
        waitForServiceState(serviceNodeA, ControllerServiceState.DISABLED);

        provider.disableControllerService(serviceNodeB);
        waitForServiceState(serviceNodeB, ControllerServiceState.DISABLED);
    }

    private void waitForServiceState(final ControllerServiceNode service, final ControllerServiceState desiredState) {
        while (service.getState() != desiredState) {
            try {
                Thread.sleep(50L);
            } catch (final InterruptedException e) {
            }
        }
    }

    /**
     * We run the same test 1000 times and prior to bug fix (see NIFI-1143) it
     * would fail on some iteration. For more details please see
     * {@link PropertyDescriptor}.isDependentServiceEnableable() as well as
     * https://issues.apache.org/jira/browse/NIFI-1143
     */
    @Test(timeout = 60000)
    public void testConcurrencyWithEnablingReferencingServicesGraph() {
        final ProcessScheduler scheduler = createScheduler();
        for (int i = 0; i < 10000; i++) {
            testEnableReferencingServicesGraph(scheduler);
        }
    }

    public void testEnableReferencingServicesGraph(final ProcessScheduler scheduler) {
        final ProcessGroup procGroup = new MockProcessGroup();
        final FlowController controller = Mockito.mock(FlowController.class);
        Mockito.when(controller.getGroup(Mockito.anyString())).thenReturn(procGroup);

        final StandardControllerServiceProvider provider =
                new StandardControllerServiceProvider(controller, scheduler, null, stateManagerProvider, variableRegistry, NiFiProperties.createBasicNiFiProperties(null, null));

        // build a graph of controller services with dependencies as such:
        //
        // A -> B -> D
        // C ---^----^
        //
        // In other words, A references B, which references D.
        // AND
        // C references B and D.
        //
        // So we have to verify that if D is enabled, when we enable its referencing services,
        // we enable C and B, even if we attempt to enable C before B... i.e., if we try to enable C, we cannot do so
        // until B is first enabled so ensure that we enable B first.
        final ControllerServiceNode serviceNode1 = provider.createControllerService(ServiceA.class.getName(), "1", false);
        final ControllerServiceNode serviceNode2 = provider.createControllerService(ServiceA.class.getName(), "2", false);
        final ControllerServiceNode serviceNode3 = provider.createControllerService(ServiceA.class.getName(), "3", false);
        final ControllerServiceNode serviceNode4 = provider.createControllerService(ServiceB.class.getName(), "4", false);

        procGroup.addControllerService(serviceNode1);
        procGroup.addControllerService(serviceNode2);
        procGroup.addControllerService(serviceNode3);
        procGroup.addControllerService(serviceNode4);

        serviceNode1.setProperty(ServiceA.OTHER_SERVICE.getName(), "2");
        serviceNode2.setProperty(ServiceA.OTHER_SERVICE.getName(), "4");
        serviceNode3.setProperty(ServiceA.OTHER_SERVICE.getName(), "2");
        serviceNode3.setProperty(ServiceA.OTHER_SERVICE_2.getName(), "4");

        provider.enableControllerService(serviceNode4);
        provider.enableReferencingServices(serviceNode4);

        // Verify that the services are either ENABLING or ENABLED, and wait for all of them to become ENABLED.
        // Note that we set a timeout of 10 seconds, in case a bug occurs and the services never become ENABLED.
        final Set<ControllerServiceState> validStates = new HashSet<>();
        validStates.add(ControllerServiceState.ENABLED);
        validStates.add(ControllerServiceState.ENABLING);

        while (serviceNode3.getState() != ControllerServiceState.ENABLED || serviceNode2.getState() != ControllerServiceState.ENABLED || serviceNode1.getState() != ControllerServiceState.ENABLED) {
            assertTrue(validStates.contains(serviceNode3.getState()));
            assertTrue(validStates.contains(serviceNode2.getState()));
            assertTrue(validStates.contains(serviceNode1.getState()));
        }
    }

    @Test
    public void testOrderingOfServices() {
        final ProcessGroup procGroup = new MockProcessGroup();
        final FlowController controller = Mockito.mock(FlowController.class);
        Mockito.when(controller.getGroup(Mockito.anyString())).thenReturn(procGroup);

        final StandardControllerServiceProvider provider =
                new StandardControllerServiceProvider(controller, null, null, stateManagerProvider, variableRegistry, NiFiProperties.createBasicNiFiProperties(null, null));
        final ControllerServiceNode serviceNode1 = provider.createControllerService(ServiceA.class.getName(), "1", false);
        final ControllerServiceNode serviceNode2 = provider.createControllerService(ServiceB.class.getName(), "2", false);

        serviceNode1.setProperty(ServiceA.OTHER_SERVICE.getName(), "2");

        final Map<String, ControllerServiceNode> nodeMap = new LinkedHashMap<>();
        nodeMap.put("1", serviceNode1);
        nodeMap.put("2", serviceNode2);

        List<List<ControllerServiceNode>> branches = StandardControllerServiceProvider.determineEnablingOrder(nodeMap);
        assertEquals(2, branches.size());
        List<ControllerServiceNode> ordered = branches.get(0);
        assertEquals(2, ordered.size());
        assertTrue(ordered.get(0) == serviceNode2);
        assertTrue(ordered.get(1) == serviceNode1);
        assertEquals(1, branches.get(1).size());
        assertTrue(branches.get(1).get(0) == serviceNode2);

        nodeMap.clear();
        nodeMap.put("2", serviceNode2);
        nodeMap.put("1", serviceNode1);

        branches = StandardControllerServiceProvider.determineEnablingOrder(nodeMap);
        assertEquals(2, branches.size());
        ordered = branches.get(1);
        assertEquals(2, ordered.size());
        assertTrue(ordered.get(0) == serviceNode2);
        assertTrue(ordered.get(1) == serviceNode1);
        assertEquals(1, branches.get(0).size());
        assertTrue(branches.get(0).get(0) == serviceNode2);

        // add circular dependency on self.
        nodeMap.clear();
        serviceNode1.setProperty(ServiceA.OTHER_SERVICE_2.getName(), "1");
        nodeMap.put("1", serviceNode1);
        nodeMap.put("2", serviceNode2);

        branches = StandardControllerServiceProvider.determineEnablingOrder(nodeMap);
        assertEquals(2, branches.size());
        ordered = branches.get(0);
        assertEquals(2, ordered.size());
        assertTrue(ordered.get(0) == serviceNode2);
        assertTrue(ordered.get(1) == serviceNode1);

        nodeMap.clear();
        nodeMap.put("2", serviceNode2);
        nodeMap.put("1", serviceNode1);
        branches = StandardControllerServiceProvider.determineEnablingOrder(nodeMap);
        assertEquals(2, branches.size());
        ordered = branches.get(1);
        assertEquals(2, ordered.size());
        assertTrue(ordered.get(0) == serviceNode2);
        assertTrue(ordered.get(1) == serviceNode1);

        // add circular dependency once removed. In this case, we won't actually be able to enable these because of the
        // circular dependency because they will never be valid because they will always depend on a disabled service.
        // But we want to ensure that the method returns successfully without throwing a StackOverflowException or anything
        // like that.
        nodeMap.clear();
        final ControllerServiceNode serviceNode3 = provider.createControllerService(ServiceA.class.getName(), "3", false);
        serviceNode1.setProperty(ServiceA.OTHER_SERVICE.getName(), "3");
        serviceNode3.setProperty(ServiceA.OTHER_SERVICE.getName(), "1");
        nodeMap.put("1", serviceNode1);
        nodeMap.put("3", serviceNode3);
        branches = StandardControllerServiceProvider.determineEnablingOrder(nodeMap);
        assertEquals(2, branches.size());
        ordered = branches.get(0);
        assertEquals(2, ordered.size());
        assertTrue(ordered.get(0) == serviceNode3);
        assertTrue(ordered.get(1) == serviceNode1);

        nodeMap.clear();
        nodeMap.put("3", serviceNode3);
        nodeMap.put("1", serviceNode1);
        branches = StandardControllerServiceProvider.determineEnablingOrder(nodeMap);
        assertEquals(2, branches.size());
        ordered = branches.get(1);
        assertEquals(2, ordered.size());
        assertTrue(ordered.get(0) == serviceNode3);
        assertTrue(ordered.get(1) == serviceNode1);

        // Add multiple completely disparate branches.
        nodeMap.clear();
        serviceNode1.setProperty(ServiceA.OTHER_SERVICE.getName(), "2");
        final ControllerServiceNode serviceNode4 = provider.createControllerService(ServiceB.class.getName(), "4", false);
        final ControllerServiceNode serviceNode5 = provider.createControllerService(ServiceB.class.getName(), "5", false);
        serviceNode3.setProperty(ServiceA.OTHER_SERVICE.getName(), "4");
        nodeMap.put("1", serviceNode1);
        nodeMap.put("2", serviceNode2);
        nodeMap.put("3", serviceNode3);
        nodeMap.put("4", serviceNode4);
        nodeMap.put("5", serviceNode5);

        branches = StandardControllerServiceProvider.determineEnablingOrder(nodeMap);
        assertEquals(5, branches.size());

        ordered = branches.get(0);
        assertEquals(2, ordered.size());
        assertTrue(ordered.get(0) == serviceNode2);
        assertTrue(ordered.get(1) == serviceNode1);

        assertEquals(1, branches.get(1).size());
        assertTrue(branches.get(1).get(0) == serviceNode2);

        ordered = branches.get(2);
        assertEquals(2, ordered.size());
        assertTrue(ordered.get(0) == serviceNode4);
        assertTrue(ordered.get(1) == serviceNode3);

        assertEquals(1, branches.get(3).size());
        assertTrue(branches.get(3).get(0) == serviceNode4);

        assertEquals(1, branches.get(4).size());
        assertTrue(branches.get(4).get(0) == serviceNode5);

        // create 2 branches both dependent on the same service
        nodeMap.clear();
        serviceNode1.setProperty(ServiceA.OTHER_SERVICE.getName(), "2");
        serviceNode3.setProperty(ServiceA.OTHER_SERVICE.getName(), "2");
        nodeMap.put("1", serviceNode1);
        nodeMap.put("2", serviceNode2);
        nodeMap.put("3", serviceNode3);

        branches = StandardControllerServiceProvider.determineEnablingOrder(nodeMap);
        assertEquals(3, branches.size());

        ordered = branches.get(0);
        assertEquals(2, ordered.size());
        assertTrue(ordered.get(0) == serviceNode2);
        assertTrue(ordered.get(1) == serviceNode1);

        ordered = branches.get(1);
        assertEquals(1, ordered.size());
        assertTrue(ordered.get(0) == serviceNode2);

        ordered = branches.get(2);
        assertEquals(2, ordered.size());
        assertTrue(ordered.get(0) == serviceNode2);
        assertTrue(ordered.get(1) == serviceNode3);
    }

    private ProcessorNode createProcessor(final StandardProcessScheduler scheduler, final ControllerServiceProvider serviceProvider) {
        final ProcessorNode procNode = new StandardProcessorNode(new DummyProcessor(), UUID.randomUUID().toString(),
                new StandardValidationContextFactory(serviceProvider, null), scheduler, serviceProvider, NiFiProperties.createBasicNiFiProperties(null, null));

        final ProcessGroup group = new StandardProcessGroup(UUID.randomUUID().toString(), serviceProvider, scheduler, null, null, null, variableRegistry);
        group.addProcessor(procNode);
        procNode.setProcessGroup(group);

        return procNode;
    }

    @Test
    public void testEnableReferencingComponents() {
        final ProcessGroup procGroup = new MockProcessGroup();
        final FlowController controller = Mockito.mock(FlowController.class);
        Mockito.when(controller.getGroup(Mockito.anyString())).thenReturn(procGroup);

        final StandardProcessScheduler scheduler = createScheduler();
        final StandardControllerServiceProvider provider =
                new StandardControllerServiceProvider(controller, null, null, stateManagerProvider, variableRegistry, NiFiProperties.createBasicNiFiProperties(null, null));
        final ControllerServiceNode serviceNode = provider.createControllerService(ServiceA.class.getName(), "1", false);

        final ProcessorNode procNode = createProcessor(scheduler, provider);
        serviceNode.addReference(procNode);

        // procNode.setScheduledState(ScheduledState.STOPPED);
        provider.unscheduleReferencingComponents(serviceNode);
        assertEquals(ScheduledState.STOPPED, procNode.getScheduledState());

        // procNode.setScheduledState(ScheduledState.RUNNING);
        provider.unscheduleReferencingComponents(serviceNode);
        assertEquals(ScheduledState.STOPPED, procNode.getScheduledState());
    }

    @Test
    public void validateEnableServices() {
        StandardProcessScheduler scheduler = createScheduler();
        FlowController controller = Mockito.mock(FlowController.class);
        StandardControllerServiceProvider provider =
                new StandardControllerServiceProvider(controller, scheduler, null, stateManagerProvider, variableRegistry, NiFiProperties.createBasicNiFiProperties(null, null));
        ProcessGroup procGroup = new MockProcessGroup();
        Mockito.when(controller.getGroup(Mockito.anyString())).thenReturn(procGroup);

        ControllerServiceNode A = provider.createControllerService(ServiceA.class.getName(), "A", false);
        ControllerServiceNode B = provider.createControllerService(ServiceA.class.getName(), "B", false);
        ControllerServiceNode C = provider.createControllerService(ServiceA.class.getName(), "C", false);
        ControllerServiceNode D = provider.createControllerService(ServiceB.class.getName(), "D", false);
        ControllerServiceNode E = provider.createControllerService(ServiceA.class.getName(), "E", false);
        ControllerServiceNode F = provider.createControllerService(ServiceB.class.getName(), "F", false);

        procGroup.addControllerService(A);
        procGroup.addControllerService(B);
        procGroup.addControllerService(C);
        procGroup.addControllerService(D);
        procGroup.addControllerService(E);
        procGroup.addControllerService(F);

        A.setProperty(ServiceA.OTHER_SERVICE.getName(), "B");
        B.setProperty(ServiceA.OTHER_SERVICE.getName(), "D");
        C.setProperty(ServiceA.OTHER_SERVICE.getName(), "B");
        C.setProperty(ServiceA.OTHER_SERVICE_2.getName(), "D");
        E.setProperty(ServiceA.OTHER_SERVICE.getName(), "A");
        E.setProperty(ServiceA.OTHER_SERVICE_2.getName(), "F");

        provider.enableControllerServices(Arrays.asList(A, B, C, D, E, F));

        assertTrue(A.isActive());
        assertTrue(B.isActive());
        assertTrue(C.isActive());
        assertTrue(D.isActive());
        assertTrue(E.isActive());
        assertTrue(F.isActive());
    }

    /**
     * This test is similar to the above, but different combination of service
     * dependencies
     *
     */
    @Test
    public void validateEnableServices2() {
        StandardProcessScheduler scheduler = createScheduler();
        FlowController controller = Mockito.mock(FlowController.class);
        StandardControllerServiceProvider provider = new StandardControllerServiceProvider(controller, scheduler, null,
                stateManagerProvider, variableRegistry, NiFiProperties.createBasicNiFiProperties(null, null));
        ProcessGroup procGroup = new MockProcessGroup();
        Mockito.when(controller.getGroup(Mockito.anyString())).thenReturn(procGroup);

        ControllerServiceNode A = provider.createControllerService(ServiceC.class.getName(), "A", false);
        ControllerServiceNode B = provider.createControllerService(ServiceA.class.getName(), "B", false);
        ControllerServiceNode C = provider.createControllerService(ServiceB.class.getName(), "C", false);
        ControllerServiceNode D = provider.createControllerService(ServiceA.class.getName(), "D", false);
        ControllerServiceNode F = provider.createControllerService(ServiceA.class.getName(), "F", false);

        procGroup.addControllerService(A);
        procGroup.addControllerService(B);
        procGroup.addControllerService(C);
        procGroup.addControllerService(D);
        procGroup.addControllerService(F);

        A.setProperty(ServiceC.REQ_SERVICE_1.getName(), "B");
        A.setProperty(ServiceC.REQ_SERVICE_2.getName(), "D");
        B.setProperty(ServiceA.OTHER_SERVICE.getName(), "C");

        F.setProperty(ServiceA.OTHER_SERVICE.getName(), "D");
        D.setProperty(ServiceA.OTHER_SERVICE.getName(), "C");

        provider.enableControllerServices(Arrays.asList(C, F, A, B, D));

        assertTrue(A.isActive());
        assertTrue(B.isActive());
        assertTrue(C.isActive());
        assertTrue(D.isActive());
        assertTrue(F.isActive());
    }

    @Test
    public void validateEnableServicesWithDisabledMissingService() {
        StandardProcessScheduler scheduler = createScheduler();
        FlowController controller = Mockito.mock(FlowController.class);
        StandardControllerServiceProvider provider =
                new StandardControllerServiceProvider(controller, scheduler, null, stateManagerProvider, variableRegistry, NiFiProperties.createBasicNiFiProperties(null, null));
        ProcessGroup procGroup = new MockProcessGroup();
        Mockito.when(controller.getGroup(Mockito.anyString())).thenReturn(procGroup);

        ControllerServiceNode serviceNode1 = provider.createControllerService(ServiceA.class.getName(), "1", false);
        ControllerServiceNode serviceNode2 = provider.createControllerService(ServiceA.class.getName(), "2", false);
        ControllerServiceNode serviceNode3 = provider.createControllerService(ServiceA.class.getName(), "3", false);
        ControllerServiceNode serviceNode4 = provider.createControllerService(ServiceB.class.getName(), "4", false);
        ControllerServiceNode serviceNode5 = provider.createControllerService(ServiceA.class.getName(), "5", false);
        ControllerServiceNode serviceNode6 = provider.createControllerService(ServiceB.class.getName(), "6", false);
        ControllerServiceNode serviceNode7 = provider.createControllerService(ServiceC.class.getName(), "7", false);

        procGroup.addControllerService(serviceNode1);
        procGroup.addControllerService(serviceNode2);
        procGroup.addControllerService(serviceNode3);
        procGroup.addControllerService(serviceNode4);
        procGroup.addControllerService(serviceNode5);
        procGroup.addControllerService(serviceNode6);
        procGroup.addControllerService(serviceNode7);

        serviceNode1.setProperty(ServiceA.OTHER_SERVICE.getName(), "2");
        serviceNode2.setProperty(ServiceA.OTHER_SERVICE.getName(), "4");
        serviceNode3.setProperty(ServiceA.OTHER_SERVICE.getName(), "2");
        serviceNode3.setProperty(ServiceA.OTHER_SERVICE_2.getName(), "4");
        serviceNode5.setProperty(ServiceA.OTHER_SERVICE.getName(), "6");
        serviceNode7.setProperty(ServiceC.REQ_SERVICE_1.getName(), "2");
        serviceNode7.setProperty(ServiceC.REQ_SERVICE_2.getName(), "3");

        provider.enableControllerServices(Arrays.asList(
                serviceNode1, serviceNode2, serviceNode3, serviceNode4, serviceNode5, serviceNode7));
        assertFalse(serviceNode1.isActive());
        assertFalse(serviceNode2.isActive());
        assertFalse(serviceNode3.isActive());
        assertFalse(serviceNode4.isActive());
        assertFalse(serviceNode5.isActive());
        assertFalse(serviceNode6.isActive());

        provider.enableControllerService(serviceNode6);
        provider.enableControllerServices(Arrays.asList(
                serviceNode1, serviceNode2, serviceNode3, serviceNode4, serviceNode5));

        assertTrue(serviceNode1.isActive());
        assertTrue(serviceNode2.isActive());
        assertTrue(serviceNode3.isActive());
        assertTrue(serviceNode4.isActive());
        assertTrue(serviceNode5.isActive());
        assertTrue(serviceNode6.isActive());
    }
}
