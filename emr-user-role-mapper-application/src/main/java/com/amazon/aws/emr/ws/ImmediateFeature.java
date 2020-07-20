// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.ws;

import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;

import javax.inject.Inject;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

/**
 * Enables {@link ImmediateFeature} in this Jersey application.
 */
public class ImmediateFeature implements Feature {

    @Inject
    public ImmediateFeature(ServiceLocator locator) {
        ServiceLocatorUtilities.enableImmediateScope(locator);
    }

    @Override
    public boolean configure(FeatureContext context) {
        return true;
    }
}