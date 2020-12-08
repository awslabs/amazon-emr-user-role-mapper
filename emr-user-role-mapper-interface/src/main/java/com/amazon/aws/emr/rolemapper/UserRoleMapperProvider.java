// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.rolemapper;

import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;

import java.util.Map;
import java.util.Optional;

public interface UserRoleMapperProvider {

    /**
     * Used to initialize the mapper. This is invoked once at Application start.
     */
    void init(Map<String, String> configMap);

    /**
     * Fetch the {@link AssumeRoleRequest} to assume for a given user.
     *
     * @param username
     * @return an {@link Optional} containing the mapped {@link AssumeRoleRequest}
     */
    Optional<AssumeRoleRequest> getMapping(String username);

    /**
     * Refresh the mapping to consult for mapping at a periodic interval.
     */
    void refresh();
}
