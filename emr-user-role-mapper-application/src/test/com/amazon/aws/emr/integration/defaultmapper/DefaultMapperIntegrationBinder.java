// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.integration.defaultmapper;

import com.amazon.aws.emr.ApplicationConfiguration;
import com.amazon.aws.emr.common.system.PrincipalResolver;
import com.amazon.aws.emr.common.system.impl.CommandBasedPrincipalResolver;
import com.amazon.aws.emr.common.system.user.UserIdService;
import com.amazon.aws.emr.credentials.MetadataCredentialsProvider;
import com.amazon.aws.emr.credentials.STSCredentialsProvider;
import com.amazon.aws.emr.integration.IntegrationTestsUserService;
import com.amazon.aws.emr.mapping.MappingInvoker;
import javax.inject.Singleton;
import org.glassfish.hk2.api.Immediate;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

/**
 * Bindings used to test {@link com.amazon.aws.emr.mapping.DefaultUserRoleMapperImpl}
 */
public class DefaultMapperIntegrationBinder extends AbstractBinder {

  @Override
  protected void configure() {
    bind(IntegrationTestsUserService.class).to(UserIdService.class);
    bind(MappingInvoker.class).to(MappingInvoker.class).in(Immediate.class);
    bind(STSCredentialsProvider.class).to(MetadataCredentialsProvider.class).in(Singleton.class);
    bind(DefaultMapperImplApplicationConfig.class).to(ApplicationConfiguration.class)
        .in(Immediate.class);
    bind(CommandBasedPrincipalResolver.class).to(PrincipalResolver.class).in(Singleton.class);
  }
}