// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.integration.policyunionmapper;

import com.amazon.aws.emr.ws.ImmediateFeature;
import javax.inject.Inject;
import org.glassfish.jersey.server.ResourceConfig;

public class PoliciesUnionProviderImplIntegrationApplication extends ResourceConfig {

    @Inject
    public PoliciesUnionProviderImplIntegrationApplication() {
        String[] pkgs = new String[]{"com.amazon.aws.emr.api", "com.amazon.aws.emr.mapping"};
        packages(pkgs);
        register(ImmediateFeature.class);
        register(new PoliciesUnionMapperIntegrationBinder());
    }
}
