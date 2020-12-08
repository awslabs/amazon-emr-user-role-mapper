// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr;

import com.amazon.aws.emr.common.Constants;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.hk2.api.Immediate;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The configuration singleton for this application.
 */
@Slf4j
@Immediate
public class ApplicationConfiguration {

    private final static String PROPS_FILE = "/user-role-mapper.properties";
    private Properties properties = new Properties();
    private ImmutableSet<String> IMPERSONATION_ALLOWED_USERS = ImmutableSet.of();

    @PostConstruct
    public void init() {
        try (final InputStream stream =
                     this.getClass().getResourceAsStream(PROPS_FILE)) {
            properties.load(stream);
            if (!isValidConfig()) {
                throw new RuntimeException("Invalid configuration!");
            }
            log.info("Loaded " + properties.toString());

            if (properties.containsKey(Constants.IMPERSONATION_ALLOWED_USERS)) {
                IMPERSONATION_ALLOWED_USERS = ImmutableSet
                        .copyOf(properties.getProperty(Constants.IMPERSONATION_ALLOWED_USERS).split(","))
                        .stream().map(String::trim).collect(ImmutableSet.toImmutableSet());
                log.info("Loaded allowed users for impersonation: {}", IMPERSONATION_ALLOWED_USERS);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not load properties file", e);
        }
    }

    private boolean isValidConfig() {
        boolean isValid = true;
        if ((getProperty(Constants.ROLE_MAPPER_CLASS, null) == null) &&
                (getProperty(Constants.ROLE_MAPPING_S3_BUCKET, null) == null &&
                        getProperty(Constants.ROLE_MAPPING_S3_KEY, null) == null)) {
            log.error("Both custom class name and bucket/key can't be null.");
            isValid = false;
        }

        return isValid;
    }

    /**
     * @return all the property names
     */
    public Set<String> getAllPropertyNames() {
        return properties.keySet().stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    /**
     * @param propertyName a property that may not exist
     * @param defaultValue default value if it does not exist
     * @return value in properties or default if not present
     */
    public String getProperty(String propertyName, String defaultValue) {
        return properties.getProperty(propertyName, defaultValue);
    }

    /**
     * @param propertyName a property that may not exist
     * @param defaultValue default value if it does not exist
     * @return value in properties or default if not present
     */
    public int getProperty(String propertyName, int defaultValue) {
        return Integer.parseInt(properties.getProperty(propertyName, String.valueOf(defaultValue)));
    }

    /**
     * Set a property, overriding any previous value.
     *
     * @param propertyName name of property
     * @param value        value to set
     */
    public void setProperty(String propertyName, String value) {
        properties.put(propertyName, value);
    }

    public Set<String> getAllowedUsersForImpersonation() {
        return this.IMPERSONATION_ALLOWED_USERS;
    }
    public Map<String, String> asMap() {
        return Maps.fromProperties(properties);
    }
}
