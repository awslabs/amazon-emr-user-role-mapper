// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.credentials;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.util.EC2MetadataUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Fetches credentials for {@code AssumeRoleRequest} from STS.
 */
@Slf4j
@Singleton
public class STSCredentialsProvider implements MetadataCredentialsProvider {

    public static final Duration MIN_REMAINING_TIME_TO_REFRESH_CREDENTIALS = Duration.ofMinutes(10);
    public static final Duration MAX_RANDOM_TIME_TO_REFRESH_CREDENTIALS = Duration.ofMinutes(5);
    private static final int CREDENTIALS_MAP_MAX_SIZE = 20000;
    // Initialized later for testing using mocks.
    public static AWSSecurityTokenService stsClient = null;
    public static Region region = null;

    private final LoadingCache<AssumeRoleRequest, Optional<EC2MetadataUtils.IAMSecurityCredential>> credentialsCache = CacheBuilder
        .newBuilder().maximumSize(CREDENTIALS_MAP_MAX_SIZE)
        .build(new CacheLoader<AssumeRoleRequest, Optional<EC2MetadataUtils.IAMSecurityCredential>>() {
            @Override
            public Optional<EC2MetadataUtils.IAMSecurityCredential> load(AssumeRoleRequest assumeRoleRequest) {
                return assumeRole(assumeRoleRequest);
            }
        });

    synchronized static AWSSecurityTokenService getStsClient() {
        try {
            if (region == null) {
                region = Regions.getCurrentRegion();
            }
        } catch (Exception e){
            log.info("Could not determine the AWS region!");
        }
        if (stsClient == null) {
            if (region != null) {
                String endpoint = String.format("https://sts.%s.amazonaws.com", region.getName());
                log.info("Running the application with regional STS endpoint " + endpoint);
                stsClient = AWSSecurityTokenServiceClientBuilder
                    .standard()
                    .withEndpointConfiguration(new EndpointConfiguration(endpoint, region.getName()))
                    .build();
            } else {
                log.info("Running the application with global STS endpoint.");
                stsClient = AWSSecurityTokenServiceClientBuilder
                    .standard()
                    .build();
            }
        }
        return stsClient;
    }

    /**
     * Create an instance of SimpleDataFormat.
     * SimpleDateFormat is not thread safe, so we create an instance when needed instead of using a shared one
     *
     * @return
     */
    static SimpleDateFormat createInterceptorDateTimeFormat() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<EC2MetadataUtils.IAMSecurityCredential> getUserCredentials(AssumeRoleRequest assumeRoleRequest) {
        log.debug("Request to assume role {} with STS", assumeRoleRequest);
        Optional<EC2MetadataUtils.IAMSecurityCredential> credentials = credentialsCache.getUnchecked(assumeRoleRequest);

        if (credentials.isPresent() && shouldRefresh(credentials.get())) {
            // TODO: we should consider using Caffeine which provides ttl at item level
            log.debug("Invalidating the cache for assume role {}", assumeRoleRequest);
            /*
             * In case of multiple threads reaching here, we should be alright as locking is at
             * segment level for both invalidate() and get() calls.
             */
            credentialsCache.invalidate(assumeRoleRequest);
            credentials = credentialsCache.getUnchecked(assumeRoleRequest);
        }
        return credentials;
    }

    /**
     * Makes actual call to STS.
     *
     * @param assumeRoleRequest the request to assume
     * @return an {@code Optional} containing {@link EC2MetadataUtils.IAMSecurityCredential}
     */
    private Optional<EC2MetadataUtils.IAMSecurityCredential> assumeRole(AssumeRoleRequest assumeRoleRequest) {
        log.info("Need to assume role {} with STS", assumeRoleRequest);
        try {
            AssumeRoleResult assumeRoleResult = getStsClient().assumeRole(assumeRoleRequest);
            EC2MetadataUtils.IAMSecurityCredential credentials = createIAMSecurityCredential(assumeRoleResult.getCredentials());
            log.debug("Procured credentials from STS for assume role {}", assumeRoleRequest);
            return Optional.of(credentials);
        } catch (AmazonServiceException ase) {
            // This is an internal server error.
            log.error("AWS Service exception {}", ase.getErrorMessage(), ase);
            throw ase;
        } catch (AmazonClientException ace) {
            log.error("AWS Client exception {}", ace.getMessage(), ace);
        }
        return Optional.empty();
    }

    private EC2MetadataUtils.IAMSecurityCredential createIAMSecurityCredential(Credentials credentials) {
        EC2MetadataUtils.IAMSecurityCredential iamCredential = new EC2MetadataUtils.IAMSecurityCredential();
        iamCredential.accessKeyId = credentials.getAccessKeyId();
        iamCredential.secretAccessKey = credentials.getSecretAccessKey();
        iamCredential.token = credentials.getSessionToken();
        iamCredential.code = "Success";
        iamCredential.type = "AWS-HMAC";
        iamCredential.expiration = createInterceptorDateTimeFormat().format(credentials.getExpiration());

        long nowTs = System.currentTimeMillis();
        Date now = new Date(nowTs);
        iamCredential.lastUpdated = createInterceptorDateTimeFormat().format(now);
        return iamCredential;
    }

    /**
     * Determines if we need to refresh the cached credentials.
     * <p>
     * The credentials are refreshed if we don't have any cached credentials, or if the
     * current time +
     * {@link STSCredentialsProvider#MIN_REMAINING_TIME_TO_REFRESH_CREDENTIALS} + some random time in range
     * [0, {@link STSCredentialsProvider#MAX_RANDOM_TIME_TO_REFRESH_CREDENTIALS}) is
     * greater than the expiration of cached credentials.
     *
     * @param credentials the cached credentials
     * @return {@code true} if we need to assume role with STS, else {@code false}
     */
    private boolean shouldRefresh(EC2MetadataUtils.IAMSecurityCredential credentials) {
        try {
            Date expirationDate = createInterceptorDateTimeFormat().parse(credentials.expiration);
            return getRandomTimeInRange() + System.currentTimeMillis() > expirationDate.getTime();
        } catch (ParseException ex) {
            log.error("Unable to parse the expiration in the cached assume role credentials. Refreshing credentials anyway.", ex);
            return true;
        }
    }

    @VisibleForTesting
    public long getRandomTimeInRange() {
        long minTimeMs = MIN_REMAINING_TIME_TO_REFRESH_CREDENTIALS.toMillis();
        long maxRandomTimeMs = MAX_RANDOM_TIME_TO_REFRESH_CREDENTIALS.toMillis();

        return minTimeMs + ThreadLocalRandom.current().nextLong(maxRandomTimeMs);
    }
}
