package com.amazonaws.emr.urm.credentialsprovider;

import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import org.apache.hadoop.conf.Configuration;

import java.net.URI;

/**
 * Provider Chain that adds URM Credentials Provider.
 */
public class URMCredentialsProviderChain
        extends AWSCredentialsProviderChain
{

    /**
     * Default constructor that provides the original DefaultCredentialsProviderChain with URMCredentialsProvider as the
     * first provider.
     */
    public URMCredentialsProviderChain()
    {
        super(new URMCredentialsProvider(),
                InstanceProfileCredentialsProvider.getInstance(),
                new EnvironmentVariableCredentialsProvider(),
                new SystemPropertiesCredentialsProvider(),
                new ProfileCredentialsProvider(),
                new EC2ContainerCredentialsProviderWrapper());
    }

    /**
     * Constructor that EMRFS calls and passes in configuration information. It provides the original DefaultCredentialsProviderChain
     * with URMCredentialsProvider as the first provider.
     */
    public URMCredentialsProviderChain(URI uri, Configuration configuration)
    {
        super(new URMCredentialsProvider(configuration),
                InstanceProfileCredentialsProvider.getInstance(),
                new EnvironmentVariableCredentialsProvider(),
                new SystemPropertiesCredentialsProvider(),
                new ProfileCredentialsProvider(),
                new EC2ContainerCredentialsProviderWrapper());
    }
}
