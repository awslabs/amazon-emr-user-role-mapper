// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.api;

import com.amazon.aws.emr.ApplicationConfiguration;
import com.amazon.aws.emr.common.system.PrincipalResolver;
import com.amazon.aws.emr.credentials.MetadataCredentialsProvider;
import com.amazon.aws.emr.mapping.MappingInvoker;
import com.amazon.aws.emr.common.system.user.UserIdService;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.util.EC2MetadataUtils;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.process.internal.RequestScoped;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * REST controller that poses as IMDS to non privileged users.
 */
@Slf4j
@RequestScoped
@Path("/")
public class MetadataController {

    public static final String LATEST_IAM_CREDENTIALS_ROOT_PATH = "/latest/meta-data/iam/security-credentials/";
    public static final String LATEST_IAM_CREDENTIALS_WITH_IMPERSONATION = LATEST_IAM_CREDENTIALS_ROOT_PATH + "impersonation/";

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
            .setPrettyPrinting()
            .create();

    @Inject
    private MappingInvoker mappingInvoker;

    @Inject
    private PrincipalResolver principalResolver;

    @Inject
    private MetadataCredentialsProvider metadataCredentialsProvider;

    @Inject
    private UserIdService userIdService;

    @Inject
    ApplicationConfiguration applicationConfiguration;

    /**
     * Gets credentials for a role name.
     * Empty credentials are returned if the calling user has no mapping defined, or is unauthorized to assume the role.
     *
     * @param httpServletRequest the HTTP Request object
     * @param roleName           the role name to assume
     * @return credentials obtained by serializing {@link EC2MetadataUtils.IAMSecurityCredential}
     */
    @GET
    @Path("{apiVersion}/meta-data/iam/security-credentials/{roleName}")
    @Produces(MediaType.TEXT_PLAIN)
    public String getUserCredentials(@Context HttpServletRequest httpServletRequest, @PathParam("roleName") String roleName) {
        log.debug("Processing a request to get credentials for {}", roleName);
        Optional<AssumeRoleRequest> assumeRoleRequest = makeUserAssumeRoleRequest(httpServletRequest);
        return assumeRoleRequest
                .filter(request -> roleName.equals(getRoleNameFromArn(request.getRoleArn())))
                .map(request -> metadataCredentialsProvider.getUserCredentials(request))
                .map(credentials -> {
                    log.debug("Done with request {}", assumeRoleRequest);
                    return GSON.toJson(credentials.get());
                })
                .orElse(null);
    }

    @GET
    @Path("{apiVersion}/meta-data/iam/security-credentials/ps/")
    @Produces(MediaType.TEXT_PLAIN)
    public String getUserCredentialsCertBased(@Context HttpServletRequest httpServletRequest) {
        log.debug("Processing a request to get credentials based on mTLS certification");
        Optional<AssumeRoleRequest> assumeRoleRequest = makeUserAssumeRoleRequestCertBased(httpServletRequest);
        return assumeRoleRequest
                .map(request -> metadataCredentialsProvider.getUserCredentials(request))
                .map(credentials -> {
                    log.debug("Done with request {}", assumeRoleRequest);
                    return GSON.toJson(credentials.get());
                })
                .orElse(null);
    }

    /**
     * Get credentials for an impersonated user, if allowed
     * Empty credentials are returned if the calling user has no mapping defined, or is unauthorized to assume the role.
     *
     * @param httpServletRequest the HTTP Request object
     * @param username           the name of impersonated user
     * @return credentials obtained by serializing {@link EC2MetadataUtils.IAMSecurityCredential}
     */
    @GET
    @Path("{apiVersion}/meta-data/iam/security-credentials/impersonation/{username}")
    @Produces(MediaType.TEXT_PLAIN)
    public String getCredentialsForUser(@Context HttpServletRequest httpServletRequest, @PathParam("username") String username) {
        log.debug("Processing a impersonation request to get credentials for {}", username);
        Optional<AssumeRoleRequest> assumeRoleRequest = makeUserAssumeRoleRequest(httpServletRequest, username);
        return assumeRoleRequest
                .map(request -> metadataCredentialsProvider.getUserCredentials(request))
                .map(credentials -> {
                    log.debug("Done with request {}", assumeRoleRequest);
                    return GSON.toJson(credentials.get());
                })
                .orElse(null);
    }

    /**
     * Returns the role that the user can assume.
     * This is the call that SDK makes to determine the role to assume.
     *
     * @param httpServletRequest the HTTP Request object
     * @return the role name that the caller could assume
     */
    @GET
    @Path("{apiVersion}/meta-data/iam/security-credentials/")
    @Produces(MediaType.TEXT_PLAIN)
    public String listUserRoles(@Context HttpServletRequest httpServletRequest) {
        log.debug("Processing a request to list roles for {}", LATEST_IAM_CREDENTIALS_ROOT_PATH);
        Optional<AssumeRoleRequest> assumeRoleRequest = makeUserAssumeRoleRequest(httpServletRequest);
        return assumeRoleRequest.map(request -> getRoleNameFromArn(request.getRoleArn()))
                .orElse(null);
    }

    private String getRoleNameFromArn(String roleArn) {
        return roleArn.substring(roleArn.lastIndexOf("/") + 1);
    }

    /**
     * For other requests that do not match the special paths, act as a pass-through to EC2 metadata service.
     *
     * @param uriInfo UriInfo of the request
     * @return the response from EC2 metadata service
     */
    @GET
    @Path("{default: .*}")
    @Produces(MediaType.TEXT_PLAIN)
    public String defaultHandler(@Context UriInfo uriInfo) {
        String uriPath = uriInfo.getPath();

        boolean isListOperation = uriPath.endsWith("/");
        log.debug("Intercepted a normal request to EC2 metadata service. The URI path is: /{}", uriPath);
        if (isListOperation) {
            return String.join("\n", EC2MetadataUtils.getItems("/" + uriPath));
        } else {
            return EC2MetadataUtils.getData("/" + uriPath);
        }
    }

    private Optional<AssumeRoleRequest> makeUserAssumeRoleRequest(HttpServletRequest httpServletRequest) {
        Optional<String> username = identifyCaller(httpServletRequest, true);
        return username.flatMap(user -> mappingInvoker.map(user));
    }

    private Optional<AssumeRoleRequest> makeUserAssumeRoleRequest(HttpServletRequest httpServletRequest, String username) {
        Optional<String> impersonatedUser = Optional.empty();
        if (isImpersonationAuthorized(httpServletRequest, username)) {
            impersonatedUser = Optional.of(username);
        }

        return impersonatedUser.flatMap(user -> mappingInvoker.map(user));
    }

    private Optional<AssumeRoleRequest> makeUserAssumeRoleRequestCertBased(HttpServletRequest httpServletRequest) {
        Optional<String> username = identifyCallerWithCert(httpServletRequest);
        return username.flatMap(user -> mappingInvoker.map(user));
    }

    private Optional<String> identifyCallerWithCert(HttpServletRequest httpServletRequest) {
        X509Certificate cert = CertUtil.getCertificate(httpServletRequest);
        // this is for phrase 1: use the OU from the cert as the username
        String orgUnit = CertUtil.getSubjectAttributes(cert).get(CertUtil.DN_ATTRIBUTE_OU);
        return Optional.ofNullable(orgUnit);
    }

    private Optional<String> identifyCaller(HttpServletRequest httpServletRequest,
                                            boolean isNativeIMDSApi) {
        OptionalInt uid = userIdService.resolveSystemUID(
                httpServletRequest.getLocalAddr(),
                httpServletRequest.getLocalPort(),
                httpServletRequest.getRemoteAddr(),
                httpServletRequest.getRemotePort(),
                isNativeIMDSApi);
        if (uid.isPresent()) {
            Optional<String> username = principalResolver.getUsername(uid.getAsInt());
            log.debug("User making the call {}", username);
            return username;
        }
        log.warn("Could not identify the caller using TCP socket info. Local addr {} " +
                        "local port {} remote addr {} remote port {}",
                httpServletRequest.getLocalAddr(),
                httpServletRequest.getLocalPort(),
                httpServletRequest.getRemoteAddr(),
                httpServletRequest.getRemotePort());
        return Optional.empty();
    }

    private boolean isImpersonationAuthorized(HttpServletRequest httpServletRequest,
                                              String impersonatedUser) {
        Optional<String> caller = identifyCaller(httpServletRequest, false);
        if (caller.isPresent()) {
            if (!applicationConfiguration
                    .getAllowedUsersForImpersonation().contains(caller.get())) {
                log.warn("Unauthorized user {} trying to impersonate {}",
                        caller.get(), impersonatedUser);
                return false;
            }

            log.debug("Authorized {} to impersonate {}", caller.get(), impersonatedUser);
            return true;
        } else {
            log.warn("Failed to identify the caller of impersonation request");
            return false;
        }
    }
}
