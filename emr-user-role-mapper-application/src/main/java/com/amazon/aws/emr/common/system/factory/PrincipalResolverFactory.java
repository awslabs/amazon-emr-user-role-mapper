// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.common.system.factory;

import com.amazon.aws.emr.ApplicationConfiguration;
import com.amazon.aws.emr.common.Constants;
import com.amazon.aws.emr.common.system.PrincipalResolver;
import com.amazon.aws.emr.common.system.impl.CommandBasedPrincipalResolver;
import com.amazon.aws.emr.common.system.impl.JniBasedPrincipalResolver;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.hk2.api.Factory;

import javax.inject.Inject;

/**
 * Factory to return principal resolver implementation depending on {@link Constants#PRINCIPAL_RESOLVER_STRATEGY_KEY}
 * value.
 * By default, it uses the JNI implementation to retrieve user/ groups.
 */
@Slf4j
public class PrincipalResolverFactory implements Factory<PrincipalResolver> {
    @Inject
    private ApplicationConfiguration appConfig;

    @Override
    public PrincipalResolver provide() {
        String principalResolverStrategy = appConfig
            .getProperty(Constants.PRINCIPAL_RESOLVER_STRATEGY_KEY, Constants.DEFAULT_PRINCIPAL_RESOLVER_STRATEGY);

        log.info("Using principal resolver strategy: {}", principalResolverStrategy);
        if (Constants.DEFAULT_PRINCIPAL_RESOLVER_STRATEGY.equalsIgnoreCase(principalResolverStrategy))
            return new CommandBasedPrincipalResolver();

        return new JniBasedPrincipalResolver();
    }

    @Override
    public void dispose(PrincipalResolver instance) {
        // noop
    }
}
