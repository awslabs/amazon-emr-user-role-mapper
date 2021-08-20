package com.amazonaws.ps.credentialsprovider;

import com.amazonaws.auth.*;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import org.apache.hadoop.conf.Configuration;

public class PSCredentialsProviderChain extends AWSCredentialsProviderChain {

    /**
     * Default constructor that provides the original DefaultCredentialsProviderChain with PSCredentialsProvider as the
     * first provider.
     */
    public PSCredentialsProviderChain() {
        super(new PSCredentialsProvider(),
                InstanceProfileCredentialsProvider.getInstance(),
                new EnvironmentVariableCredentialsProvider(),
                new SystemPropertiesCredentialsProvider(),
                new ProfileCredentialsProvider(),
                new EC2ContainerCredentialsProviderWrapper());
    }

    /**
     * Constructor that utilizes configuration information. It provides the original DefaultCredentialsProviderChain
     * with PSCredentialsProvider as the first provider.
     */
    public PSCredentialsProviderChain(Configuration configuration) {
        super(new PSCredentialsProvider(configuration),
                InstanceProfileCredentialsProvider.getInstance(),
                new EnvironmentVariableCredentialsProvider(),
                new SystemPropertiesCredentialsProvider(),
                new ProfileCredentialsProvider(),
                new EC2ContainerCredentialsProviderWrapper());
    }
}
