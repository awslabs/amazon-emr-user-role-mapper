// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.common.system.impl;

import com.google.common.annotations.VisibleForTesting;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Uses linux commands to gather user and group information.
 */
@Slf4j
@NoArgsConstructor
public class CommandBasedPrincipalResolver extends AbstractPrincipalResolver {

    @VisibleForTesting
    CommandBasedPrincipalResolver(Integer groupMapTtl, TimeUnit timeUnit) {
        super(groupMapTtl, timeUnit);
    }

    @Override
    @VisibleForTesting
    protected Optional<String> getLinuxUsername(int uid) {
        List<String> getUsernameCommand = Arrays.asList("id", "-nu", String.valueOf(uid));

        log.debug("Finding username for uid: {}", uid);
        List<String> getUsernameOutput = runCommand(getUsernameCommand);
        return getUsernameOutput.stream().findFirst();
    }

    @Override
    @VisibleForTesting
    protected List<String> getLinuxGroups(String username) {
        List<String> getGroupsCommand = Arrays.asList("id", "-Gn", username);

        log.debug("Finding groups for user: {}", username);
        return runCommand(getGroupsCommand);
    }

    /**
     * Returns the command output delimited by space
     * In case of any error such as a non zero return code from subprocess, exception etc
     * returns an empty list.
     */
    protected List<String> runCommand(List<String> command) {
        List<String> commandOutput = new ArrayList<>();

        try {
            Process process = new ProcessBuilder(command).start();

            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                log.error("Command didn't finish: {}", command);
                process.destroyForcibly();
                return commandOutput;
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return br.lines().flatMap(Pattern.compile("\\s+")::splitAsStream).collect(Collectors.toList());
            }
        } catch (IOException | InterruptedException ie) {
            log.error("Couldn't run command to retrieve user/ groups: {}", command, ie);
        }

        return commandOutput;
    }
}
