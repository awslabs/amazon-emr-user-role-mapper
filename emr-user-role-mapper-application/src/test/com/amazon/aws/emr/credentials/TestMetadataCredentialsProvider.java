// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.credentials;

import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.util.EC2MetadataUtils;

import java.util.Date;
import java.util.Optional;

public class TestMetadataCredentialsProvider implements MetadataCredentialsProvider {

    public static final String TEST_ACCESSKEY_ID = "test-accesskey-id";
    public static final String TEST_SECRETKEY = "test-secret";
    public static final String TEST_SESSION_TOKEN = "test-token";

    @Override
    public Optional<EC2MetadataUtils.IAMSecurityCredential> getUserCredentials(AssumeRoleRequest assumeRoleRequest) {
        EC2MetadataUtils.IAMSecurityCredential iamCredential = new EC2MetadataUtils.IAMSecurityCredential();
        iamCredential.accessKeyId = TEST_ACCESSKEY_ID;
        iamCredential.secretAccessKey = TEST_SECRETKEY;
        iamCredential.token = TEST_SESSION_TOKEN;
        iamCredential.code = "Success";
        iamCredential.type = "AWS-HMAC";
        iamCredential.expiration = new Date().toString();

        return Optional.of(iamCredential);
    }
}
