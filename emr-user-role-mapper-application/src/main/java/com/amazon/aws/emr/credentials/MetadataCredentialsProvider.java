// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.credentials;

import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.util.EC2MetadataUtils;

import java.util.Optional;

/**
 * Provider for IMDS credentials.
 */
public interface MetadataCredentialsProvider {
    /**
     * Gets credentials for {@code AssumeRoleRequest}.
     *
     * @param assumeRoleRequest the request to assume
     * @return credentials in the {@link EC2MetadataUtils.IAMSecurityCredential} format
     */
    Optional<EC2MetadataUtils.IAMSecurityCredential> getUserCredentials(AssumeRoleRequest assumeRoleRequest);
}
