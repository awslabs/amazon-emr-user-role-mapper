// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.api;

import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;
import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * This class implements the request filter before request matching.
 * <p>
 * We are sanitizing the request URI to remove repeating forward slashes, which can trick the request matching.
 * e.g. "////latest/meta-data/iam/security-credentials////EMR_EC2_DefaultRole" will be matched to default handler.
 * So the instance role credentials will be returned if the unprivileged user utilizes this vulnerability.
 * <p>
 * More info: https://jersey.github.io/documentation/latest/filters-and-interceptors.html#d0e9365
 */
@Provider
@PreMatching
@Slf4j
public class RequestFilter implements ContainerRequestFilter {

    private static final List<String> STATIC_SENSITIVE_RESOURCES =
            Collections.singletonList("user-data");

    @Override
    public void filter(ContainerRequestContext ctx) {
        UriInfo uriInfo = ctx.getUriInfo();
        URI sanitizedUri = sanitizeRequestUri(uriInfo);
        if (isAuthorizedUri(sanitizedUri)) {
            ctx.setRequestUri(sanitizedUri);
        } else {
            ctx.abortWith(Response
                    .status(Response.Status.UNAUTHORIZED)
                    .entity("Permission denied to access the resource")
                    .build());
        }
    }

    private URI sanitizeRequestUri(UriInfo uriInfo) {
        String sanitizedUri = uriInfo.getPath().replaceAll("\\/+", "/");
        UriBuilder uriBuilder = uriInfo.getBaseUriBuilder();
        URI newUri = uriBuilder.path(sanitizedUri).build();
        return newUri.normalize();
    }

    private boolean isAuthorizedUri(URI sanitizedUri) {
        String path = sanitizedUri.getPath();

        for (String staticSensitiveResource : STATIC_SENSITIVE_RESOURCES) {
            if (path.contains(staticSensitiveResource)) {
                return false;
            }
        }
        return true;
    }
}

