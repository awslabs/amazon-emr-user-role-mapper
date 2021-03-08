package org.sfdc.uip.hive.ql.security.authorization;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import org.apache.hadoop.hive.metastore.api.Database;
import org.apache.hadoop.hive.ql.metadata.AuthorizationException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.security.HiveAuthenticationProvider;
import org.apache.hadoop.hive.ql.security.authorization.Privilege;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAccessControlException;

import org.eclipse.jetty.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.security.AccessControlException;

import static junit.framework.Assert.fail;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest({AmazonS3ClientBuilder.class})
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*"})
public class S3StorageBasedAuthorizationProviderTest
{
    public static final String USER = "someuser";
    private static final String UPLOAD_ID = "UPLOAD_ID";

    @Mock
    AmazonS3ClientBuilder clientBuilder;

    @Mock
    AmazonS3 s3Client;

    @Mock
    HiveAuthenticationProvider mockHiveAuthenticationProvider;

    @Mock
    URMCredentialsRetriever mockURMCredentialsRetriever;

    @Mock
    AWSCredentials mockCredentials;

    @Captor
    ArgumentCaptor<InitiateMultipartUploadRequest> s3InitiateUploadRequestCaptor;

    @Captor
    ArgumentCaptor<AbortMultipartUploadRequest> s3AbortUploadRequestCaptor;

    S3StorageBasedAuthorizationProvider provider;

    @Before
    public void setUp() {
        mockStatic(AmazonS3ClientBuilder.class);

        //AWS S3 Client Builder mocking
        when(AmazonS3ClientBuilder.standard()).thenReturn(clientBuilder);
        when(clientBuilder.withCredentials(any(AWSCredentialsProvider.class))).thenReturn(clientBuilder);
        when(clientBuilder.build()).thenReturn(s3Client);

        when(mockHiveAuthenticationProvider.getUserName()).thenReturn(USER);
        when(mockURMCredentialsRetriever.getCredentialsForUser(eq(USER)))
                .thenReturn(mockCredentials);

        provider = new S3StorageBasedAuthorizationProvider(mockURMCredentialsRetriever);
        provider.setAuthenticator(mockHiveAuthenticationProvider);
    }

    @Test
    public void test_privilegeCheckOnTable_read_write_privileges()
            throws HiveException
    {
        Database mockDatabase = mock(Database.class);
        when(mockDatabase.getLocationUri()).thenReturn("s3://somebucket/somePrefix");

        Privilege[] readPrivileges = new Privilege[] {Privilege.SELECT};
        Privilege[] writePrivileges = new Privilege[] {Privilege.ALTER_DATA};

        InitiateMultipartUploadResult response = mock(InitiateMultipartUploadResult.class);
        when(response.getUploadId()).thenReturn(UPLOAD_ID);
        when(s3Client.initiateMultipartUpload(any())).thenReturn(response);

        provider.authorize(mockDatabase, readPrivileges, writePrivileges);

        verify(s3Client).initiateMultipartUpload(s3InitiateUploadRequestCaptor.capture());
        verify(s3Client).abortMultipartUpload(s3AbortUploadRequestCaptor.capture());

        assertEquals(s3InitiateUploadRequestCaptor.getValue().getBucketName(), "somebucket");
        assertTrue(s3InitiateUploadRequestCaptor.getValue().getKey().startsWith("somePrefix/"));

        assertEquals(s3AbortUploadRequestCaptor.getValue().getBucketName(), "somebucket");
        assertTrue(s3AbortUploadRequestCaptor.getValue().getKey().startsWith("somePrefix/"));
        assertEquals(s3AbortUploadRequestCaptor.getValue().getUploadId(), UPLOAD_ID);
    }

    @Test(expected = AuthorizationException.class)
    public void test_unauthorized_s3_returns_403() throws HiveException
    {
        Database mockDatabase = mock(Database.class);
        when(mockDatabase.getLocationUri()).thenReturn("s3://somebucket/somePrefix");

        Privilege[] readPrivileges = new Privilege[] {};
        Privilege[] writePrivileges = new Privilege[] {Privilege.ALTER_DATA};

        InitiateMultipartUploadResult response = mock(InitiateMultipartUploadResult.class);
        when(response.getUploadId()).thenReturn(UPLOAD_ID);

        AmazonServiceException ase = new AmazonS3Exception("Access Denied");
        ase.setErrorCode("AccessDenied");
        ase.setErrorType(AmazonServiceException.ErrorType.Client);
        ase.setStatusCode(HttpStatus.FORBIDDEN_403);

        when(s3Client.initiateMultipartUpload(any())).thenThrow(ase);

        try {
            provider.authorize(mockDatabase, readPrivileges, writePrivileges);
        } catch (AccessControlException ace) {
            verify(s3Client).initiateMultipartUpload(s3InitiateUploadRequestCaptor.capture());
            assertEquals(s3InitiateUploadRequestCaptor.getValue().getBucketName(), "somebucket");
            assertTrue(s3InitiateUploadRequestCaptor.getValue().getKey().startsWith("somePrefix/"));

            throw ace;
        }
    }

    @Test(expected = HiveAccessControlException.class)
    public void test_index_operation() throws HiveException
    {
        Database mockDatabase = mock(Database.class);
        when(mockDatabase.getLocationUri()).thenReturn("s3://somebucket/somePrefix");

        Privilege[] readPrivileges = new Privilege[] {Privilege.INDEX};
        Privilege[] writePrivileges = new Privilege[] {};

        provider.authorize(mockDatabase, readPrivileges, writePrivileges);
    }

    @Test(expected = HiveAccessControlException.class)
    public void test_lock_operation() throws HiveException
    {
        Database mockDatabase = mock(Database.class);
        when(mockDatabase.getLocationUri()).thenReturn("s3://somebucket/somePrefix");

        Privilege[] readPrivileges = new Privilege[] {Privilege.LOCK};
        Privilege[] writePrivileges = new Privilege[] {};

        provider.authorize(mockDatabase, readPrivileges, writePrivileges);
    }

}
