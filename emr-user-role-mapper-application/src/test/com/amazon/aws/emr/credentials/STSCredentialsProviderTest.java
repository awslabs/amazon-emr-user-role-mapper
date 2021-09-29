// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.credentials;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.Is.is;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.util.EC2MetadataUtils;
import java.util.Date;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({STSCredentialsProvider.class})
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*"})
public class STSCredentialsProviderTest {

  private static final long ONE_HR_MS = 60 * 60 * 1000;

  private static final long TWO_MIN_MS = 2 * 60 * 1000;

  @Mock
  STSClient stsClient;

  @InjectMocks
  STSCredentialsProvider stsCredentialsProvider;

  AssumeRoleRequest assumeRoleRequest;

  @Before
  public void setup() {
    mockStatic(STSCredentialsProvider.class);
    Mockito.when(STSCredentialsProvider.createInterceptorDateTimeFormat()).thenCallRealMethod();
    assumeRoleRequest = createTestAssumeRoleRequest();
  }

  @Test
  public void get_credentials() {
    Credentials longLivedCredentials = createTestCredentials(ONE_HR_MS);
    Mockito.when(stsClient.assumeRole(assumeRoleRequest)).thenReturn(
        new AssumeRoleResult()
            .withCredentials(longLivedCredentials));
    Optional<EC2MetadataUtils.IAMSecurityCredential> optionalIAMSecurityCredentials = stsCredentialsProvider
        .getUserCredentials(assumeRoleRequest);
    assertThat(optionalIAMSecurityCredentials.isPresent(), is(true));
    EC2MetadataUtils.IAMSecurityCredential iamSecurityCredential = optionalIAMSecurityCredentials
        .get();
    assertThat(iamSecurityCredential.accessKeyId, is("test-access"));
    assertThat(iamSecurityCredential.secretAccessKey, is("test-secret"));
    assertThat(iamSecurityCredential.code, is("Success"));
    assertThat(iamSecurityCredential.code, is("Success"));
    assertThat(iamSecurityCredential.expiration, is(
        STSCredentialsProvider.createInterceptorDateTimeFormat()
            .format(longLivedCredentials.getExpiration())));
  }

  @Test
  public void get_cached_credentials() {
    Credentials longLivedCredentials = createTestCredentials(ONE_HR_MS);
    Mockito.when(stsClient.assumeRole(assumeRoleRequest)).thenReturn(
        new AssumeRoleResult()
            .withCredentials(longLivedCredentials));

    stsCredentialsProvider.getUserCredentials(assumeRoleRequest);
    Mockito.verify(stsClient, Mockito.times(1)).assumeRole(assumeRoleRequest);
    // Make the second call and there should no additional Mock invocation
    stsCredentialsProvider.getUserCredentials(assumeRoleRequest);
    Mockito.verify(stsClient, Mockito.times(1)).assumeRole(assumeRoleRequest);
  }

  @Test
  public void expired_credentials() {
    Credentials shortLivedTestCredentials = createTestCredentials(-1);
    Mockito.when(stsClient.assumeRole(assumeRoleRequest)).thenReturn(
        new AssumeRoleResult()
            .withCredentials(shortLivedTestCredentials));
    stsCredentialsProvider.getUserCredentials(assumeRoleRequest);
    /*
     * Why 2?
     * First call gets the credentials using sts as the cache is empty.
     * Second call is made to STS as the retrieved credentials are expired.
     */
    Mockito.verify(stsClient, Mockito.times(2)).assumeRole(assumeRoleRequest);

    // Make the second call and there should no another Mock invocation
    stsCredentialsProvider.getUserCredentials(assumeRoleRequest);
    Mockito.verify(stsClient, Mockito.times(3)).assumeRole(assumeRoleRequest);

    // Make second call, should invoke STS client again
    stsCredentialsProvider.getUserCredentials(assumeRoleRequest);
    PowerMockito.verifyStatic(STSCredentialsProvider.class, Mockito.times(3));
  }

  @Test
  public void about_to_expire_credentials() {
    Credentials shortLivedTestCredentials = createTestCredentials(TWO_MIN_MS);
    Mockito.when(stsClient.assumeRole(assumeRoleRequest)).thenReturn(
        new AssumeRoleResult()
            .withCredentials(shortLivedTestCredentials));
    stsCredentialsProvider.getUserCredentials(assumeRoleRequest);
    Mockito.verify(stsClient, Mockito.times(2)).assumeRole(assumeRoleRequest);

    // Make the second call and there should no another Mock invocation
    stsCredentialsProvider.getUserCredentials(assumeRoleRequest);
    Mockito.verify(stsClient, Mockito.times(3)).assumeRole(assumeRoleRequest);

    // Make second call, should invoke STS client again
    stsCredentialsProvider.getUserCredentials(assumeRoleRequest);
    PowerMockito.verifyStatic(STSCredentialsProvider.class, Mockito.times(3));
  }

  @Test
  public void random_refresh_time() {
    assertThat(stsCredentialsProvider.getRandomTimeInRange(), allOf(
        greaterThan(STSCredentialsProvider.MIN_REMAINING_TIME_TO_REFRESH_CREDENTIALS.toMillis()),
        lessThan(STSCredentialsProvider.MIN_REMAINING_TIME_TO_REFRESH_CREDENTIALS.toMillis() +
            STSCredentialsProvider.MAX_RANDOM_TIME_TO_REFRESH_CREDENTIALS.toMillis())));
  }

  private Credentials createTestCredentials(long xp) {
    return new Credentials().withAccessKeyId("test-access")
        .withSecretAccessKey("test-secret")
        .withSessionToken("test-session")
        .withExpiration(new Date(System.currentTimeMillis() + xp));
  }


  private AssumeRoleRequest createTestAssumeRoleRequest() {
    return new AssumeRoleRequest()
        .withRoleArn("test-arn")
        .withRoleSessionName("test-session");
  }
}
