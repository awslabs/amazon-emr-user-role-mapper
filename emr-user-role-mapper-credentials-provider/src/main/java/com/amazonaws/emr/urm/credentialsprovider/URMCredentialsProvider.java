package com.amazonaws.emr.urm.credentialsprovider;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.URMCredentialsFetcher;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * This class will get credentials from the URM process if the current user is configured
 * to impersonate the user in the UGI session. This is useful for applications like Presto and
 * Hive.
 */
public class URMCredentialsProvider
        implements AWSCredentialsProvider
{
    static final String EMRFS_SITE_CONF_ALLOWED_USERS = "urm.credentialsprovider.impersonation.users";
    private static final Log LOG = LogFactory.getLog(URMCredentialsProvider.class);
    private static final Set<String> DEFAULT_ALLOWED_USERS = new HashSet<>(Arrays.asList("presto", "hive"));
    private final Set<String> usersAllowedToImpersonate;

    private final String createdBy;
    private final String createdForUser;

    final private URMCredentialsFetcher urmCredentialsFetcher;

    /**
     * Default constructor. This constructor will get the current user from the {@link UserGroupInformation} and
     * uses the default allowed users list.
     */
    public URMCredentialsProvider()
    {
        this(new URMCredentialsFetcher(getUgi().getShortUserName()),
                DEFAULT_ALLOWED_USERS,
                getUgi().getShortUserName(),
                getUgi().getRealUser().getShortUserName()
                );
    }

    /**
     * This constructor takes a {@link Configuration} and gets the list of users that can impersonate other users.
     * It will also use the {@link UserGroupInformation} to get the current user.
     * @param configuration The configuration object that can be passed in to read from.
     */
    public URMCredentialsProvider(Configuration configuration)
    {
        this(new URMCredentialsFetcher(getUgi().getShortUserName()),
                getUsersAllowedToImpersonate(configuration),
                getUgi().getShortUserName(),
                getUgi().getRealUser().getShortUserName());
    }

    /**
     * This constructor takes all members as an argument for testing purposes.
     * @param urmCredentialsFetcher
     * @param usersAllowedToImpersonate
     */
    @VisibleForTesting
    URMCredentialsProvider(URMCredentialsFetcher urmCredentialsFetcher, Set<String> usersAllowedToImpersonate,
            String createdForUser, String realUser)
    {
        Preconditions.checkNotNull(urmCredentialsFetcher);
        Preconditions.checkNotNull(usersAllowedToImpersonate);

        LOG.debug("Building URMCredentials Provider.");
        this.urmCredentialsFetcher = urmCredentialsFetcher;
        this.usersAllowedToImpersonate = usersAllowedToImpersonate;
        this.createdBy = realUser;
        this.createdForUser = createdForUser;
    }

    /**
     * Gets credentials if the real user is allowed to get credentials for an impersonated user.
     *
     * @return AWSCredentials if user is allowed to impersonate, null otherwise.
     */
    @Override
    public AWSCredentials getCredentials()
    {
        if (!usersAllowedToImpersonate.contains(createdBy)) {
            //not going to use impersonation for this.
            if (LOG.isDebugEnabled()) {
                LOG.debug("Not Impersonating as I am: " + createdBy);
            }
            //Return null which will then let the existing credentials provider chain to get credentials
            return null;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("I am impersonating user: " + createdForUser);
        }
        String callingUser = getUgi().getShortUserName();
        //Put in a safety check here to make sure that this object is created and used. This is to ensure that
        //credentials leak doesn't happen.
        if (!callingUser.equalsIgnoreCase(createdForUser) && usersAllowedToImpersonate.contains(callingUser)) {
            final String errmsg = "Current user is different than calling user! CallingUser: " + callingUser + " CurrentUser: " + createdForUser;
            LOG.error(errmsg);
            throw new RuntimeException(errmsg);
        }
        return urmCredentialsFetcher.getCredentials();
    }

    @Override
    public void refresh()
    {
        LOG.debug("Starting to Refreshing Credentials.");
        urmCredentialsFetcher.refresh();
        LOG.debug("Finished to Refreshing Credentials.");
    }

    @VisibleForTesting
    static Set<String> getUsersAllowedToImpersonate(Configuration configuration)
    {
        String[] allowedUsers = configuration.getTrimmedStrings(EMRFS_SITE_CONF_ALLOWED_USERS);
        if (allowedUsers != null) {
            return new HashSet<>(Arrays.asList(allowedUsers));
        }
        else {
            return DEFAULT_ALLOWED_USERS;
        }
    }

    private static UserGroupInformation getUgi()
    {
        try {
            UserGroupInformation ugi = UserGroupInformation.getCurrentUser();
            if (LOG.isDebugEnabled()) {
                LOG.debug("UGI Returned user : " + ugi.getShortUserName() + " Real User: " + ugi.getRealUser() + " AuthN Method: " + ugi.getAuthenticationMethod());
            }
            return ugi;
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to get UGI of the current user.", e);
        }
    }
}
