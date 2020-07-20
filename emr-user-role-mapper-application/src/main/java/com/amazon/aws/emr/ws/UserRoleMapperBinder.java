// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.ws;

import com.amazon.aws.emr.ApplicationConfiguration;
import com.amazon.aws.emr.common.system.PrincipalResolver;
import com.amazon.aws.emr.common.system.factory.PrincipalResolverFactory;
import com.amazon.aws.emr.credentials.MetadataCredentialsProvider;
import com.amazon.aws.emr.credentials.STSCredentialsProvider;
import com.amazon.aws.emr.mapping.MappingInvoker;
import com.amazon.aws.emr.common.system.user.LinuxUserIdService;
import com.amazon.aws.emr.common.system.user.UserIdService;
import org.glassfish.hk2.api.Immediate;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.inject.Singleton;

/**
 * Bindings used in {@link UserRoleMapperApplication}.
 */
public class UserRoleMapperBinder extends AbstractBinder {

    @Override
    protected void configure() {
        bind(LinuxUserIdService.class).to(UserIdService.class);
        bind(MappingInvoker.class).to(MappingInvoker.class).in(Immediate.class);
        bind(STSCredentialsProvider.class).to(MetadataCredentialsProvider.class).in(Singleton.class);
        bind(ApplicationConfiguration.class).to(ApplicationConfiguration.class).in(Immediate.class);
        bindFactory(PrincipalResolverFactory.class).to(PrincipalResolver.class).in(Singleton.class);
    }
}
