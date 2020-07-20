// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.common.system;

import java.util.List;
import java.util.Optional;

public interface PrincipalResolver {
    /**
     * Gets the username associated with a user id.
     *
     * @param uid the user id whose mapping needs to be found.
     * @return an {@link Optional} containing username if mapping found, else {@link Optional#empty()}
     */
    Optional<String> getUsername(int uid);

    /**
     * Get the group names a username belongs to.
     *
     * @param username
     * @return list of group names
     */
    Optional<List<String>> getGroups(String username);

}
