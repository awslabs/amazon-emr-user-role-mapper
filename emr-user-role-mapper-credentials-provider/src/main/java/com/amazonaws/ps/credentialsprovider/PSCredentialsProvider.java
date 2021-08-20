package com.amazonaws.ps.credentialsprovider;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.PSCredentialsFetcher;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;

public class PSCredentialsProvider implements AWSCredentialsProvider
{
    private static final Log LOG = LogFactory.getLog(PSCredentialsProvider.class);

    final private PSCredentialsFetcher psCredentialsFetcher;

    /**
     * Default constructor. This constructor will get the current user from the {@link UserGroupInformation} and
     * uses the default allowed users list.
     */
    public PSCredentialsProvider()
    {
        this(new PSCredentialsFetcher());
    }

    public PSCredentialsProvider(Configuration configuration) {
        //TODO
        this(new PSCredentialsFetcher());
    }
    /**
     * This constructor takes all members as an argument for testing purposes.
     * @param psCredentialsFetcher
     */
    @VisibleForTesting
    PSCredentialsProvider(PSCredentialsFetcher psCredentialsFetcher)
    {
        Preconditions.checkNotNull(psCredentialsFetcher);

        LOG.debug("Building PSCredentials Provider.");
        this.psCredentialsFetcher = psCredentialsFetcher;
    }



    /**
     * Gets credentials if the real user is allowed to get credentials for an impersonated user.
     *
     * @return AWSCredentials if user is allowed to impersonate, null otherwise.
     */
    @Override
    public AWSCredentials getCredentials()
    {
        if (LOG.isDebugEnabled()) {
            LOG.debug("I am impersonating user using the cerificate");
        }

        try {
            return psCredentialsFetcher.getCredentials();
        } catch (Exception e) {
            LOG.error("Failed to get credentials using mTLS", e);
            throw e;
        }
    }

    @Override
    public void refresh()
    {
        LOG.debug("Starting to Refreshing Credentials.");
        psCredentialsFetcher.refresh();
        LOG.debug("Finished to Refreshing Credentials.");
    }

}
