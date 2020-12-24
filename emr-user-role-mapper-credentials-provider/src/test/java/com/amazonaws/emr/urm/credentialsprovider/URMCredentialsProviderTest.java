package com.amazonaws.emr.urm.credentialsprovider;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.URMCredentialsFetcher;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(UserGroupInformation.class)
public class URMCredentialsProviderTest
{
    public static final String USER = "a_user";
    public static final String REALUSER = "realuser";

    @Test
    public void test_nonimpersonation()
    {
        URMCredentialsFetcher mockURMCredentialsFetcher = mock(URMCredentialsFetcher.class);
        URMCredentialsProvider urmCredentialsProvider = new URMCredentialsProvider(mockURMCredentialsFetcher,
                new HashSet<>(Arrays.asList("user1", "user2")), USER, REALUSER);
        assertNull(urmCredentialsProvider.getCredentials());
    }

    @Test
    public void test_gettingCredentials() throws IOException
    {
        // Mock out  URMCredentialsFetcher
        URMCredentialsFetcher mockURMCredentialsFetcher = mock(URMCredentialsFetcher.class);
        BasicSessionCredentials basicSessionCredentials = new BasicSessionCredentials("accesskey", "secretKey", "sessionToken");
        when(mockURMCredentialsFetcher.getCredentials()).thenReturn(basicSessionCredentials);

        // Mock out UGI for the impersonated user, and the real user
        UserGroupInformation mockUgi = mock(UserGroupInformation.class);
        when(mockUgi.getShortUserName()).thenReturn(USER);

        UserGroupInformation mockRealUserUGI = mock(UserGroupInformation.class);
        when(mockUgi.getRealUser()).thenReturn(mockRealUserUGI);
        when(mockRealUserUGI.getShortUserName()).thenReturn(REALUSER);

        // Mock out UGI singleton
        PowerMockito.mockStatic(UserGroupInformation.class);
        BDDMockito.given(UserGroupInformation.getCurrentUser()).willReturn(mockUgi);
        when(mockUgi.getRealUser()).thenReturn(mockRealUserUGI);

        Set<String> allowedList = new HashSet<>(Collections.singletonList(REALUSER));
        URMCredentialsProvider urmCredentialsProvider = new URMCredentialsProvider(mockURMCredentialsFetcher,
                allowedList, USER, REALUSER);

        AWSCredentials returnedCreds = urmCredentialsProvider.getCredentials();

        assertNotNull(returnedCreds);
        assertTrue(returnedCreds instanceof BasicSessionCredentials);
        assertEquals(basicSessionCredentials.getAWSAccessKeyId(), returnedCreds.getAWSAccessKeyId());
        assertEquals(basicSessionCredentials.getAWSSecretKey(), returnedCreds.getAWSSecretKey());
        assertEquals(basicSessionCredentials.getSessionToken(), ((BasicSessionCredentials) returnedCreds).getSessionToken());
    }

    @Test
    public void test_AllowListFromConfiguration()
    {
        Configuration mockConfiguration = mock(Configuration.class);
        when(mockConfiguration.getTrimmedStrings(URMCredentialsProvider.EMRFS_SITE_CONF_ALLOWED_USERS))
                .thenReturn(new String[] {"user1", "user2"});

        Set<String> users = URMCredentialsProvider.getUsersAllowedToImpersonate(mockConfiguration);

        assertNotNull(users);
        assertEquals(2, users.size());
        assertTrue(users.contains("user1"));
        assertTrue(users.contains("user2"));
    }
}
