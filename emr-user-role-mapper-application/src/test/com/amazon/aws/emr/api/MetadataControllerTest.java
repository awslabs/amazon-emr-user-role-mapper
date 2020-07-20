// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.api;

import com.amazon.aws.emr.ApplicationConfiguration;
import com.amazon.aws.emr.common.TestConstants;
import com.amazon.aws.emr.common.system.PrincipalResolver;
import com.amazon.aws.emr.common.system.user.TestCommandBasedPrincipalResolver;
import com.amazon.aws.emr.credentials.MetadataCredentialsProvider;
import com.amazon.aws.emr.credentials.TestMetadataCredentialsProvider;
import com.amazon.aws.emr.mapping.MappingInvoker;
import com.amazon.aws.emr.common.system.user.LinuxUserIdService;
import com.amazon.aws.emr.common.system.user.UserIdService;
import com.amazon.aws.emr.ws.ImmediateFeature;
import com.amazonaws.util.EC2MetadataUtils;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.glassfish.hk2.api.Immediate;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.test.DeploymentContext;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.ServletDeploymentContext;
import org.glassfish.jersey.test.TestProperties;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.inject.Singleton;
import javax.ws.rs.client.WebTarget;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.collection.IsArrayWithSize.arrayWithSize;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest({EC2MetadataUtils.class})
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*"})
public class MetadataControllerTest extends JerseyTest {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
            .setPrettyPrinting()
            .create();
    private static final List<String> RESOURCES_UNDER_IAM = Collections.unmodifiableList(
            Arrays.asList("info", "security-credentials/"));

    @Mock
    LinuxUserIdService osUserIdentificationService;

    @Override
    protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
        return new GrizzlyWebTestContainerFactory();
    }

    @Override
    protected DeploymentContext configureDeployment() {
        forceSet(TestProperties.CONTAINER_PORT, "0"); // avoid BindException - choose a new port every time
        ResourceConfig config = new ResourceConfig(MetadataController.class);
        config.register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(osUserIdentificationService).to(UserIdService.class);
                bind(TestCommandBasedPrincipalResolver.class).to(PrincipalResolver.class).in(Singleton.class);
                bind(MappingInvoker.class).to(MappingInvoker.class).in(Singleton.class);
                bind(TestMetadataCredentialsProvider.class).to(MetadataCredentialsProvider.class).in(Singleton.class);
                bind(ApplicationConfiguration.class).to(ApplicationConfiguration.class).in(Immediate.class);
            }
        });
        config.register(RequestFilter.class);
        config.register(ImmediateFeature.class);
        return ServletDeploymentContext.forServlet(new ServletContainer(config)).build();
    }

    /**
     * The following set up exists
     * User U1 with UID 503 is mapped to Username mapped Role U1
     * User U2 with UID 504 is mapped to Username mapped Role U2
     * User U3 with UID 505 is mapped to nothing.
     * User U4 with UID 506 is mapped to Group mapped Role G1.
     */
    @Before
    public void setup() {
        mockStatic(EC2MetadataUtils.class);
    }

    @Test
    public void list_mapped_user_roles() {
        when(osUserIdentificationService.resolveSystemUID
                (Mockito.anyString(), Mockito.anyInt(), Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(OptionalInt.of(TestConstants.USER1_UID));
        WebTarget target = target(MetadataController.LATEST_IAM_CREDENTIALS_ROOT_PATH);
        String actualRoleName = target.request().get(String.class);
        assertThat(actualRoleName, is(TestConstants.USER1_ROLE_NAME));
    }

    // This is to test cases when we many not be able to resolve caller's user id
    @Test
    public void list_empty_userid_roles() {
        when(osUserIdentificationService.resolveSystemUID
                (Mockito.anyString(), Mockito.anyInt(), Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(OptionalInt.empty());
        WebTarget target = target(MetadataController.LATEST_IAM_CREDENTIALS_ROOT_PATH);
        String actualRoleName = target.request().get(String.class);
        assertThat(actualRoleName, is(TestConstants.EMPTY_ROLE_NAME));
    }

    // This can happen when a user has just been deleted after the request is made.
    @Test
    public void list_unknown_username_roles() {
        when(osUserIdentificationService.resolveSystemUID
                (Mockito.anyString(), Mockito.anyInt(), Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(OptionalInt.of(TestConstants.UNKNOWN_USERNAME_UID));
        WebTarget target = target(MetadataController.LATEST_IAM_CREDENTIALS_ROOT_PATH);
        String actualRoleName = target.request().get(String.class);
        assertThat(actualRoleName, is(TestConstants.EMPTY_ROLE_NAME));
    }

    @Test
    public void list_unmapped_username_roles() {
        when(osUserIdentificationService.resolveSystemUID
                (Mockito.anyString(), Mockito.anyInt(), Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(OptionalInt.of(TestConstants.UNMAPPED_UID));
        WebTarget target = target(MetadataController.LATEST_IAM_CREDENTIALS_ROOT_PATH);
        String actualRoleName = target.request().get(String.class);
        assertThat(actualRoleName, is(TestConstants.EMPTY_ROLE_NAME));
    }

    @Test
    public void get_mapped_user_role_credentials() {
        when(osUserIdentificationService.resolveSystemUID
                (Mockito.anyString(), Mockito.anyInt(), Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(OptionalInt.of(TestConstants.USER1_UID));
        WebTarget target = target(MetadataController.LATEST_IAM_CREDENTIALS_ROOT_PATH + TestConstants.USER1_ROLE_NAME);
        String actualCredentials = target.request().get(String.class);
        assertCorrectCredentials(actualCredentials);
    }

    @Test
    public void get_empty_userid_role_credentials() {
        when(osUserIdentificationService.resolveSystemUID
                (Mockito.anyString(), Mockito.anyInt(), Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(OptionalInt.empty());
        WebTarget target = target(MetadataController.LATEST_IAM_CREDENTIALS_ROOT_PATH + TestConstants.USER1_ROLE_NAME);
        String actualCredentials = target.request().get(String.class);
        assertThat(actualCredentials, is(TestConstants.EMPTY_ROLE_CREDENTIALS));
    }

    @Test
    public void get_unknown_username_role_credentials() {
        when(osUserIdentificationService.resolveSystemUID
                (Mockito.anyString(), Mockito.anyInt(), Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(OptionalInt.of(TestConstants.UNKNOWN_USERNAME_UID));
        WebTarget target = target(MetadataController.LATEST_IAM_CREDENTIALS_ROOT_PATH + TestConstants.USER1_ROLE_NAME);
        String actualCredentials = target.request().get(String.class);
        assertThat(actualCredentials, is(TestConstants.EMPTY_ROLE_CREDENTIALS));
    }

    @Test
    public void get_unauthorized_role_access_empty_credentials() {
        when(osUserIdentificationService.resolveSystemUID
                (Mockito.anyString(), Mockito.anyInt(), Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(OptionalInt.of(TestConstants.USER1_UID));
        WebTarget target = target(MetadataController.LATEST_IAM_CREDENTIALS_ROOT_PATH + TestConstants.USER2_ROLE_NAME);
        String actualCredentials = target.request().get(String.class);
        assertThat(actualCredentials, is(TestConstants.EMPTY_ROLE_CREDENTIALS));
    }

    @Test
    public void group_mapped_role_credentials() {
        when(osUserIdentificationService.resolveSystemUID
                (Mockito.anyString(), Mockito.anyInt(), Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(OptionalInt.of(TestConstants.GROUP_MAPPED_UID));
        WebTarget target = target(MetadataController.LATEST_IAM_CREDENTIALS_ROOT_PATH + TestConstants.GROUP_ROLE_NAME);
        String actualCredentials = target.request().get(String.class);
        assertCorrectCredentials(actualCredentials);
    }

    @Test
    public void metadata_non_credentials_get_call() {
        PowerMockito.when(EC2MetadataUtils.getData("/latest/meta-data/instance-id")).thenReturn("i-xyz");
        WebTarget target = target("/latest/meta-data/instance-id");
        String actualInstanceId = target.request().get(String.class);
        assertThat(actualInstanceId, is("i-xyz"));
    }

    @Test
    public void metadata_non_credentials_list_call() {
        PowerMockito.when(EC2MetadataUtils.getItems("/latest/metadata/iam/")).thenReturn(RESOURCES_UNDER_IAM);
        WebTarget target = target("/latest/metadata/iam/");
        String[] actualResources = target.request().get(String.class).split("\\n");
        assertThat(actualResources, arrayWithSize(2));
        assertThat(actualResources, hasItemInArray(actualResources[0]));
        assertThat(actualResources, hasItemInArray(actualResources[1]));
    }

    @Test
    public void uri_with_slashes_normalized() {
        when(osUserIdentificationService.resolveSystemUID
                (Mockito.anyString(), Mockito.anyInt(), Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(OptionalInt.of(TestConstants.USER1_UID));
        WebTarget target = target("///latest/meta-data///iam///security-credentials///");
        String actualRoleName = target.request().get(String.class);
        assertThat(actualRoleName, is(TestConstants.USER1_ROLE_NAME));
    }

    @Test
    public void uri_with_dots_normalized() {
        when(osUserIdentificationService.resolveSystemUID
                (Mockito.anyString(), Mockito.anyInt(), Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(OptionalInt.of(TestConstants.USER1_UID));
        WebTarget target = target("/latest/meta-data/./iam/./security-credentials/");
        String actualRoleName = target.request().get(String.class);
        assertThat(actualRoleName, is(TestConstants.USER1_ROLE_NAME));
    }

    private void assertCorrectCredentials(String actualCredentials) {
        EC2MetadataUtils.IAMSecurityCredential iamSecurityCredential = GSON.fromJson(actualCredentials,
                EC2MetadataUtils.IAMSecurityCredential.class);
        assertThat(iamSecurityCredential.accessKeyId, is(TestMetadataCredentialsProvider.TEST_ACCESSKEY_ID));
        assertThat(iamSecurityCredential.secretAccessKey, is(TestMetadataCredentialsProvider.TEST_SECRETKEY));
        assertThat(iamSecurityCredential.token, is(TestMetadataCredentialsProvider.TEST_SESSION_TOKEN));
        assertThat(iamSecurityCredential.code, is("Success"));
        assertThat(iamSecurityCredential.type, is("AWS-HMAC"));
    }
}