// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.ws;

import javax.inject.Inject;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * The Jersey servlet application.
 */
public class UserRoleMapperIntegApplication extends ResourceConfig {

    @Inject
    public UserRoleMapperIntegApplication() {
        String[] pkgs = new String[]{"com.amazon.aws.emr.api", "com.amazon.aws.emr.mapping"};
        packages(pkgs);
        register(ImmediateFeature.class);
        register(new UserRoleMapperIntegBinder());
    }
}
