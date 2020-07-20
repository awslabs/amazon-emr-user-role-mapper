// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.common.system.impl;

import com.google.common.annotations.VisibleForTesting;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.systems.global.linux;
import org.bytedeco.systems.linux.group;
import org.bytedeco.systems.linux.passwd;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Uses linux native calls to gather user and group information.
 *
 * In order to make native calls, it uses the {@link linux} library
 * which acts as an interface to invoke APIs provided by glibc.
 * @see <a href="https://github.com/bytedeco/javacpp-presets">javacpp-presets</a>
 */
@Slf4j
@NoArgsConstructor
public class JniBasedPrincipalResolver extends AbstractPrincipalResolver {
    private static final int MAX_NUM_GROUPS_FETCH = 100;

    @VisibleForTesting
    JniBasedPrincipalResolver(Integer groupMapTtl, TimeUnit timeUnit) {
        super(groupMapTtl, timeUnit);
    }

    @Override
    @VisibleForTesting
    protected Optional<String> getLinuxUsername(int uid) {
        log.debug("Finding username for uid: {}", uid);

        passwd passwdEntry = linux.getpwuid(uid);
        if (passwdEntry == null) {
            log.error("Couldn't fetch record from password database for uid: {}", uid);
            return Optional.empty();
        }
        return Optional.ofNullable(passwdEntry.pw_name().getString());
    }

    @Override
    @VisibleForTesting
    protected List<String> getLinuxGroups(String username) {
        List<String> groups = new ArrayList<>();

        log.debug("Finding groups for user: {}", username);
        passwd passwdEntry = linux.getpwnam(username);

        if (passwdEntry == null) {
            log.error("Couldn't fetch record from password database for user: {}", username);
            return groups;
        }

        int gid = passwdEntry.pw_gid();
        log.debug("Got group id: {} for username: {}", gid, username);

        int[] numGroups = new int[] { MAX_NUM_GROUPS_FETCH };
        int[] allGroupIds = new int[MAX_NUM_GROUPS_FETCH];

        /* If the number of groups of which user is a member is less than or equal
         * to numGroups, then the value numGroups is returned.
         *
         * If the user is a member of more than numGroups groups, then
         * getgrouplist() returns -1.  In this case, the value returned in
         * numGroups can be used to resize the buffer passed to a further call
         * getgrouplist().
         */
        int getGroupsExitCode = linux.getgrouplist(username, gid, allGroupIds, numGroups);

        if (getGroupsExitCode == -1) {
            log.warn("Some groups may not be fetched, {} has more than {} groups", username, MAX_NUM_GROUPS_FETCH);
        }

        /* nGroups[0] is always set to actual number of groups a user is part of.
         * To avoid spending too much time/ putting memory pressure, we will only
         * fetch minimum of {numGroups[0], MAX_NUM_GROUPS_FETCH}.
         * As a follow up, we can make this limit configurable.
         */
        int numGroupsToFetch = Math.min(numGroups[0], MAX_NUM_GROUPS_FETCH);
        log.debug("Retrieving {} groups for username: {}", numGroupsToFetch, username);
        for (int i = 0; i < numGroupsToFetch; i++) {
            group grp = linux.getgrgid(allGroupIds[i]);

            if (grp == null || grp.gr_name() == null) {
                log.debug("No group entry found for gid: {} username: {}", allGroupIds[i], username);
                continue;
            }
            groups.add(grp.gr_name().getString());
        }
        return groups;
    }
}
