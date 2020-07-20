// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.common.system.impl;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PrincipalResolverTestBase {
    protected static final Integer TTL_SECS = 3;

    protected static final int VALID_UID = 123;
    protected static final String VALID_USERNAME = "validUsername";

    protected static final String USER_SINGLE_GRP = "userSingleGrp";
    protected static final List<String> SINGLE_GRP = Arrays.asList("grp1");

    protected static final String USER_MULTI_GRPS = "userMultiGrps";
    protected static final List<String> MULTI_GRPS = Arrays.asList("grp1", "grp2", "grp3");

    protected AbstractPrincipalResolver principalResolver;

    @Test
    public void validUid_returnsCorrespondingUsername() {
        assertThat(principalResolver.getUsername(VALID_UID).get(), is(VALID_USERNAME));
        verify(principalResolver).getLinuxUsername(VALID_UID);

        // ensure username is returned from cache
        assertThat(principalResolver.getUsername(VALID_UID).get(), is(VALID_USERNAME));
        assertThat(principalResolver.getUsername(VALID_UID).get(), is(VALID_USERNAME));

        // both the above calls retrieve mapping from cache so there should not be any additional invocations
        verify(principalResolver, times(1)).getLinuxUsername(VALID_UID);
    }

    @Test
    public void validUsername_returnsCorrespondingGroups() {
        assertThat(principalResolver.getGroups(USER_SINGLE_GRP).get(), is(SINGLE_GRP));
        verify(principalResolver).getLinuxGroups(USER_SINGLE_GRP);
        assertThat(principalResolver.getGroups(USER_MULTI_GRPS).get(), is(MULTI_GRPS));
        verify(principalResolver).getLinuxGroups(USER_MULTI_GRPS);
    }

    @Test
    public void subsequentRetrieveGroupsCall_returnGroupsFromCache() {
        assertThat(principalResolver.getGroups(USER_MULTI_GRPS).get(), is(MULTI_GRPS));
        verify(principalResolver).getLinuxGroups(USER_MULTI_GRPS);

        assertThat(principalResolver.getGroups(USER_MULTI_GRPS).get(), is(MULTI_GRPS));
        assertThat(principalResolver.getGroups(USER_MULTI_GRPS).get(), is(MULTI_GRPS));
        // both the above calls retrieve mapping from cache so there should not be any additional invocations
        verify(principalResolver, times(1)).getLinuxGroups(USER_MULTI_GRPS);
    }

    @Test
    public void staleGroupMapping_shouldGetRefreshed() throws InterruptedException {
        // populate the cache with groups
        doReturn(MULTI_GRPS).when(principalResolver).getLinuxGroups(eq(USER_MULTI_GRPS));
        assertThat(principalResolver.getGroups(USER_MULTI_GRPS).get(), is(MULTI_GRPS));
        verify(principalResolver).getLinuxGroups(USER_MULTI_GRPS);

        assertThat(principalResolver.getGroups(USER_MULTI_GRPS).get(), is(MULTI_GRPS));
        // above call retrieves mapping from cache so there should not be any additional invocation
        verify(principalResolver, times(1)).getLinuxGroups((USER_MULTI_GRPS));

        // sleep for more than ttl value for cache
        Thread.sleep(TTL_SECS * 1000 + 5);
        assertThat(principalResolver.getGroups(USER_MULTI_GRPS).get(), is(MULTI_GRPS));
        verify(principalResolver, times(2)).getLinuxGroups(USER_MULTI_GRPS);
    }
}
