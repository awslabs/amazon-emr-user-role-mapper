// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.common.system.impl;

import com.amazon.aws.emr.ApplicationConfiguration;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.amazon.aws.emr.common.Constants.COMMAND_TIMEOUT_SECONDS;
import static com.amazon.aws.emr.common.Constants.DEFAULT_COMMAND_TIMEOUT_SECONDS;

/**
 * Uses linux commands to gather user and group information.
 */
@Slf4j
public class CommandBasedPrincipalResolver extends AbstractPrincipalResolver {

    ApplicationConfiguration appConfig;

    public CommandBasedPrincipalResolver(ApplicationConfiguration applicationConfiguration) {
        this.appConfig = applicationConfiguration;
    }

    @VisibleForTesting
    CommandBasedPrincipalResolver(Integer groupMapTtl, TimeUnit timeUnit)
    {
        super(groupMapTtl, timeUnit);
        this.appConfig = new ApplicationConfiguration();
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
    public List<String> runCommand(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).start();
            String commandTimeoutSeconds = appConfig.getProperty(COMMAND_TIMEOUT_SECONDS, DEFAULT_COMMAND_TIMEOUT_SECONDS);
            if (!process.waitFor(Integer.valueOf(commandTimeoutSeconds), TimeUnit.SECONDS)) {
                log.error("Command didn't finish: {}", command);
                process.destroyForcibly();
                throw new TimeoutException(Arrays.toString(command.toArray()) +  " timed out after " + commandTimeoutSeconds + " seconds");
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return br.lines().flatMap(Pattern.compile("\\s+")::splitAsStream).collect(Collectors.toList());
            }
        } catch (IOException | InterruptedException | TimeoutException ie) {
            log.error("Couldn't run command to retrieve user/ groups: {}", command, ie);
            throw new RuntimeException(ie);
        }
    }
}
