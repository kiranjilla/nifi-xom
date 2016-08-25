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
package org.apache.nifi.web.api;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import com.wordnik.swagger.annotations.Authorization;
import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.authorization.AuthorizeControllerServiceReference;
import org.apache.nifi.authorization.Authorizer;
import org.apache.nifi.authorization.ControllerServiceReferencingComponentAuthorizable;
import org.apache.nifi.authorization.RequestAction;
import org.apache.nifi.authorization.resource.Authorizable;
import org.apache.nifi.authorization.user.NiFiUser;
import org.apache.nifi.authorization.user.NiFiUserUtils;
import org.apache.nifi.ui.extension.UiExtension;
import org.apache.nifi.ui.extension.UiExtensionMapping;
import org.apache.nifi.web.NiFiServiceFacade;
import org.apache.nifi.web.Revision;
import org.apache.nifi.web.UiExtensionType;
import org.apache.nifi.web.api.dto.ComponentStateDTO;
import org.apache.nifi.web.api.dto.ProcessorConfigDTO;
import org.apache.nifi.web.api.dto.ProcessorDTO;
import org.apache.nifi.web.api.dto.PropertyDescriptorDTO;
import org.apache.nifi.web.api.entity.ComponentStateEntity;
import org.apache.nifi.web.api.entity.ProcessorEntity;
import org.apache.nifi.web.api.entity.PropertyDescriptorEntity;
import org.apache.nifi.web.api.request.ClientIdParameter;
import org.apache.nifi.web.api.request.LongParameter;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Set;

/**
 * RESTful endpoint for managing a Processor.
 */
@Path("/processors")
@Api(
        value = "/processors",
        description = "Endpoint for managing a Processor."
)
public class ProcessorResource extends ApplicationResource {
    private NiFiServiceFacade serviceFacade;
    private Authorizer authorizer;

    @Context
    private ServletContext servletContext;

    /**
     * Populate the uri's for the specified processors and their relationships.
     *
     * @param processorEntities processors
     * @return dtos
     */
    public Set<ProcessorEntity> populateRemainingProcessorEntitiesContent(Set<ProcessorEntity> processorEntities) {
        for (ProcessorEntity processorEntity : processorEntities) {
            populateRemainingProcessorEntityContent(processorEntity);
        }
        return processorEntities;
    }

    /**
     * Populate the uri's for the specified processors and their relationships.
     *
     * @param processorEntity processors
     * @return dtos
     */
    public ProcessorEntity populateRemainingProcessorEntityContent(ProcessorEntity processorEntity) {
        processorEntity.setUri(generateResourceUri("processors", processorEntity.getId()));

        // populate remaining content
        if (processorEntity.getComponent() != null) {
            populateRemainingProcessorContent(processorEntity.getComponent());
        }
        return processorEntity;
    }

    /**
     * Populate the uri's for the specified processor and its relationships.
     */
    public ProcessorDTO populateRemainingProcessorContent(ProcessorDTO processor) {
        // get the config details and see if there is a custom ui for this processor type
        ProcessorConfigDTO config = processor.getConfig();
        if (config != null) {
            // consider legacy custom ui fist
            String customUiUrl = servletContext.getInitParameter(processor.getType());
            if (StringUtils.isNotBlank(customUiUrl)) {
                config.setCustomUiUrl(customUiUrl);
            } else {
                // see if this processor has any ui extensions
                final UiExtensionMapping uiExtensionMapping = (UiExtensionMapping) servletContext.getAttribute("nifi-ui-extensions");
                if (uiExtensionMapping.hasUiExtension(processor.getType())) {
                    final List<UiExtension> uiExtensions = uiExtensionMapping.getUiExtension(processor.getType());
                    for (final UiExtension uiExtension : uiExtensions) {
                        if (UiExtensionType.ProcessorConfiguration.equals(uiExtension.getExtensionType())) {
                            config.setCustomUiUrl(uiExtension.getContextPath() + "/configure");
                        }
                    }
                }
            }
        }

        return processor;
    }

    /**
     * Retrieves the specified processor.
     *
     * @param id The id of the processor to retrieve.
     * @return A processorEntity.
     * @throws InterruptedException if interrupted
     */
    @GET
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    @ApiOperation(
            value = "Gets a processor",
            response = ProcessorEntity.class,
            authorizations = {
                    @Authorization(value = "Read - /processors/{uuid}", type = "")
            }
    )
    @ApiResponses(
            value = {
                    @ApiResponse(code = 400, message = "NiFi was unable to complete the request because it was invalid. The request should not be retried without modification."),
                    @ApiResponse(code = 401, message = "Client could not be authenticated."),
                    @ApiResponse(code = 403, message = "Client is not authorized to make this request."),
                    @ApiResponse(code = 404, message = "The specified resource could not be found."),
                    @ApiResponse(code = 409, message = "The request was valid but NiFi was not in the appropriate state to process it. Retrying the same request later may be successful.")
            }
    )
    public Response getProcessor(
            @ApiParam(
                    value = "The processor id.",
                    required = true
            )
            @PathParam("id") final String id) throws InterruptedException {

        if (isReplicateRequest()) {
            return replicate(HttpMethod.GET);
        }

        // authorize access
        serviceFacade.authorizeAccess(lookup -> {
            final Authorizable processor = lookup.getProcessor(id).getAuthorizable();
            processor.authorize(authorizer, RequestAction.READ, NiFiUserUtils.getNiFiUser());
        });

        // get the specified processor
        final ProcessorEntity entity = serviceFacade.getProcessor(id);
        populateRemainingProcessorEntityContent(entity);

        // generate the response
        return clusterContext(generateOkResponse(entity)).build();
    }

    /**
     * Returns the descriptor for the specified property.
     *
     * @param id           The id of the processor
     * @param propertyName The property
     * @return a propertyDescriptorEntity
     * @throws InterruptedException if interrupted
     */
    @GET
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}/descriptors")
    @ApiOperation(
            value = "Gets the descriptor for a processor property",
            response = PropertyDescriptorEntity.class,
            authorizations = {
                    @Authorization(value = "Read - /processors/{uuid}", type = "")
            }
    )
    @ApiResponses(
            value = {
                    @ApiResponse(code = 400, message = "NiFi was unable to complete the request because it was invalid. The request should not be retried without modification."),
                    @ApiResponse(code = 401, message = "Client could not be authenticated."),
                    @ApiResponse(code = 403, message = "Client is not authorized to make this request."),
                    @ApiResponse(code = 404, message = "The specified resource could not be found."),
                    @ApiResponse(code = 409, message = "The request was valid but NiFi was not in the appropriate state to process it. Retrying the same request later may be successful.")
            }
    )
    public Response getPropertyDescriptor(
            @ApiParam(
                    value = "If the client id is not specified, new one will be generated. This value (whether specified or generated) is included in the response.",
                    required = false
            )
            @QueryParam(CLIENT_ID) @DefaultValue(StringUtils.EMPTY) final ClientIdParameter clientId,
            @ApiParam(
                    value = "The processor id.",
                    required = true
            )
            @PathParam("id") final String id,
            @ApiParam(
                    value = "The property name.",
                    required = true
            )
            @QueryParam("propertyName") final String propertyName) throws InterruptedException {

        // ensure the property name is specified
        if (propertyName == null) {
            throw new IllegalArgumentException("The property name must be specified.");
        }

        if (isReplicateRequest()) {
            return replicate(HttpMethod.GET);
        }

        // authorize access
        serviceFacade.authorizeAccess(lookup -> {
            final Authorizable processor = lookup.getProcessor(id).getAuthorizable();
            processor.authorize(authorizer, RequestAction.READ, NiFiUserUtils.getNiFiUser());
        });

        // get the property descriptor
        final PropertyDescriptorDTO descriptor = serviceFacade.getProcessorPropertyDescriptor(id, propertyName);

        // generate the response entity
        final PropertyDescriptorEntity entity = new PropertyDescriptorEntity();
        entity.setPropertyDescriptor(descriptor);

        // generate the response
        return clusterContext(generateOkResponse(entity)).build();
    }

    /**
     * Gets the state for a processor.
     *
     * @param id The id of the processor
     * @return a componentStateEntity
     * @throws InterruptedException if interrupted
     */
    @GET
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}/state")
    @ApiOperation(
            value = "Gets the state for a processor",
            response = ComponentStateDTO.class,
            authorizations = {
                    @Authorization(value = "Write - /processors/{uuid}", type = "")
            }
    )
    @ApiResponses(
            value = {
                    @ApiResponse(code = 400, message = "NiFi was unable to complete the request because it was invalid. The request should not be retried without modification."),
                    @ApiResponse(code = 401, message = "Client could not be authenticated."),
                    @ApiResponse(code = 403, message = "Client is not authorized to make this request."),
                    @ApiResponse(code = 404, message = "The specified resource could not be found."),
                    @ApiResponse(code = 409, message = "The request was valid but NiFi was not in the appropriate state to process it. Retrying the same request later may be successful.")
            }
    )
    public Response getState(
            @ApiParam(
                    value = "The processor id.",
                    required = true
            )
            @PathParam("id") final String id) throws InterruptedException {

        if (isReplicateRequest()) {
            return replicate(HttpMethod.GET);
        }

        // authorize access
        serviceFacade.authorizeAccess(lookup -> {
            final Authorizable processor = lookup.getProcessor(id).getAuthorizable();
            processor.authorize(authorizer, RequestAction.WRITE, NiFiUserUtils.getNiFiUser());
        });

        // get the component state
        final ComponentStateDTO state = serviceFacade.getProcessorState(id);

        // generate the response entity
        final ComponentStateEntity entity = new ComponentStateEntity();
        entity.setComponentState(state);

        // generate the response
        return clusterContext(generateOkResponse(entity)).build();
    }

    /**
     * Clears the state for a processor.
     *
     * @param httpServletRequest servlet request
     * @param id                 The id of the processor
     * @return a componentStateEntity
     * @throws InterruptedException if interrupted
     */
    @POST
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id}/state/clear-requests")
    @ApiOperation(
            value = "Clears the state for a processor",
            response = ComponentStateDTO.class,
            authorizations = {
                    @Authorization(value = "Write - /processors/{uuid}", type = "")
            }
    )
    @ApiResponses(
            value = {
                    @ApiResponse(code = 400, message = "NiFi was unable to complete the request because it was invalid. The request should not be retried without modification."),
                    @ApiResponse(code = 401, message = "Client could not be authenticated."),
                    @ApiResponse(code = 403, message = "Client is not authorized to make this request."),
                    @ApiResponse(code = 404, message = "The specified resource could not be found."),
                    @ApiResponse(code = 409, message = "The request was valid but NiFi was not in the appropriate state to process it. Retrying the same request later may be successful.")
            }
    )
    public Response clearState(
            @Context final HttpServletRequest httpServletRequest,
            @ApiParam(
                    value = "The processor id.",
                    required = true
            )
            @PathParam("id") final String id) throws InterruptedException {

        if (isReplicateRequest()) {
            return replicate(HttpMethod.POST);
        }

        final ProcessorEntity requestProcessorEntity = new ProcessorEntity();
        requestProcessorEntity.setId(id);

        return withWriteLock(
                serviceFacade,
                requestProcessorEntity,
                lookup -> {
                    final Authorizable processor = lookup.getProcessor(id).getAuthorizable();
                    processor.authorize(authorizer, RequestAction.WRITE, NiFiUserUtils.getNiFiUser());
                },
                () -> serviceFacade.verifyCanClearProcessorState(id),
                (processorEntity) -> {
                    // get the component state
                    serviceFacade.clearProcessorState(processorEntity.getId());

                    // generate the response entity
                    final ComponentStateEntity entity = new ComponentStateEntity();

                    // generate the response
                    return clusterContext(generateOkResponse(entity)).build();
                }
        );
    }

    /**
     * Updates the specified processor with the specified values.
     *
     * @param httpServletRequest request
     * @param id                 The id of the processor to update.
     * @param requestProcessorEntity    A processorEntity.
     * @return A processorEntity.
     * @throws InterruptedException if interrupted
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    @ApiOperation(
            value = "Updates a processor",
            response = ProcessorEntity.class,
            authorizations = {
                    @Authorization(value = "Write - /processors/{uuid}", type = ""),
                    @Authorization(value = "Read - any referenced Controller Services - /controller-services/{uuid}", type = "")
            }
    )
    @ApiResponses(
            value = {
                    @ApiResponse(code = 400, message = "NiFi was unable to complete the request because it was invalid. The request should not be retried without modification."),
                    @ApiResponse(code = 401, message = "Client could not be authenticated."),
                    @ApiResponse(code = 403, message = "Client is not authorized to make this request."),
                    @ApiResponse(code = 404, message = "The specified resource could not be found."),
                    @ApiResponse(code = 409, message = "The request was valid but NiFi was not in the appropriate state to process it. Retrying the same request later may be successful.")
            }
    )
    public Response updateProcessor(
            @Context final HttpServletRequest httpServletRequest,
            @ApiParam(
                    value = "The processor id.",
                    required = true
            )
            @PathParam("id") final String id,
            @ApiParam(
                    value = "The processor configuration details.",
                    required = true
            ) final ProcessorEntity requestProcessorEntity) throws InterruptedException {

        if (requestProcessorEntity == null || requestProcessorEntity.getComponent() == null) {
            throw new IllegalArgumentException("Processor details must be specified.");
        }

        if (requestProcessorEntity.getRevision() == null) {
            throw new IllegalArgumentException("Revision must be specified.");
        }

        // ensure the same id is being used
        final ProcessorDTO requestProcessorDTO = requestProcessorEntity.getComponent();
        if (!id.equals(requestProcessorDTO.getId())) {
            throw new IllegalArgumentException(String.format("The processor id (%s) in the request body does "
                    + "not equal the processor id of the requested resource (%s).", requestProcessorDTO.getId(), id));
        }

        if (isReplicateRequest()) {
            return replicate(HttpMethod.PUT, requestProcessorEntity);
        }

        // handle expects request (usually from the cluster manager)
        final Revision requestRevision = getRevision(requestProcessorEntity, id);
        return withWriteLock(
                serviceFacade,
                requestProcessorEntity,
                requestRevision,
                lookup -> {
                    final NiFiUser user = NiFiUserUtils.getNiFiUser();

                    final ControllerServiceReferencingComponentAuthorizable authorizable = lookup.getProcessor(id);
                    authorizable.getAuthorizable().authorize(authorizer, RequestAction.WRITE, user);

                    final ProcessorConfigDTO config = requestProcessorDTO.getConfig();
                    if (config != null) {
                        AuthorizeControllerServiceReference.authorizeControllerServiceReferences(config.getProperties(), authorizable, authorizer, lookup);
                    }
                },
                () -> serviceFacade.verifyUpdateProcessor(requestProcessorDTO),
                (revision, processorEntity) -> {
                    final ProcessorDTO processorDTO = processorEntity.getComponent();

                    // update the processor
                    final ProcessorEntity entity = serviceFacade.updateProcessor(revision, processorDTO);
                    populateRemainingProcessorEntityContent(entity);

                    return clusterContext(generateOkResponse(entity)).build();
                }
        );
    }

    /**
     * Removes the specified processor.
     *
     * @param httpServletRequest request
     * @param version            The revision is used to verify the client is working with the latest version of the flow.
     * @param clientId           Optional client id. If the client id is not specified, a new one will be generated. This value (whether specified or generated) is included in the response.
     * @param id                 The id of the processor to remove.
     * @return A processorEntity.
     * @throws InterruptedException if interrupted
     */
    @DELETE
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    @ApiOperation(
            value = "Deletes a processor",
            response = ProcessorEntity.class,
            authorizations = {
                    @Authorization(value = "Write - /processors/{uuid}", type = "")
            }
    )
    @ApiResponses(
            value = {
                    @ApiResponse(code = 400, message = "NiFi was unable to complete the request because it was invalid. The request should not be retried without modification."),
                    @ApiResponse(code = 401, message = "Client could not be authenticated."),
                    @ApiResponse(code = 403, message = "Client is not authorized to make this request."),
                    @ApiResponse(code = 404, message = "The specified resource could not be found."),
                    @ApiResponse(code = 409, message = "The request was valid but NiFi was not in the appropriate state to process it. Retrying the same request later may be successful.")
            }
    )
    public Response deleteProcessor(
            @Context final HttpServletRequest httpServletRequest,
            @ApiParam(
                    value = "The revision is used to verify the client is working with the latest version of the flow.",
                    required = false
            )
            @QueryParam(VERSION) final LongParameter version,
            @ApiParam(
                    value = "If the client id is not specified, new one will be generated. This value (whether specified or generated) is included in the response.",
                    required = false
            )
            @QueryParam(CLIENT_ID) @DefaultValue(StringUtils.EMPTY) final ClientIdParameter clientId,
            @ApiParam(
                    value = "The processor id.",
                    required = true
            )
            @PathParam("id") final String id) throws InterruptedException {

        if (isReplicateRequest()) {
            return replicate(HttpMethod.DELETE);
        }

        final ProcessorEntity requestProcessorEntity = new ProcessorEntity();
        requestProcessorEntity.setId(id);

        final Revision requestRevision = new Revision(version == null ? null : version.getLong(), clientId.getClientId(), id);
        return withWriteLock(
                serviceFacade,
                requestProcessorEntity,
                requestRevision,
                lookup -> {
                    final Authorizable processor = lookup.getProcessor(id).getAuthorizable();
                    processor.authorize(authorizer, RequestAction.WRITE, NiFiUserUtils.getNiFiUser());
                },
                () -> serviceFacade.verifyDeleteProcessor(id),
                (revision, processorEntity) -> {
                    // delete the processor
                    final ProcessorEntity entity = serviceFacade.deleteProcessor(revision, processorEntity.getId());

                    // generate the response
                    return clusterContext(generateOkResponse(entity)).build();
                }
        );
    }

    // setters

    public void setServiceFacade(NiFiServiceFacade serviceFacade) {
        this.serviceFacade = serviceFacade;
    }

    public void setAuthorizer(Authorizer authorizer) {
        this.authorizer = authorizer;
    }
}
