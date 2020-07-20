// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.common.system.impl;

import org.junit.Before;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

public class JniBasedPrincipalResolverTest extends PrincipalResolverTestBase {

    @Before
    public void setup() {
        principalResolver = mock(JniBasedPrincipalResolver.class,
            withSettings().useConstructor(TTL_SECS, TimeUnit.SECONDS).defaultAnswer(CALLS_REAL_METHODS));
        setupMocks();
    }

    private void setupMocks() {
        doReturn(Optional.of(VALID_USERNAME)).when(principalResolver).getLinuxUsername(eq(VALID_UID));
        doReturn(SINGLE_GRP).when(principalResolver).getLinuxGroups(eq(USER_SINGLE_GRP));
        doReturn(MULTI_GRPS).when(principalResolver).getLinuxGroups(eq(USER_MULTI_GRPS));
    }
}
