// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.mapping;

import com.amazon.aws.emr.ApplicationConfiguration;
import com.amazon.aws.emr.common.Constants;
import com.amazon.aws.emr.common.system.PrincipalResolver;
import com.amazon.aws.emr.rolemapper.UserRoleMapperProvider;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.hk2.api.Immediate;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Maps username to the {@code AssumeRoleRequest}.
 */
@Slf4j
@Immediate
public class MappingInvoker {
    // The mapping would be read many times, but changed quite infrequently!
    // Hence we don't block readers if there is no change in mapping.
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLockInRwLock = rwLock.readLock();
    private final Lock writeLockInRwLock = rwLock.writeLock();

    UserRoleMapperProvider roleMapperProvider;

    @Inject
    ApplicationConfiguration applicationConfiguration;

    @Inject
    PrincipalResolver principalResolver;

    /**
     * Constructs a mapper object via reflection and delegates calls to it.
     * Also creates a thread to refresh mappings.
     */
    @PostConstruct
    void init() {
        try {
            String className = applicationConfiguration.getProperty(Constants.ROLE_MAPPER_CLASS,
                    Constants.ROLE_MAPPING_DEFAULT_CLASSNAME);
            log.info("Trying to load {}", className);
            if (isS3BasedProviderImpl(className)) {
                // For our default mapper implementation we need at least the S3 bucket name and key
                Constructor c = Class.forName(className)
                                     .getConstructor(String.class, String.class, PrincipalResolver.class);
                String bucketName = applicationConfiguration.getProperty(Constants.ROLE_MAPPING_S3_BUCKET, null);
                String key = applicationConfiguration.getProperty(Constants.ROLE_MAPPING_S3_KEY, null);
                roleMapperProvider = (UserRoleMapperProvider) c.newInstance(bucketName, key, principalResolver);
                log.info("Successfully created the mapper using {}/{}", bucketName, key);
            } else {
                Class clazz = Class.forName(className);
                roleMapperProvider = (UserRoleMapperProvider) clazz.newInstance();
            }
            roleMapperProvider.init(applicationConfiguration.asMap());
            log.info("Initialized the mapper.");
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            log.error("Could not load the mapper", e);
            throw new RuntimeException("Could not load the mapper class", e);
        } catch (Throwable t) {
            log.error("Could not load the mapper", t);
            throw new RuntimeException("Could not initialize the mapper", t);
        }
        int refreshIntervalMins = Integer.parseInt(applicationConfiguration.getProperty
                (Constants.ROLE_MAPPPING_REFRESH_INTERVAL_MIN, Constants.ROLE_MAPPPING_DEFAULT_REFRESH_INTERVAL_MIN));
        createRefreshTask(Math.max(Constants.ROLE_MAPPING_MIN_REFRESH_INTERVAL_MIN, refreshIntervalMins));
    }

    @VisibleForTesting
    public boolean isS3BasedProviderImpl(String className) {
        return className.equals(Constants.ROLE_MAPPING_DEFAULT_CLASSNAME) ||
            className.equals(Constants.ROLE_MAPPING_MANAGED_POLICY_CLASSNAME);
    }

    /**
     * Maps a user to an {@code Optional} of {@link AssumeRoleRequest}.
     * This is invoked by many threads and we employ a reentrant read lock
     * to stay unblocked as long as there is no need to refresh mapping.
     *
     * @param username
     * @return
     */
    public Optional<AssumeRoleRequest> map(String username) {
        readLockInRwLock.lock();
        try {
            Optional<AssumeRoleRequest> assumeRoleRequest = roleMapperProvider.getMapping(username);
            log.debug("Found mapping for {} as {}", username, assumeRoleRequest);
            return assumeRoleRequest;
        } catch (Throwable t) {
            // We are running some custom code that could throw anything.
            log.error("Got exception in getting mapping for {}", username, t);
            return Optional.empty();
        } finally {
            readLockInRwLock.unlock();
        }
    }

    /**
     * Creates a thread that runs the user provided refresh method periodically.
     * It acquires a write lock and reloads the mapping.
     *
     * @param refreshIntervalMins
     */
    private void createRefreshTask(int refreshIntervalMins) {
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("refresh-mapping-%d")
                .setDaemon(true)
                .build();
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(threadFactory);
        exec.scheduleAtFixedRate(() -> {
            writeLockInRwLock.lock();
            try {
                log.debug("Refreshing the user role mapping.");
                roleMapperProvider.refresh();
            } catch (Throwable t) {
                // We are running some custom code that could throw anything.
                log.error("Got an error while refreshing", t);
            } finally {
                writeLockInRwLock.unlock();
            }
        }, 0, refreshIntervalMins, TimeUnit.MINUTES);
    }
}
