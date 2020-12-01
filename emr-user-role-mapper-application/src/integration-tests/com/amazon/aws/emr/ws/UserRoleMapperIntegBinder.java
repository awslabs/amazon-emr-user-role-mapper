// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.ws;

import com.amazon.aws.emr.ApplicationConfiguration;
import com.amazon.aws.emr.common.system.PrincipalResolver;
import com.amazon.aws.emr.common.system.user.UserIdService;
import com.amazon.aws.emr.credentials.MetadataCredentialsProvider;
import com.amazon.aws.emr.credentials.STSCredentialsProvider;
import com.amazon.aws.emr.mapping.MappingInvoker;
import com.amazon.aws.emr.system.user.IntegTestPrincipalResolver;
import com.amazon.aws.emr.system.user.IntegTestsUserService;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Immediate;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

/**
 * Bindings used in {@link UserRoleMapperApplication}.
 */
public class UserRoleMapperIntegBinder extends AbstractBinder {

    @Override
    protected void configure() {
        bind(IntegTestsUserService.class).to(UserIdService.class);
        bind(MappingInvoker.class).to(MappingInvoker.class).in(Immediate.class);
        bind(STSCredentialsProvider.class).to(MetadataCredentialsProvider.class).in(Singleton.class);
        bind(IntegTestsAppConfig.class).to(ApplicationConfiguration.class).in(Immediate.class);
        bind(IntegTestPrincipalResolver.class).to(PrincipalResolver.class).in(Singleton.class);
    }
}
