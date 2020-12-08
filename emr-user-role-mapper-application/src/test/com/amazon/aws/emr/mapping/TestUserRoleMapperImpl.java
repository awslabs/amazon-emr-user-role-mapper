// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.mapping;

import com.amazon.aws.emr.common.TestConstants;
import com.amazon.aws.emr.common.system.PrincipalResolver;
import com.amazon.aws.emr.rolemapper.UserRoleMapperProvider;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import lombok.extern.slf4j.Slf4j;
import org.powermock.api.mockito.PowerMockito;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Slf4j
public class TestUserRoleMapperImpl implements UserRoleMapperProvider {
    private Map<String, AssumeRoleRequest> userRoleMapping = new HashMap<>();
    private Map<String, AssumeRoleRequest> groupRoleMapping = new HashMap<>();

    private PrincipalResolver principalResolver;

    @Override
    public void init(Map<String, String> configMap) {
        AssumeRoleRequest assumeRoleRequestU1 = new AssumeRoleRequest()
                .withRoleArn("arn:aws:iam::123456789:role/u1")
                .withRoleSessionName("u1");
        AssumeRoleRequest assumeRoleRequestU2 = new AssumeRoleRequest()
                .withRoleArn("arn:aws:iam::123456789:role/u2")
                .withRoleSessionName("u2");
        AssumeRoleRequest assumeRoleRequestU3 = new AssumeRoleRequest()
                .withRoleArn("arn:aws:iam::123456789:role/g1")
                .withRoleSessionName("g2");
        userRoleMapping.put(TestConstants.USER1_ROLE_NAME, assumeRoleRequestU1);
        userRoleMapping.put(TestConstants.USER2_ROLE_NAME, assumeRoleRequestU2);
        groupRoleMapping.put(TestConstants.GROUP_ROLE_NAME, assumeRoleRequestU3);

        principalResolver = PowerMockito.mock(PrincipalResolver.class);
        when(principalResolver.getGroups(eq("u2"))).thenReturn(Optional.of(Collections.singletonList("g1")));
        when(principalResolver.getGroups(eq("u4"))).thenReturn(Optional.of(Collections.singletonList("g1")));
        when(principalResolver.getGroups(eq("u3"))).thenReturn(Optional.of(Collections.singletonList("g2")));
    }

    @Override
    public Optional<AssumeRoleRequest> getMapping(String username) {
        AssumeRoleRequest assumeRoleRequest;
        assumeRoleRequest = userRoleMapping.get(username);
        if (assumeRoleRequest != null) {
            return Optional.of(userRoleMapping.get(username));
        }
        Optional<List<String>> groups = principalResolver.getGroups(username);
        return groups.orElse(Collections.emptyList()).stream()
                .filter(g -> groupRoleMapping.get(g) != null)
                .map(g -> groupRoleMapping.get(g))
                .findFirst();
    }

    @Override
    public void refresh() {
    }
}
