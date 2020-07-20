// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.common.system.user;

import com.amazon.aws.emr.common.system.PrincipalResolver;
import com.amazon.aws.emr.common.system.impl.CommandBasedPrincipalResolver;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestCommandBasedPrincipalResolver extends CommandBasedPrincipalResolver implements PrincipalResolver {
    private static final String TEST_USERS_FILE = "/test-users";

    protected Path getSystemUsersFileName() {
        java.net.URL url = this.getClass().getResource(TEST_USERS_FILE);
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
