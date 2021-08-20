package com.amazonaws.auth;

import com.amazonaws.internal.EC2ResourceFetcher;
import com.amazonaws.internal.InstanceMetadataServiceResourceFetcher;
import com.amazonaws.retry.internal.CredentialsEndpointRetryParameters;
import com.amazonaws.retry.internal.CredentialsEndpointRetryPolicy;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.URI;

/**
 * This class reuses the {@link BaseCredentialsFetcher} from AWS CLI because it takes care for us:
 * 1) refreshing credentials automatically when they are close to expiration
 * 2) parses of the output for credentials and returns the appropriate credentials
 * 3) makes sure that we conform to the behaviour of the existing instance profile credentials provider.
 * <p>
 * This class needed to be in this namespace as the class is package private.
 */
public class PSCredentialsFetcher
        extends BaseCredentialsFetcher
        implements CredentialsEndpointRetryPolicy
{
    private static final Log LOG = LogFactory.getLog(PSCredentialsFetcher.class);

    //TODO: use property config to dynamically create the URI path
    final private static String PERMISSION_SERVICE_PATH = "http://localhost:9944/latest/meta-data/iam/security-credentials/ps/";
    private URI permissionServiceURI;

    private final EC2ResourceFetcher resourceFetcher;

    public PSCredentialsFetcher()
    {
        this(InstanceMetadataServiceResourceFetcher.getInstance());
    }

    @VisibleForTesting
    PSCredentialsFetcher(EC2ResourceFetcher ec2ResourceFetcher)
    {
        this.resourceFetcher = ec2ResourceFetcher;
        this.permissionServiceURI = URI.create(PERMISSION_SERVICE_PATH);
    }

    @Override
    protected String getCredentialsResponse()
    {
        if (LOG.isDebugEnabled()) {
            LOG.debug("PSCredentialsFetcher: Calling URI: " + permissionServiceURI.toASCIIString());
        }
        return resourceFetcher.readResource(permissionServiceURI, this);
    }

    @Override
    public String toString()
    {
        return "PermissionServiceCredentialsFetcher";
    }

    @Override
    public boolean shouldRetry(int retriesAttempted, CredentialsEndpointRetryParameters retryParams)
    {
        return false;
    }
}