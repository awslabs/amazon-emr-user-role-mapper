// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.mapping;

import com.amazon.aws.emr.ApplicationConfiguration;
import com.amazon.aws.emr.common.system.PrincipalResolver;
import com.amazon.aws.emr.common.system.factory.PrincipalResolverFactory;
import com.amazon.aws.emr.model.PrincipalRoleMapping;
import com.amazon.aws.emr.model.PrincipalRoleMappings;
import com.amazon.aws.emr.rolemapper.UserRoleMapperProvider;
import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Properties;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default implementation to read mapping from S3 in JSON format.
 * The format for the JSON can be found in {@code PrincipalRoleMappings}.
 */
@NoArgsConstructor
@Slf4j
public class DefaultUserRoleMapperImpl extends S3BasedUserMappingImplBase
    implements UserRoleMapperProvider {

    static final AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
            .setPrettyPrinting()
            .create();


    private final Map<String, AssumeRoleRequest> userRoleMapping = new HashMap<>();
    private final Map<String, AssumeRoleRequest> groupRoleMapping = new HashMap<>();

    private PrincipalResolver principalResolver;

    public DefaultUserRoleMapperImpl(String bucketName, String key, PrincipalResolver principalResolver) {
        this.bucketName = Objects.requireNonNull(bucketName);

        // TODO: We may relax this to allow null value. In case of null value, parse all keys under above bucket
        this.key = Objects.requireNonNull(key);
        this.etag = null;
        this.principalResolver = Objects.requireNonNull(principalResolver);
    }

    /**
     * Inits the mapper.
     */
    public void init(Map<String, String> configMap) {
    }

    /**
     * @param username the user whose mapping we want.
     *                 Username mapping takes precedence over group name mapping.
     *                 If multiple group name mappings exist, then the first one is returned.
     * @return an {@code Optional} of {@code AssumeRoleRequest}
     */
    public Optional<AssumeRoleRequest> getMapping(String username) {
        // Consult if we have a mapping with username
        AssumeRoleRequest assumeRoleRequest = userRoleMapping.get(username);
        if (assumeRoleRequest != null) {
            log.debug("Usermapping found for {} as {}", username, assumeRoleRequest);
            return Optional.of(assumeRoleRequest);
        }
        log.debug("No user mapping found for {}. Checking with group mapping.", username);
        Optional<List<String>> groups = principalResolver.getGroups(username);

        return groups.orElse(Collections.emptyList()).stream()
                .filter(group -> groupRoleMapping.get(group) != null)
                .map(group -> {
                    log.debug("Mapped {} with group membership of {}", username, group);
                    return groupRoleMapping.get(group);
                })
                .findFirst();
    }

    /**
     * Populates the internal maps with the mapping in S3.
     * The format for the JSON can be found in {@code PrincipalRoleMappings}.
     *
     * @param jsonString the S3 JSON represented as a String.
     */
    void processFile(String jsonString) {
        log.info("Received the following JSON {}", jsonString);
        PrincipalRoleMappings principalRoleMappings = GSON.fromJson(jsonString, PrincipalRoleMappings.class);
        // Clear the old mapping now since we found a new valid mapping!
        userRoleMapping.clear();
        groupRoleMapping.clear();
        for (PrincipalRoleMapping principalRoleMapping : principalRoleMappings.getPrincipalRoleMappings()) {
            if (principalRoleMapping == null) {
                log.info("Invalid record!");
                continue;
            }
            String principal = principalRoleMapping.getUsername() != null ? principalRoleMapping.getUsername() :
                    principalRoleMapping.getGroupname();
            if (principal == null) {
                log.info("Invalid record containing no username or groupname");
                continue;
            }
            String roleArn = principalRoleMapping.getRoleArn();
            if (roleArn == null) {
                log.info("Invalid record containing no role ARN");
                continue;
            }
            AssumeRoleRequest assumeRoleRequest = new AssumeRoleRequest()
                    .withRoleArn(principalRoleMapping.getRoleArn())
                    .withRoleSessionName(principal) // Use principal as session name
                    .withDurationSeconds(principalRoleMapping.getDurationSeconds())
                    .withPolicy(principalRoleMapping.getPolicy())
                    .withSerialNumber(principalRoleMapping.getSerialNumber())
                    .withExternalId(principalRoleMapping.getExternalId());
            if (principalRoleMapping.getUsername() != null) {
                userRoleMapping.put(principal, assumeRoleRequest);
            } else {
                groupRoleMapping.put(principal, assumeRoleRequest);
            }
            log.info("Mapped {} to {}", principal, assumeRoleRequest);
        }
    }
}
