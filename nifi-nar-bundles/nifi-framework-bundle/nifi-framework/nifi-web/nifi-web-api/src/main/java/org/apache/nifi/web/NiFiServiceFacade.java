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
package org.apache.nifi.web;

import org.apache.nifi.authorization.AuthorizeAccess;
import org.apache.nifi.authorization.RequestAction;
import org.apache.nifi.authorization.user.NiFiUser;
import org.apache.nifi.controller.ScheduledState;
import org.apache.nifi.controller.repository.claim.ContentDirection;
import org.apache.nifi.controller.service.ControllerServiceState;
import org.apache.nifi.groups.ProcessGroup;
import org.apache.nifi.web.api.dto.AccessPolicyDTO;
import org.apache.nifi.web.api.dto.BulletinBoardDTO;
import org.apache.nifi.web.api.dto.BulletinQueryDTO;
import org.apache.nifi.web.api.dto.ClusterDTO;
import org.apache.nifi.web.api.dto.ComponentHistoryDTO;
import org.apache.nifi.web.api.dto.ComponentStateDTO;
import org.apache.nifi.web.api.dto.ConnectionDTO;
import org.apache.nifi.web.api.dto.ControllerConfigurationDTO;
import org.apache.nifi.web.api.dto.ControllerDTO;
import org.apache.nifi.web.api.dto.ControllerServiceDTO;
import org.apache.nifi.web.api.dto.CounterDTO;
import org.apache.nifi.web.api.dto.CountersDTO;
import org.apache.nifi.web.api.dto.DocumentedTypeDTO;
import org.apache.nifi.web.api.dto.DropRequestDTO;
import org.apache.nifi.web.api.dto.FlowFileDTO;
import org.apache.nifi.web.api.dto.FunnelDTO;
import org.apache.nifi.web.api.dto.LabelDTO;
import org.apache.nifi.web.api.dto.ListingRequestDTO;
import org.apache.nifi.web.api.dto.NodeDTO;
import org.apache.nifi.web.api.dto.PortDTO;
import org.apache.nifi.web.api.dto.ProcessGroupDTO;
import org.apache.nifi.web.api.dto.ProcessorDTO;
import org.apache.nifi.web.api.dto.PropertyDescriptorDTO;
import org.apache.nifi.web.api.dto.RemoteProcessGroupDTO;
import org.apache.nifi.web.api.dto.RemoteProcessGroupPortDTO;
import org.apache.nifi.web.api.dto.ReportingTaskDTO;
import org.apache.nifi.web.api.dto.ResourceDTO;
import org.apache.nifi.web.api.dto.SnippetDTO;
import org.apache.nifi.web.api.dto.SystemDiagnosticsDTO;
import org.apache.nifi.web.api.dto.TemplateDTO;
import org.apache.nifi.web.api.dto.UserDTO;
import org.apache.nifi.web.api.dto.UserGroupDTO;
import org.apache.nifi.web.api.dto.action.HistoryDTO;
import org.apache.nifi.web.api.dto.action.HistoryQueryDTO;
import org.apache.nifi.web.api.dto.provenance.ProvenanceDTO;
import org.apache.nifi.web.api.dto.provenance.ProvenanceEventDTO;
import org.apache.nifi.web.api.dto.provenance.ProvenanceOptionsDTO;
import org.apache.nifi.web.api.dto.provenance.lineage.LineageDTO;
import org.apache.nifi.web.api.dto.search.SearchResultsDTO;
import org.apache.nifi.web.api.dto.status.ControllerStatusDTO;
import org.apache.nifi.web.api.entity.AccessPolicyEntity;
import org.apache.nifi.web.api.entity.ActionEntity;
import org.apache.nifi.web.api.entity.ConnectionEntity;
import org.apache.nifi.web.api.entity.ConnectionStatusEntity;
import org.apache.nifi.web.api.entity.ControllerBulletinsEntity;
import org.apache.nifi.web.api.entity.ControllerConfigurationEntity;
import org.apache.nifi.web.api.entity.ControllerServiceEntity;
import org.apache.nifi.web.api.entity.ControllerServiceReferencingComponentsEntity;
import org.apache.nifi.web.api.entity.CurrentUserEntity;
import org.apache.nifi.web.api.entity.FlowConfigurationEntity;
import org.apache.nifi.web.api.entity.FlowEntity;
import org.apache.nifi.web.api.entity.FunnelEntity;
import org.apache.nifi.web.api.entity.LabelEntity;
import org.apache.nifi.web.api.entity.PortEntity;
import org.apache.nifi.web.api.entity.PortStatusEntity;
import org.apache.nifi.web.api.entity.ProcessGroupEntity;
import org.apache.nifi.web.api.entity.ProcessGroupFlowEntity;
import org.apache.nifi.web.api.entity.ProcessGroupStatusEntity;
import org.apache.nifi.web.api.entity.ProcessorEntity;
import org.apache.nifi.web.api.entity.ProcessorStatusEntity;
import org.apache.nifi.web.api.entity.RemoteProcessGroupEntity;
import org.apache.nifi.web.api.entity.RemoteProcessGroupPortEntity;
import org.apache.nifi.web.api.entity.RemoteProcessGroupStatusEntity;
import org.apache.nifi.web.api.entity.ReportingTaskEntity;
import org.apache.nifi.web.api.entity.ScheduleComponentsEntity;
import org.apache.nifi.web.api.entity.SnippetEntity;
import org.apache.nifi.web.api.entity.StatusHistoryEntity;
import org.apache.nifi.web.api.entity.TemplateEntity;
import org.apache.nifi.web.api.entity.UserEntity;
import org.apache.nifi.web.api.entity.UserGroupEntity;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Defines the NiFiServiceFacade interface.
 */
public interface NiFiServiceFacade {

    // ----------------------------------------
    // Synchronization methods
    // ----------------------------------------

    /**
     * Authorizes access to the service facade.
     *
     * @param authorizeAccess authorize access callback
     */
    void authorizeAccess(AuthorizeAccess authorizeAccess);

    /**
     * Claims the specified revision for the specified user.
     *
     * @param revision revision
     * @param user user
     * @throws InvalidRevisionException invalid revision
     */
    void verifyRevision(Revision revision, NiFiUser user) throws InvalidRevisionException;

    /**
     * Claims the specified revisions for the specified user.
     *
     * @param revisions revisions
     * @param user user
     * @throws InvalidRevisionException invalid revision
     */
    void verifyRevisions(Set<Revision> revisions, NiFiUser user) throws InvalidRevisionException;

    /**
     * Gets the current revisions for the components based on the specified function.
     *
     * @param groupId group
     * @param getComponents callback
     * @return component revisions
     */
    Set<Revision> getRevisionsFromGroup(String groupId, Function<ProcessGroup, Set<String>> getComponents);

    /**
     * Gets the revisions from the specified snippet.
     *
     * @param snippetId snippet
     * @return component revisions from the snippet
     */
    Set<Revision> getRevisionsFromSnippet(String snippetId);

    // ----------------------------------------
    // Controller methods
    // ----------------------------------------
    ControllerDTO getSiteToSiteDetails();

    /**
     * Searches the controller for the specified query string.
     *
     * @param query query
     * @return results
     */
    SearchResultsDTO searchController(String query);

    /**
     * Submits a provenance request.
     *
     * @param query query
     * @return results
     */
    ProvenanceDTO submitProvenance(ProvenanceDTO query);

    /**
     * Submits a new replay request.
     *
     * @param eventId id
     * @return event
     */
    ProvenanceEventDTO submitReplay(Long eventId);

    /**
     * Gets the content for the specified claim.
     *
     * @param eventId id
     * @param uri uri
     * @param contentDirection direction
     * @return content
     */
    DownloadableContent getContent(Long eventId, String uri, ContentDirection contentDirection);

    /**
     * Gets the content for the specified flowfile in the specified connection.
     *
     * @param connectionId connection
     * @param flowfileUuid flowfile
     * @param uri uri
     * @return content
     */
    DownloadableContent getContent(String connectionId, String flowfileUuid, String uri);

    /**
     * Retrieves provenance.
     *
     * @param queryId identifier
     * @return result
     */
    ProvenanceDTO getProvenance(String queryId);

    /**
     * Deletes provenance.
     *
     * @param queryId identifier
     */
    void deleteProvenance(String queryId);

    /**
     * Returns the available options for searching provenance.
     *
     * @return options
     */
    ProvenanceOptionsDTO getProvenanceSearchOptions();

    /**
     * Submits a lineage request.
     *
     * @param lineage lineage
     * @return lineage
     */
    LineageDTO submitLineage(LineageDTO lineage);

    /**
     * Gets the lineage with the specified id.
     *
     * @param lineageId id
     * @return lineage
     */
    LineageDTO getLineage(String lineageId);

    /**
     * Deletes the lineage with the specified id.
     *
     * @param lineageId lineage
     */
    void deleteLineage(String lineageId);

    /**
     * Gets the provenance event with the specified id.
     *
     * @param id id
     * @return event
     */
    ProvenanceEventDTO getProvenanceEvent(Long id);

    /**
     * Gets the configuration for this controller.
     *
     * @return Controller configuration transfer object
     */
    ControllerConfigurationEntity getControllerConfiguration();

    /**
     * Gets the controller level bulletins.
     *
     * @return Controller level bulletins
     */
    ControllerBulletinsEntity getControllerBulletins();

    /**
     * Gets the configuration for the flow.
     *
     * @return Flow configuration transfer object
     */
    FlowConfigurationEntity getFlowConfiguration();

    /**
     * Updates the configuration for this controller.
     *
     * @param revision Revision to compare with current base revision
     * @param controllerConfigurationDTO Controller configuration DTO
     * @return Controller configuration DTO
     */
    ControllerConfigurationEntity updateControllerConfiguration(Revision revision, ControllerConfigurationDTO controllerConfigurationDTO);

    /**
     * Returns the process group status.
     *
     * @param groupId group
     * @return The process group status
     */
    ProcessGroupStatusEntity getProcessGroupStatus(String groupId, boolean recursive);

    /**
     * Gets the process group status history.
     *
     * @param groupId id
     * @return history
     */
    StatusHistoryEntity getProcessGroupStatusHistory(String groupId);

    /**
     * Returns the controller status.
     *
     * @return status
     */
    ControllerStatusDTO getControllerStatus();

    /**
     * Updates the specified counter by setting its value to 0.
     *
     * @param counterId id
     * @return counter
     */
    CounterDTO updateCounter(String counterId);

    /**
     * Returns the counters.
     *
     * @return The counters
     */
    CountersDTO getCounters();

    /**
     * Returns the list of processor types.
     *
     * @return The list of available processor types
     */
    Set<DocumentedTypeDTO> getProcessorTypes();

    /**
     * Returns the list of controller service types.
     *
     * @param serviceType Filters only service types that implement this type
     * @return The list of available controller types
     */
    Set<DocumentedTypeDTO> getControllerServiceTypes(String serviceType);

    /**
     * Returns the list of reporting task types.
     *
     * @return The list of available reporting task types
     */
    Set<DocumentedTypeDTO> getReportingTaskTypes();

    /**
     * Returns the list of prioritizer types.
     *
     * @return The list of available prioritizer types
     */
    Set<DocumentedTypeDTO> getWorkQueuePrioritizerTypes();

    // ----------------------------------------
    // Template methods
    // ----------------------------------------

    /**
     * Verifies a template with the specified name can be created.
     *
     * @param groupId the id of the group for the template
     * @param name name of purposed template
     */
    void verifyCanAddTemplate(String groupId, String name);

    /**
     * Creates a new Template based off the specified snippet.
     *
     * @param name name
     * @param description description
     * @param snippetId id
     * @param groupId id of the process group
     * @param idGenerationSeed the seed to use for generating a UUID
     * @return template
     */
    TemplateDTO createTemplate(String name, String description, String snippetId, String groupId, Optional<String> idGenerationSeed);

    /**
     * Imports the specified Template.
     *
     * @param templateDTO The template dto
     * @param groupId id of the process group
     * @param idGenerationSeed the seed to use for generating a UUID
     *
     * @return The new template dto
     */
    TemplateDTO importTemplate(TemplateDTO templateDTO, String groupId, Optional<String> idGenerationSeed);

    /**
     * Instantiate the corresponding template.
     *
     * @param groupId group id
     * @param templateId template id
     * @param originX x
     * @param originY y
     * @param idGenerationSeed the ID to use for generating UUID's. May be null.
     * @return snapshot
     */
    FlowEntity createTemplateInstance(String groupId, Double originX, Double originY, String templateId, String idGenerationSeed);

    /**
     * Gets the template with the specified id.
     *
     * @param id id
     * @return template
     */
    TemplateDTO getTemplate(String id);

    /**
     * Gets the template, includes contents, with the specified id.
     *
     * @param id id
     * @return template
     */
    TemplateDTO exportTemplate(String id);

    /**
     * Gets all templates.
     *
     * @return templates
     */
    Set<TemplateEntity> getTemplates();

    /**
     * Deletes the specified template.
     *
     * @param id The id of the template
     */
    void deleteTemplate(String id);

    // ----------------------------------------
    // Processor methods
    // ----------------------------------------
    /**
     * Creates a new Processor.
     *
     * @param revision revision
     * @param groupId Group id
     * @param processorDTO The processor DTO
     * @return The new processor DTO
     */
    ProcessorEntity createProcessor(Revision revision, String groupId, ProcessorDTO processorDTO);

    /**
     * Gets the Processor transfer object for the specified id.
     *
     * @param id Id of the processor to return
     * @return The Processor transfer object
     */
    ProcessorEntity getProcessor(String id);

    /**
     * Gets the processor status.
     *
     * @param id id
     * @return status
     */
    ProcessorStatusEntity getProcessorStatus(String id);

    /**
     * Gets the processor status history.
     *
     * @param id id
     * @return history
     */
    StatusHistoryEntity getProcessorStatusHistory(String id);

    /**
     * Get the descriptor for the specified property of the specified processor.
     *
     * @param id id
     * @param property property
     * @return descriptor
     */
    PropertyDescriptorDTO getProcessorPropertyDescriptor(String id, String property);

    /**
     * Gets all the Processor transfer objects for this controller.
     *
     * @param groupId group
     * @return List of all the Processor transfer object
     */
    Set<ProcessorEntity> getProcessors(String groupId);

    /**
     * Verifies the specified processor can be updated.
     *
     * @param processorDTO processor
     */
    void verifyUpdateProcessor(ProcessorDTO processorDTO);

    /**
     * Updates the specified Processor.
     *
     * @param revision Revision to compare with current base revision
     * @param processorDTO The processorDTO
     * @return The updated processor
     */
    ProcessorEntity updateProcessor(Revision revision, ProcessorDTO processorDTO);

    /**
     * Verifies the specified processor can be removed.
     *
     * @param processorId processor
     */
    void verifyDeleteProcessor(String processorId);

    /**
     * Deletes the specified processor.
     *
     * @param revision Revision to compare with current base revision
     * @param processorId The processor id to delete
     * @return snapshot
     */
    ProcessorEntity deleteProcessor(Revision revision, String processorId);

    // ----------------------------------------
    // Connections methods
    // ----------------------------------------

    /**
     * Gets the Connection transfer objects for the specified source processor.
     *
     * @param groupId group
     * @return The Connection transfer objects
     */
    Set<ConnectionEntity> getConnections(String groupId);

    /**
     * Gets the specified Connection transfer object.
     *
     * @param connectionId The ID of the connection
     * @return The Connection transfer object
     */
    ConnectionEntity getConnection(String connectionId);

    /**
     * Gets the status of the specified connection.
     *
     * @param connectionId connection
     * @return status
     */
    ConnectionStatusEntity getConnectionStatus(String connectionId);

    /**
     * Gets the status history of the specified connection.
     *
     * @param connectionId connection
     * @return history
     */
    StatusHistoryEntity getConnectionStatusHistory(String connectionId);

    /**
     * Creates a new Relationship target.
     *
     * @param revision revision
     * @param groupId group
     * @param connectionDTO The Connection DTO
     * @return The Connection DTO
     */
    ConnectionEntity createConnection(Revision revision, String groupId, ConnectionDTO connectionDTO);

    /**
     * Determines if this connection can be listed.
     *
     * @param connectionId connection
     */
    void verifyListQueue(String connectionId);

    /**
     * Determines if this connection can be created.
     *
     * @param groupId group
     * @param connectionDTO connection
     */
    void verifyCreateConnection(String groupId, ConnectionDTO connectionDTO);

    /**
     * Determines if this connection can be updated.
     *
     * @param connectionDTO connection
     */
    void verifyUpdateConnection(ConnectionDTO connectionDTO);

    /**
     * Updates the specified Relationship target.
     *
     * @param revision Revision to compare with current base revision
     * @param connectionDTO The Connection DTO
     * @return The Connection DTO
     */
    ConnectionEntity updateConnection(Revision revision, ConnectionDTO connectionDTO);

    /**
     * Determines if this connection can be removed.
     *
     * @param connectionId connection
     */
    void verifyDeleteConnection(String connectionId);

    /**
     * Deletes the specified relationship target.
     *
     * @param revision Revision to compare with current base revision
     * @param connectionId The ID of the connection
     * @return snapshot
     */
    // TODO: Remove the id's from all of the delete methods. The Revision now contains
    // the component ID. We can't do this yet though because we have to wait for the
    // Revisions to be properly formed on the client/UI side
    ConnectionEntity deleteConnection(Revision revision, String connectionId);

    /**
     * Creates a new flow file drop request.
     *
     * @param connectionId The ID of the connection
     * @param dropRequestId The ID of the drop request
     * @return The DropRequest
     */
    DropRequestDTO createFlowFileDropRequest(String connectionId, String dropRequestId);

    /**
     * Gets the specified flow file drop request.
     *
     * @param connectionId The ID of the connection
     * @param dropRequestId The flow file drop request
     * @return The DropRequest
     */
    DropRequestDTO getFlowFileDropRequest(String connectionId, String dropRequestId);

    /**
     * Cancels/removes the specified flow file drop request.
     *
     * @param connectionId The ID of the connection
     * @param dropRequestId The flow file drop request
     * @return The DropRequest
     */
    DropRequestDTO deleteFlowFileDropRequest(String connectionId, String dropRequestId);

    /**
     * Creates a new flow file listing request.
     *
     * @param connectionId The ID of the connection
     * @param listingRequestId The ID of the listing request
     * @return The ListingRequest
     */
    ListingRequestDTO createFlowFileListingRequest(String connectionId, String listingRequestId);

    /**
     * Gets a new flow file listing request.
     *
     * @param connectionId The ID of the connection
     * @param listingRequestId The ID of the listing request
     * @return The ListingRequest
     */
    ListingRequestDTO getFlowFileListingRequest(String connectionId, String listingRequestId);

    /**
     * Deletes a new flow file listing request.
     *
     * @param connectionId The ID of the connection
     * @param listingRequestId The ID of the listing request
     * @return The ListingRequest
     */
    ListingRequestDTO deleteFlowFileListingRequest(String connectionId, String listingRequestId);

    /**
     * Gets the specified flowfile from the specified connection.
     *
     * @param connectionId The ID of the connection
     * @param flowFileUuid The UUID of the flowfile
     * @return The FlowFileDTO
     */
    FlowFileDTO getFlowFile(String connectionId, String flowFileUuid);

    // ----------------------------------------
    // InputPort methods
    // ----------------------------------------
    /**
     * Creates a new input port.
     *
     * @param revision revision
     * @param groupId The id of the group this port should be create in
     * @param inputPortDTO The input PortDTO
     * @return snapshot
     */
    PortEntity createInputPort(Revision revision, String groupId, PortDTO inputPortDTO);

    /**
     * Gets an input port.
     *
     * @param inputPortId The input port id
     * @return port
     */
    PortEntity getInputPort(String inputPortId);

    /**
     * Gets all input ports in a given group.
     *
     * @param groupId The id of the group
     * @return port
     */
    Set<PortEntity> getInputPorts(String groupId);

    /**
     * Gets the input port status.
     *
     * @param inputPortId input port
     * @return status
     */
    PortStatusEntity getInputPortStatus(String inputPortId);

    /**
     * Determines if the input port could be updated.
     *
     * @param inputPortDTO The id of the input port
     */
    void verifyUpdateInputPort(PortDTO inputPortDTO);

    /**
     * Updates the specified input port.
     *
     * @param revision Revision to compare with current base revision
     * @param inputPortDTO The input PortDTO
     * @return snapshot
     */
    PortEntity updateInputPort(Revision revision, PortDTO inputPortDTO);

    /**
     * Determines if the input port could be deleted.
     *
     * @param inputPortId The id of the input port
     */
    void verifyDeleteInputPort(String inputPortId);

    /**
     * Deletes the specified input port.
     *
     * @param revision Revision to compare with current base revision
     * @param inputPortId The id of the input port
     * @return snapshot
     */
    PortEntity deleteInputPort(Revision revision, String inputPortId);

    // ----------------------------------------
    // OutputPort methods
    // ----------------------------------------
    /**
     * Creates a new output port.
     *
     * @param revision revision
     * @param groupId The id of the group this port should be create in
     * @param outputPortDTO The output PortDTO
     * @return snapshot
     */
    PortEntity createOutputPort(Revision revision, String groupId, PortDTO outputPortDTO);

    /**
     * Gets an output port.
     *
     * @param outputPortId The output port id
     * @return port
     */
    PortEntity getOutputPort(String outputPortId);

    /**
     * Gets all output ports in a given group.
     *
     * @param groupId The id of the group
     * @return ports
     */
    Set<PortEntity> getOutputPorts(String groupId);

    /**
     * Gets the output port status.
     *
     * @param outputPortId output port
     * @return status
     */
    PortStatusEntity getOutputPortStatus(String outputPortId);

    /**
     * Determines if the output port could be updated.
     *
     * @param outputPortDTO The id of the output port
     */
    void verifyUpdateOutputPort(PortDTO outputPortDTO);

    /**
     * Updates the specified output port.
     *
     * @param revision Revision to compare with current base revision
     * @param outputPortDTO The output PortDTO
     * @return snapshot
     */
    PortEntity updateOutputPort(Revision revision, PortDTO outputPortDTO);

    /**
     * Determines if the output port could be deleted.
     *
     * @param outputPortId The id of the output port
     */
    void verifyDeleteOutputPort(String outputPortId);

    /**
     * Determines if the output port could be deleted.
     *
     * @param revision revision
     * @param outputPortId The id of the output port
     * @return snapshot
     */
    PortEntity deleteOutputPort(Revision revision, String outputPortId);

    // ------------
    // Current user
    // ------------

    CurrentUserEntity getCurrentUser();

    // ----------------------------------------
    // Flow methods
    // ----------------------------------------

    /**
     * Returns the flow.
     *
     * @param groupId group
     * @return the flow
     */
    ProcessGroupFlowEntity getProcessGroupFlow(String groupId);

    // ----------------------------------------
    // ProcessGroup methods
    // ----------------------------------------
    /**
     * Creates a new process group.
     *
     * @param revision revision
     * @param parentGroupId The id of the parent group
     * @param processGroupDTO The ProcessGroupDTO
     * @return snapshot
     */
    ProcessGroupEntity createProcessGroup(Revision revision, String parentGroupId, ProcessGroupDTO processGroupDTO);

    /**
     * Returns the process group.
     *
     * @param groupId group
     * @return ProcessGroup transfer object
     */
    ProcessGroupEntity getProcessGroup(String groupId);

    /**
     * Gets all process groups in the specified parent group.
     *
     * @param parentGroupId The id of the parent group
     * @return process group
     */
    Set<ProcessGroupEntity> getProcessGroups(String parentGroupId);

    /**
     * Verifies the contents of the specified process group can be scheduled or unscheduled.
     *
     * @param processGroupId The ProcessGroup id
     * @param componentIds the components
     * @param state scheduled state
     */
    void verifyScheduleComponents(String processGroupId, ScheduledState state, Set<String> componentIds);

    /**
     * Schedules all applicable components under the specified ProcessGroup.
     *
     * @param processGroupId The ProcessGroup id
     * @param state schedule state
     * @param componentRevisions components and their revision
     * @return snapshot
     */
    ScheduleComponentsEntity scheduleComponents(String processGroupId, ScheduledState state, Map<String, Revision> componentRevisions);

    /**
     * Updates the specified process group.
     *
     * @param revision Revision to compare with current base revision
     * @param processGroupDTO The ProcessGroupDTO
     * @return snapshot
     */
    ProcessGroupEntity updateProcessGroup(Revision revision, ProcessGroupDTO processGroupDTO);

    /**
     * Verifies the specified process group can be removed.
     *
     * @param groupId The id of the process group
     */
    void verifyDeleteProcessGroup(String groupId);

    /**
     * Deletes the specified process group.
     *
     * @param revision Revision to compare with current base revision
     * @param groupId The id of the process group
     * @return snapshot
     */
    ProcessGroupEntity deleteProcessGroup(Revision revision, String groupId);

    // ----------------------------------------
    // RemoteProcessGroup methods
    // ----------------------------------------
    /**
     * Creates a new remote process group.
     *
     * @param revision revision
     * @param groupId The id of the parent group
     * @param remoteProcessGroupDTO The RemoteProcessGroupDTO
     * @return snapshot
     */
    RemoteProcessGroupEntity createRemoteProcessGroup(Revision revision, String groupId, RemoteProcessGroupDTO remoteProcessGroupDTO);

    /**
     * Gets a remote process group.
     *
     * @param remoteProcessGroupId The id of the remote process group
     * @return group
     */
    RemoteProcessGroupEntity getRemoteProcessGroup(String remoteProcessGroupId);

    /**
     * Gets all remote process groups in the a given parent group.
     *
     * @param groupId The id of the parent group
     * @return group
     */
    Set<RemoteProcessGroupEntity> getRemoteProcessGroups(String groupId);

    /**
     * Gets the remote process group status.
     *
     * @param id remote process group
     * @return status
     */
    RemoteProcessGroupStatusEntity getRemoteProcessGroupStatus(String id);

    /**
     * Gets the remote process group status history.
     *
     * @param id The id of the remote process group
     * @return history
     */
    StatusHistoryEntity getRemoteProcessGroupStatusHistory(String id);

    /**
     * Verifies the specified remote process group can be updated.
     *
     * @param remoteProcessGroupDTO The RemoteProcessGroupDTO
     */
    void verifyUpdateRemoteProcessGroup(RemoteProcessGroupDTO remoteProcessGroupDTO);

    /**
     * Verifies the specified remote process group can update the specified remote input port.
     *
     * @param remoteProcessGroupId The id of the remote process group
     * @param remoteProcessGroupPortDTO The RemoteProcessGroupPortDTO
     */
    void verifyUpdateRemoteProcessGroupInputPort(String remoteProcessGroupId, RemoteProcessGroupPortDTO remoteProcessGroupPortDTO);

    /**
     * Verifies the specified remote process group can update the specified remote output port.
     *
     * @param remoteProcessGroupId The id of the remote process group
     * @param remoteProcessGroupPortDTO The RemoteProcessGroupPortDTO
     */
    void verifyUpdateRemoteProcessGroupOutputPort(String remoteProcessGroupId, RemoteProcessGroupPortDTO remoteProcessGroupPortDTO);

    /**
     * Updates the specified remote process group.
     *
     * @param revision Revision to compare with current base revision
     * @param remoteProcessGroupDTO The RemoteProcessGroupDTO
     * @return snapshot
     */
    RemoteProcessGroupEntity updateRemoteProcessGroup(Revision revision, RemoteProcessGroupDTO remoteProcessGroupDTO);

    /**
     * Updates the specified remote process groups input port.
     *
     * @param revision Revision to compare with current base revision
     * @param remoteProcessGroupId The id of the remote process group
     * @param remoteProcessGroupPortDTO The RemoteProcessGroupPortDTO
     * @return snapshot
     */
    RemoteProcessGroupPortEntity updateRemoteProcessGroupInputPort(Revision revision, String remoteProcessGroupId, RemoteProcessGroupPortDTO remoteProcessGroupPortDTO);

    /**
     * Updates the specified remote process groups output port.
     *
     * @param revision Revision to compare with current base revision
     * @param remoteProcessGroupId The id of the remote process group
     * @param remoteProcessGroupPortDTO The RemoteProcessGroupPortDTO
     * @return snapshot
     */
    RemoteProcessGroupPortEntity updateRemoteProcessGroupOutputPort(Revision revision, String remoteProcessGroupId, RemoteProcessGroupPortDTO remoteProcessGroupPortDTO);

    /**
     * Verifies the remote process group can be deleted.
     *
     * @param remoteProcessGroupId The id of the remote process group
     */
    void verifyDeleteRemoteProcessGroup(String remoteProcessGroupId);

    /**
     * Deletes the specified remote process group.
     *
     * @param revision Revision to compare with current base revision
     * @param remoteProcessGroupId The id of the remote process group
     * @return snapshot
     */
    RemoteProcessGroupEntity deleteRemoteProcessGroup(Revision revision, String remoteProcessGroupId);

    // ----------------------------------------
    // Funnel methods
    // ----------------------------------------
    /**
     * Creates a funnel.
     *
     * @param revision revision
     * @param groupId group
     * @param funnelDTO funnel
     * @return The funnel DTO
     */
    FunnelEntity createFunnel(Revision revision, String groupId, FunnelDTO funnelDTO);

    /**
     * Gets the specified funnel.
     *
     * @param funnelId The funnel id
     * @return The funnel transfer object
     */
    FunnelEntity getFunnel(String funnelId);

    /**
     * Gets all of the funnels.
     *
     * @param groupId group
     * @return The funnel transfer objects
     */
    Set<FunnelEntity> getFunnels(String groupId);

    /**
     * Updates the specified funnel.
     *
     * @param revision Revision to compare with current base revision
     * @param funnelDTO The funnel DTO
     * @return The funnel DTO
     */
    FunnelEntity updateFunnel(Revision revision, FunnelDTO funnelDTO);

    /**
     * Verifies the specified funnel can be deleted.
     *
     * @param funnelId funnel
     */
    void verifyDeleteFunnel(String funnelId);

    /**
     * Deletes the specified funnel.
     *
     * @param revision Revision to compare with current base revision
     * @param funnelId The funnel id
     * @return snapshot
     */
    FunnelEntity deleteFunnel(Revision revision, String funnelId);

    // ----------------------------------------
    // Component state methods
    // ----------------------------------------

    /**
     * Gets the state for the specified processor.
     *
     * @param processorId the processor id
     * @return  the component state
     */
    ComponentStateDTO getProcessorState(String processorId);

    /**
     * Verifies the processor state could be cleared.
     *
     * @param processorId the processor id
     */
    void verifyCanClearProcessorState(String processorId);

    /**
     * Clears the state for the specified processor.
     *
     * @param processorId the processor id
     */
    void clearProcessorState(String processorId);

    /**
     * Gets the state for the specified controller service.
     *
     * @param controllerServiceId the controller service id
     * @return  the component state
     */
    ComponentStateDTO getControllerServiceState(String controllerServiceId);

    /**
     * Verifies the controller service state could be cleared.
     *
     * @param controllerServiceId the controller service id
     */
    void verifyCanClearControllerServiceState(String controllerServiceId);

    /**
     * Clears the state for the specified controller service.
     *
     * @param controllerServiceId the controller service id
     */
    void clearControllerServiceState(String controllerServiceId);

    /**
     * Gets the state for the specified reporting task.
     *
     * @param reportingTaskId the reporting task id
     * @return  the component state
     */
    ComponentStateDTO getReportingTaskState(String reportingTaskId);

    /**
     * Verifies the reporting task state could be cleared.
     *
     * @param reportingTaskId the reporting task id
     */
    void verifyCanClearReportingTaskState(String reportingTaskId);

    /**
     * Clears the state for the specified reporting task.
     *
     * @param reportingTaskId the reporting task id
     */
    void clearReportingTaskState(String reportingTaskId);

    // ----------------------------------------
    // Label methods
    // ----------------------------------------
    /**
     * Creates a label.
     *
     * @param revision revision
     * @param groupId group
     * @param labelDTO The label DTO
     * @return The label DTO
     */
    LabelEntity createLabel(Revision revision, String groupId, LabelDTO labelDTO);

    /**
     * Gets the specified label.
     *
     * @param labelId The label id
     * @return The label transfer object
     */
    LabelEntity getLabel(String labelId);

    /**
     * Gets all of the labels.
     *
     * @param groupId group
     * @return The label transfer objects
     */
    Set<LabelEntity> getLabels(String groupId);

    /**
     * Updates the specified label.
     *
     * @param revision Revision to compare with current base revision
     * @param labelDTO The label DTO
     * @return The label DTO
     */
    LabelEntity updateLabel(Revision revision, LabelDTO labelDTO);

    /**
     * Deletes the specified label.
     *
     * @param revision Revision to compare with current base revision
     * @param labelId The label id
     * @return snapshot
     */
    LabelEntity deleteLabel(Revision revision, String labelId);

    // ----------------------------------------
    // User methods
    // ----------------------------------------
    /**
     * Creates a user.
     * @param revision The starting revision
     * @param userDTO The user DTO
     * @return The user transfer object
     */
    UserEntity createUser(Revision revision, UserDTO userDTO);

    /**
     * Gets the user with the specified ID.
     * @param userId The user ID
     * @return The user transfer object
     */
    UserEntity getUser(String userId);

    /**
     * Gets all the users.
     * @return The user transfer objects
     */
    Set<UserEntity> getUsers();

    /**
     * Updates the specified user.
     * @param revision Revision to compare with current base revision
     * @param userDTO The user DTO
     * @return The user transfer object
     */
    UserEntity updateUser(Revision revision, UserDTO userDTO);

    /**
     * Deletes the specified user.
     * @param revision Revision to compare with current base revision
     * @param userId The user ID
     * @return The user transfer object of the deleted user
     */
    UserEntity deleteUser(Revision revision, String userId);

    // ----------------------------------------
    // Group methods
    // ----------------------------------------
    /**
     * Creates a user group.
     * @param revision The starting revision
     * @param userGroupDTO The user group DTO
     * @return The user group transfer object
     */
    UserGroupEntity createUserGroup(Revision revision, UserGroupDTO userGroupDTO);

    /**
     * Gets the user group with the specified ID.
     * @param userGroupId The user group ID
     * @return The user group transfer object
     */
    UserGroupEntity getUserGroup(String userGroupId);

    /**
     * Gets all user groups.
     * @return The user group transfer objects
     */
    Set<UserGroupEntity> getUserGroups();

    /**
     * Updates the specified user group.
     * @param revision Revision to compare with current base revision
     * @param userGroupDTO The user group DTO
     * @return The user group transfer object
     */
    UserGroupEntity updateUserGroup(Revision revision, UserGroupDTO userGroupDTO);

    /**
     * Deletes the specified user group.
     * @param revision Revision to compare with current base revision
     * @param userGroupId The user group ID
     * @return The user group transfer object of the deleted user group
     */
    UserGroupEntity deleteUserGroup(Revision revision, String userGroupId);

    // ----------------------------------------
    // AccessPolicy methods
    // ----------------------------------------
    /**
     * Creates an access policy.
     * @param revision The starting revision
     * @param accessPolicyDTO The access policy DTO
     * @return The access policy transfer object
     */
    AccessPolicyEntity createAccessPolicy(Revision revision, AccessPolicyDTO accessPolicyDTO);

    /**
     * Gets the access policy with the specified ID.
     * @param accessPolicyId access policy ID
     * @return The access policy transfer object
     */
    AccessPolicyEntity getAccessPolicy(String accessPolicyId);

    /**
     * Gets the access policy for the specified action, resource type, and component id.
     *
     * @param resource resource
     * @return access policy
     */
    AccessPolicyEntity getAccessPolicy(RequestAction requestAction, String resource);

    /**
     * Updates the specified access policy.
     * @param revision Revision to compare with current base revision
     * @param accessPolicyDTO The access policy DTO
     * @return The access policy transfer object
     */
    AccessPolicyEntity updateAccessPolicy(Revision revision, AccessPolicyDTO accessPolicyDTO);

    /**
     * Deletes the specified access policy.
     * @param revision Revision to compare with current base revision
     * @param accessPolicyId The access policy ID
     * @return The access policy transfer object of the deleted access policy
     */
    AccessPolicyEntity deleteAccessPolicy(Revision revision, String accessPolicyId);

    // ----------------------------------------
    // Controller Services methods
    // ----------------------------------------
    /**
     * Creates a controller service.
     *
     * @param revision revision
     * @param groupId the ID of the Process Group to add the Controller Service to
     * @param controllerServiceDTO The controller service DTO
     * @return The controller service DTO
     */
    ControllerServiceEntity createControllerService(Revision revision, String groupId, ControllerServiceDTO controllerServiceDTO);

    /**
     * Gets all controller services that belong to the given group and its parent/ancestor groups
     *
     * @param groupId the id of the process group of interest
     * @return services
     */
    Set<ControllerServiceEntity> getControllerServices(String groupId);

    /**
     * Gets the specified controller service.
     *
     * @param controllerServiceId id
     * @return service
     */
    ControllerServiceEntity getControllerService(String controllerServiceId);

    /**
     * Get the descriptor for the specified property of the specified controller service.
     *
     * @param id id
     * @param property property
     * @return property
     */
    PropertyDescriptorDTO getControllerServicePropertyDescriptor(String id, String property);

    /**
     * Gets the references for specified controller service.
     *
     * @param controllerServiceId id
     * @return service reference
     */
    ControllerServiceReferencingComponentsEntity getControllerServiceReferencingComponents(String controllerServiceId);

    /**
     * Updates the referencing components for the specified controller service.
     *
     * @param referenceRevisions revisions
     * @param controllerServiceId id
     * @param scheduledState state
     * @param controllerServiceState the value of state
     * @return The referencing component dtos
     */
    ControllerServiceReferencingComponentsEntity updateControllerServiceReferencingComponents(
        Map<String, Revision> referenceRevisions, String controllerServiceId, ScheduledState scheduledState, ControllerServiceState controllerServiceState);

    /**
     * Updates the specified controller service.
     *
     * @param revision Revision to compare with current base revision
     * @param controllerServiceDTO The controller service DTO
     * @return The controller service DTO
     */
    ControllerServiceEntity updateControllerService(Revision revision, ControllerServiceDTO controllerServiceDTO);

    /**
     * Deletes the specified label.
     *
     * @param revision Revision to compare with current base revision
     * @param controllerServiceId The controller service id
     * @return snapshot
     */
    ControllerServiceEntity deleteControllerService(Revision revision, String controllerServiceId);

    /**
     * Verifies the specified controller service can be updated.
     *
     * @param controllerServiceDTO service
     */
    void verifyUpdateControllerService(ControllerServiceDTO controllerServiceDTO);

    /**
     * Verifies the referencing components of the specified controller service can be updated.
     *
     * @param controllerServiceId id
     * @param scheduledState schedule state
     * @param controllerServiceState service state
     */
    void verifyUpdateControllerServiceReferencingComponents(String controllerServiceId, ScheduledState scheduledState, ControllerServiceState controllerServiceState);

    /**
     * Verifies the specified controller service can be removed.
     *
     * @param controllerServiceId id
     */
    void verifyDeleteControllerService(String controllerServiceId);

    // ----------------------------------------
    // Reporting Task methods
    // ----------------------------------------
    /**
     * Creates a reporting task.
     *
     * @param revision revision
     * @param reportingTaskDTO The reporting task DTO
     * @return The reporting task DTO
     */
    ReportingTaskEntity createReportingTask(Revision revision, ReportingTaskDTO reportingTaskDTO);

    /**
     * Gets all reporting tasks.
     *
     * @return tasks
     */
    Set<ReportingTaskEntity> getReportingTasks();

    /**
     * Gets the specified reporting task.
     *
     * @param reportingTaskId id
     * @return task
     */
    ReportingTaskEntity getReportingTask(String reportingTaskId);

    /**
     * Get the descriptor for the specified property of the specified reporting task.
     *
     * @param id id
     * @param property property
     * @return descriptor
     */
    PropertyDescriptorDTO getReportingTaskPropertyDescriptor(String id, String property);

    /**
     * Updates the specified reporting task.
     *
     * @param revision Revision to compare with current base revision
     * @param reportingTaskDTO The reporting task DTO
     * @return The reporting task DTO
     */
    ReportingTaskEntity updateReportingTask(Revision revision, ReportingTaskDTO reportingTaskDTO);

    /**
     * Deletes the specified reporting task.
     *
     * @param revision Revision to compare with current base revision
     * @param reportingTaskId The reporting task id
     * @return snapshot
     */
    ReportingTaskEntity deleteReportingTask(Revision revision, String reportingTaskId);

    /**
     * Verifies the specified reporting task can be updated.
     *
     * @param reportingTaskDTO task
     */
    void verifyUpdateReportingTask(ReportingTaskDTO reportingTaskDTO);

    /**
     * Verifies the specified reporting task can be removed.
     *
     * @param reportingTaskId id
     */
    void verifyDeleteReportingTask(String reportingTaskId);

    // ----------------------------------------
    // History methods
    // ----------------------------------------
    /**
     * Returns actions that meet the specified criteria.
     *
     * @param historyQuery query
     * @return history
     */
    HistoryDTO getActions(HistoryQueryDTO historyQuery);

    /**
     * Returns the details for the specified action id.
     *
     * @param actionId id
     * @return action
     */
    ActionEntity getAction(Integer actionId);

    /**
     * Purges all actions up to the specified end date.
     *
     * @param endDate The end date
     */
    void deleteActions(Date endDate);

    /**
     * Gets the history for the specified property for the specified component.
     *
     * @param componentId id
     * @return history
     */
    ComponentHistoryDTO getComponentHistory(String componentId);

    // ----------------------------------------
    // Snippet methods
    // ----------------------------------------
    /**
     * Creates a new snippet based off the existing snippet.
     *
     * @param groupId group id
     * @param snippetId snippet id
     * @param originX x
     * @param originY y
     * @param idGenerationSeed the seed to use for generating UUID's. May be null.
     * @return snapshot
     */
    FlowEntity copySnippet(String groupId, String snippetId, Double originX, Double originY, String idGenerationSeed);

    /**
     * Creates a new snippet.
     *
     * @param snippet snippet
     * @return snapshot
     */
    SnippetEntity createSnippet(SnippetDTO snippet);

    /**
     * Determines if this snippet can be updated.
     *
     * @param snippetDto snippet
     */
    void verifyUpdateSnippet(SnippetDTO snippetDto, Set<String> affectedComponentIds);

    /**
     * If group id is specified, moves the specified snippet to the specified group.
     *
     * @param revisions revisions
     * @param snippetDto snippet
     * @return snapshot
     */
    SnippetEntity updateSnippet(Set<Revision> revisions, SnippetDTO snippetDto);

    /**
     * Determines if this snippet can be removed.
     *
     * @param id id
     * @param affectedComponentIds affected components
     */
    void verifyDeleteSnippet(String id, Set<String> affectedComponentIds);

    /**
     * Removes the specified snippet.
     *
     * @param revisions revisions
     * @param snippetId snippet
     * @return snapshot
     */
    SnippetEntity deleteSnippet(Set<Revision> revisions, String snippetId);

    // ----------------------------------------
    // Cluster methods
    // ----------------------------------------
    /**
     * @return true if controller is connected or trying to connect to the cluster
     */
    boolean isClustered();

    /**
     * @return the id of this node, if clustered. If not clustered, returns null
     */
    String getNodeId();

    /**
     * @return the contents of cluster
     */
    ClusterDTO getCluster();

    /**
     * Returns the contents of the node.
     *
     * @param nodeId a node identifier
     * @return the contents of the node
     */
    NodeDTO getNode(String nodeId);

    /**
     * Updates the contents of the node.
     *
     * @param nodeDTO a node transfer object
     * @return node
     */
    NodeDTO updateNode(NodeDTO nodeDTO);

    /**
     * Deletes the node.
     *
     * @param nodeId a node identifier
     */
    void deleteNode(String nodeId);

    // ----------------------------------------
    // BulletinBoard methods
    // ----------------------------------------
    /**
     * @param query query
     * @return the bulletin board for this NiFi
     */
    BulletinBoardDTO getBulletinBoard(BulletinQueryDTO query);

    // ----------------------------------------
    // System diagnostics methods
    // ----------------------------------------
    /**
     * @return the system diagnostics
     */
    SystemDiagnosticsDTO getSystemDiagnostics();

    // ----------------------------------------
    // Resources
    // ----------------------------------------
    /**
     * @return the resources
     */
    List<ResourceDTO> getResources();
}
