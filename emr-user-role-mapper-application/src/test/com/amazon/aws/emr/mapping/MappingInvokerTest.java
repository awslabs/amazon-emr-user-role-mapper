package com.amazon.aws.emr.mapping;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import com.amazon.aws.emr.ApplicationConfiguration;
import com.amazon.aws.emr.common.Constants;
import com.amazon.aws.emr.rolemapper.UserRoleMapperProvider;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import java.util.Optional;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import static org.mockito.Mockito.when;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class MappingInvokerTest {

  private static final String TEST_USER = "test-user";
  @Mock
  UserRoleMapperProvider roleMapperProvider;
  @Mock
  ApplicationConfiguration applicationConfiguration;
  @InjectMocks
  MappingInvoker mappingInvoker;

  @Test
  public void is_s3_based_impl() {
    assertThat(mappingInvoker.isS3BasedProviderImpl("my.class"), is(false));
    assertThat(mappingInvoker.isS3BasedProviderImpl(Constants.ROLE_MAPPING_DEFAULT_CLASSNAME),
        is(true));
    assertThat(
        mappingInvoker.isS3BasedProviderImpl(Constants.ROLE_MAPPING_MANAGED_POLICY_CLASSNAME),
        is(true));
  }

  @Test
  public void source_identity_enabled_then_injected() {
    AssumeRoleRequest assumeRoleRequest = new AssumeRoleRequest().withRoleArn("test-arn");
    when(roleMapperProvider.getMapping(TEST_USER)).thenReturn(Optional.of(assumeRoleRequest));
    when(applicationConfiguration.isSetSourceIdentityEnabled()).thenReturn(true);
    Optional<AssumeRoleRequest> actual = mappingInvoker.map(TEST_USER);
    assertThat(actual.isPresent(), is(true));
    assertThat(actual.get(), is(assumeRoleRequest.withSourceIdentity(TEST_USER)));
  }

  @Test
  public void source_identity_disabled_then_not_injected() {
    AssumeRoleRequest assumeRoleRequest = new AssumeRoleRequest().withRoleArn("test-arn");
    when(roleMapperProvider.getMapping(TEST_USER)).thenReturn(Optional.of(assumeRoleRequest));
    when(applicationConfiguration.isSetSourceIdentityEnabled()).thenReturn(false);
    Optional<AssumeRoleRequest> actual = mappingInvoker.map(TEST_USER);
    assertThat(actual.isPresent(), is(true));
    assertThat(actual.get(), is(assumeRoleRequest));
  }

}
