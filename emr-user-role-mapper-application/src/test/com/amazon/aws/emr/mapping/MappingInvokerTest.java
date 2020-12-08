package com.amazon.aws.emr.mapping;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import com.amazon.aws.emr.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class MappingInvokerTest {

  MappingInvoker mappingInvoker = new MappingInvoker();

  @Test
  public void is_s3_based_impl() {
    assertThat(mappingInvoker.isS3BasedProviderImpl("my.class"), is(false));
    assertThat(mappingInvoker.isS3BasedProviderImpl(Constants.ROLE_MAPPING_DEFAULT_CLASSNAME),
        is(true));
    assertThat(
        mappingInvoker.isS3BasedProviderImpl(Constants.ROLE_MAPPING_MANAGED_POLICY_CLASSNAME),
        is(true));
  }
}
