// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.ws;

import org.glassfish.jersey.server.ResourceConfig;

import javax.inject.Inject;

/**
 * The Jersey servlet application.
 */
public class UserRoleMapperApplication extends ResourceConfig {

    @Inject
    public UserRoleMapperApplication() {
        String[] pkgs = new String[]{"com.amazon.aws.emr.api", "com.amazon.aws.emr.mapping"};
        packages(pkgs);
        register(ImmediateFeature.class);
        register(new UserRoleMapperBinder());
    }
}
