// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.common.system.impl;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ CommandBasedPrincipalResolver.class })
@PowerMockIgnore({ "javax.management.*", "javax.net.ssl.*" })
public class CommandBasedPrincipalResolverTest extends PrincipalResolverTestBase {
    private static final List<String> USER_SINGLE_GRP_GET_GRPS_CMD = Arrays.asList("id", "-Gn", USER_SINGLE_GRP);
    private static final List<String> USER_MULTI_GRPS_GET_GRPS_CMD = Arrays.asList("id", "-Gn", USER_MULTI_GRPS);
    private static final List<String> GET_USERNAME_CMD = Arrays.asList("id", "-nu", String.valueOf(VALID_UID));

    @Before
    public void setup() throws Exception {
        principalResolver = mock(CommandBasedPrincipalResolver.class,
            withSettings().useConstructor(TTL_SECS, TimeUnit.SECONDS).defaultAnswer(CALLS_REAL_METHODS));
        setupGetUsernameMock();
        setupGetGrpsMocks();
    }

    @Test
    public void linuxCommandDidNotFinish_returnsNoGroups() throws Exception {
        ProcessBuilder mockProcessBuilder = PowerMockito.mock(ProcessBuilder.class);
        PowerMockito.whenNew(ProcessBuilder.class).withArguments(anyList()).thenReturn(mockProcessBuilder);

        Process mockProcess = mock(Process.class);
        when(mockProcessBuilder.start()).thenReturn(mockProcess);
        when(mockProcess.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(false);

        // verify that getGroups will just return empty list and not an exception
        assertThat(principalResolver.getGroups(USER_MULTI_GRPS).get().isEmpty(), is(true));

        // verify that getLinuxGroups throws an exception so the cache does not get populated
        try {
            principalResolver.getLinuxGroups(USER_MULTI_GRPS);
            Assert.fail("should have thrown timeout exception");
        } catch (RuntimeException e) {
            assertTrue(e.getCause().getClass().equals(TimeoutException.class));
        }

        // call getGroups again and verify that we still call underlying getLinuxGroups as cache is not populated
        assertThat(principalResolver.getGroups(USER_MULTI_GRPS).get().isEmpty(), is(true));
        verify(principalResolver, times(3)).getLinuxGroups(USER_MULTI_GRPS);
        verify(mockProcess, times(3)).waitFor(anyLong(), any());
    }

    private void setupGetGrpsMocks() throws Exception {
        ProcessBuilder mockProcessBuilderGetSingleGrp = PowerMockito.mock(ProcessBuilder.class);
        ProcessBuilder mockProcessBuilderGetMultiGrp = PowerMockito.mock(ProcessBuilder.class);
        PowerMockito.whenNew(ProcessBuilder.class).withArguments(USER_SINGLE_GRP_GET_GRPS_CMD)
                    .thenReturn(mockProcessBuilderGetSingleGrp);
        PowerMockito.whenNew(ProcessBuilder.class).withArguments(USER_MULTI_GRPS_GET_GRPS_CMD)
                    .thenReturn(mockProcessBuilderGetMultiGrp);

        Process subProcessSingleGrp = mock(Process.class);
        Process subProcessMultiGrp = mock(Process.class);
        when(mockProcessBuilderGetSingleGrp.start()).thenReturn(subProcessSingleGrp);
        when(mockProcessBuilderGetMultiGrp.start()).thenReturn(subProcessMultiGrp);
        when(subProcessSingleGrp.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(subProcessMultiGrp.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(subProcessSingleGrp.getInputStream())
            .thenReturn(new ByteArrayInputStream(String.join(" ", SINGLE_GRP).getBytes()));
        when(subProcessMultiGrp.getInputStream())
            .thenReturn(new ByteArrayInputStream(String.join(" ", MULTI_GRPS).getBytes()));
    }

    private void setupGetUsernameMock() throws Exception {
        ProcessBuilder mockProcessBuilder = PowerMockito.mock(ProcessBuilder.class);
        PowerMockito.whenNew(ProcessBuilder.class).withArguments(GET_USERNAME_CMD).thenReturn(mockProcessBuilder);

        Process subprocess = mock(Process.class);
        when(mockProcessBuilder.start()).thenReturn(subprocess);
        when(subprocess.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(subprocess.getInputStream()).thenReturn(new ByteArrayInputStream(VALID_USERNAME.getBytes()));
    }
}