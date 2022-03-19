// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.common.system.impl;

import com.amazon.aws.emr.common.system.PrincipalResolver;
import com.amazon.aws.emr.model.User;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Provides shared caching functionality for concrete implementations.
 */
@Slf4j
public abstract class AbstractPrincipalResolver implements PrincipalResolver {
    private static final int USER_MAP_MAX_SIZE = 10000;
    private static final String LINUX_USERS_FILE = "/etc/passwd";

    private static final int GROUP_MAP_MAX_SIZE = 10000;
    private static final int DEFAULT_GROUP_MAP_EXPIRATION_MINS = 60 * 4;

    private final LoadingCache<Integer, Optional<String>> userMap;
    private final LoadingCache<String, List<String>> groupMap;

    AbstractPrincipalResolver() {
        this(DEFAULT_GROUP_MAP_EXPIRATION_MINS, TimeUnit.MINUTES);
    }

    AbstractPrincipalResolver(Integer groupMapTtl, TimeUnit timeUnit) {
        CacheLoader<Integer, Optional<String>> userLoader = new CacheLoader<Integer, Optional<String>>() {
            @Override
            public Optional<String> load(Integer uid) {
                return getLinuxUsername(uid);
            }
        };

        CacheLoader<String, List<String>> groupLoader = new CacheLoader<String, List<String>>() {
            @Override
            public List<String> load(String username) {
                return getLinuxGroups(username);
            }
        };

        this.userMap = CacheBuilder.newBuilder()
                                   .maximumSize(USER_MAP_MAX_SIZE)
                                   .build(userLoader);

        this.groupMap = CacheBuilder.newBuilder()
                                    .maximumSize(GROUP_MAP_MAX_SIZE)
                                    .expireAfterWrite(groupMapTtl, timeUnit)
                                    .build(groupLoader);
    }

    @PostConstruct
    void init() {
        log.info("Reading all OS users");
        readOSUsers();
    }

    /**
     * We don't employ locks here as we never clear the mapping.
     * User id once assigned a username by Linux is not reused even if the same username is created again.
     * <p>
     * Note the map might contain more entries if users get deleted but that should be an infrequent operation.
     */
    private synchronized void readOSUsers() {
        try (Stream<String> stream = Files.lines(getSystemUsersFileName())) {
            stream.filter(s -> s.charAt(0) != '#').map(User::createFromPasswdEntry)
                  .filter(u -> !u.getShell().equals("/usr/sbin/nologin"))
                  .forEach(user -> userMap.put(user.getUid(), Optional.ofNullable(user.getName())));
        } catch (IOException ioe) {
            log.error("Couldn't parse system users file", ioe);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> getUsername(int uid) {
        try {
            return userMap.getUnchecked(uid);
        } catch (UncheckedExecutionException e) {
            // ignore and return empty username, the underlying method should have logged exception details
        }
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Uses linux command: id -Gn {username} to get groups
     */
    @Override
    public Optional<List<String>> getGroups(String username)
    {
        try {
            return Optional.ofNullable(groupMap.getUnchecked(username));
        }
        catch (UncheckedExecutionException e) {
            // ignore and return empty groups, the underlying method should have logged exception details
            // not returning empty to preserve the existing behavior.
            return Optional.of(Collections.emptyList());
        }
    }

    /**
     * Get linux username corresponding to POSIX userId
     *
     * @param userId
     * @return Username wrapped in an {@code Optional}
     */
    protected abstract Optional<String> getLinuxUsername(int userId);

    /**
     * @param username
     * @return List containing groups if mapping is found else empty
     */
    protected abstract List<String> getLinuxGroups(String username);

    @VisibleForTesting
    protected Path getSystemUsersFileName() {
        return Paths.get(LINUX_USERS_FILE);
    }
}
