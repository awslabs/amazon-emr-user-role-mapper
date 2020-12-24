package com.amazonaws.auth;

import com.amazonaws.internal.EC2ResourceFetcher;
import com.amazonaws.internal.InstanceMetadataServiceResourceFetcher;
import com.amazonaws.retry.internal.CredentialsEndpointRetryParameters;
import com.amazonaws.retry.internal.CredentialsEndpointRetryPolicy;
import com.google.common.annotations.VisibleForTesting;

import java.net.URI;

/**
 * This class reuses the {@link BaseCredentialsFetcher} from AWS CLI because it takes care for us:
 * 1) refreshing credentials automatically when they are close to expiration
 * 2) parses of the output for credentials and returns the appropriate credentials
 * 3) makes sure that we conform to the behaviour of the existing instance profile credentials provider.
 * <p>
 * This class needed to be in this namespace as the class is package private.
 */
public class URMCredentialsFetcher
        extends BaseCredentialsFetcher
        implements CredentialsEndpointRetryPolicy
{
    final private static String IMPERSONATION_PATH = "http://localhost:9944/latest/meta-data/iam/security-credentials/impersonation/";
    private URI impersonationURI;

    private final EC2ResourceFetcher resourceFetcher;

    public URMCredentialsFetcher(String user)
    {
        this(user, InstanceMetadataServiceResourceFetcher.getInstance());
    }

    @VisibleForTesting
    URMCredentialsFetcher(String user, EC2ResourceFetcher ec2ResourceFetcher)
    {
        this.impersonationURI = URI.create(IMPERSONATION_PATH + user);
        this.resourceFetcher = ec2ResourceFetcher;
    }

    public void setImpersonationUser(String user) {
        this.impersonationURI = URI.create(IMPERSONATION_PATH + user);
    }

    @Override
    protected String getCredentialsResponse()
    {
        return resourceFetcher.readResource(impersonationURI, this);
    }

    @Override
    public String toString()
    {
        return "URMCredentialsFetcher";
    }

    @Override
    public boolean shouldRetry(int retriesAttempted, CredentialsEndpointRetryParameters retryParams)
    {
        return false;
    }
}