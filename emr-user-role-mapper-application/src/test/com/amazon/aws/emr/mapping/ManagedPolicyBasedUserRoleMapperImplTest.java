package com.amazon.aws.emr.mapping;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.hamcrest.core.Is.is;

import com.amazon.aws.emr.common.Constants;
import com.amazon.aws.emr.common.system.PrincipalResolver;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ManagedPolicyBasedUserRoleMapperImplTest {

  ManagedPolicyBasedUserRoleMapperImpl managedPolicyBasedUserRoleMapper;

  @Mock
  PrincipalResolver principalResolver;

  private static final String TEST_BUCKET = "testBucket";
  private static final String TEST_KEY = "testKey";
  private static final String MAPPING_ROLE_ARN = "arn:aws:s3:::MappingRole";
  private static final String mappingJson =  "{\n" +
      "  \"PrincipalPolicyMappings\": [\n" +
      "    {\n" +
      "      \"username\": \"u1\",\n" +
      "      \"policies\": [\n" +
      "        \"arn:aws:s3:::UserPolicy\"\n" +
      "      ]\n" +
      "    },\n" +
      "    {\n" +
      "      \"username\": \"g1\",\n" +
      "      \"policies\": [\n" +
      "        \"arn:aws:s3:::Group1-P1\",\n" +
      "        \"arn:aws:s3:::Group1-P2\",\n" +
      "        \"arn:aws:s3:::Group-Common\"\n" +
      "      ]\n" +
      "    },\n" +
      "    {\n" +
      "      \"groupname\": \"g2\",\n" +
      "      \"policies\": [\n" +
      "        \"arn:aws:s3:::Group2-P1\"\n," +
      "        \"arn:aws:s3:::Group-Common\"\n" +
      "      ]\n" +
      "    }\n" +
      "  ]\n" +
      "}";

  @Before
  public void setUp() {
    managedPolicyBasedUserRoleMapper =
        new ManagedPolicyBasedUserRoleMapperImpl(TEST_BUCKET, TEST_KEY, principalResolver);
    managedPolicyBasedUserRoleMapper.init(Collections.singletonMap(Constants.ROLE_MAPPING_ROLE_ARN,
        MAPPING_ROLE_ARN));
    managedPolicyBasedUserRoleMapper.processFile(mappingJson);
  }

  @Test
  public void user_with_one_group_assumeRoleRequest() {
    when(principalResolver.getGroups(eq("u1"))).thenReturn(Optional.of(Collections.singletonList("g1")));
    Optional<AssumeRoleRequest> optionalAssumeRoleRequest = managedPolicyBasedUserRoleMapper.getMapping("u1");
    assertThat(optionalAssumeRoleRequest.isPresent(), is(true));
    AssumeRoleRequest assumeRoleRequest = optionalAssumeRoleRequest.get();
    assertThat(assumeRoleRequest.getRoleArn(), is(MAPPING_ROLE_ARN));
    assertThat(assumeRoleRequest.getPolicyArns(), hasSize(4));
    assertThat(assumeRoleRequest.getPolicyArns().get(0).getArn(), is("arn:aws:s3:::UserPolicy"));
    assertThat(assumeRoleRequest.getPolicyArns().get(1).getArn(), is("arn:aws:s3:::Group1-P1"));
    assertThat(assumeRoleRequest.getPolicyArns().get(2).getArn(), is("arn:aws:s3:::Group1-P2"));
    assertThat(assumeRoleRequest.getPolicyArns().get(3).getArn(), is("arn:aws:s3:::Group-Common"));
  }

  @Test
  public void user_with_two_groups_assumeRoleRequest() {
    when(principalResolver.getGroups(eq("u1"))).thenReturn(Optional.of(Arrays.asList("g1", "g2")));
    Optional<AssumeRoleRequest> optionalAssumeRoleRequest = managedPolicyBasedUserRoleMapper.getMapping("u1");
    assertThat(optionalAssumeRoleRequest.isPresent(), is(true));
    AssumeRoleRequest assumeRoleRequest = optionalAssumeRoleRequest.get();
    assertThat(assumeRoleRequest.getRoleArn(), is(MAPPING_ROLE_ARN));
    assertThat(assumeRoleRequest.getPolicyArns(), hasSize(5));
    assertThat(assumeRoleRequest.getPolicyArns().get(0).getArn(), is("arn:aws:s3:::UserPolicy"));
    assertThat(assumeRoleRequest.getPolicyArns().get(1).getArn(), is("arn:aws:s3:::Group1-P1"));
    assertThat(assumeRoleRequest.getPolicyArns().get(2).getArn(), is("arn:aws:s3:::Group1-P2"));
    assertThat(assumeRoleRequest.getPolicyArns().get(3).getArn(), is("arn:aws:s3:::Group-Common"));
    assertThat(assumeRoleRequest.getPolicyArns().get(4).getArn(), is("arn:aws:s3:::Group2-P1"));
  }

  @Test
  public void user_with_no_groups_assumeRoleRequest() {
    when(principalResolver.getGroups(eq("u1"))).thenReturn(Optional.of(Collections.emptyList()));
    Optional<AssumeRoleRequest> optionalAssumeRoleRequest = managedPolicyBasedUserRoleMapper.getMapping("u1");
    assertThat(optionalAssumeRoleRequest.isPresent(), is(true));
    AssumeRoleRequest assumeRoleRequest = optionalAssumeRoleRequest.get();
    assertThat(assumeRoleRequest.getRoleArn(), is(MAPPING_ROLE_ARN));
    assertThat(assumeRoleRequest.getPolicyArns(), hasSize(1));
    assertThat(assumeRoleRequest.getPolicyArns().get(0).getArn(), is("arn:aws:s3:::UserPolicy"));
  }

  @Test
  public void user_with_no_mapping_assumeRoleRequest() {
    when(principalResolver.getGroups(eq("u2"))).thenReturn(Optional.of(Collections.emptyList()));
    Optional<AssumeRoleRequest> optionalAssumeRoleRequest = managedPolicyBasedUserRoleMapper
        .getMapping("u2");
    assertThat(optionalAssumeRoleRequest.isPresent(), is(false));
  }

  @Test
  public void user_with_no_username_mapping_assumeRoleRequest() {
    when(principalResolver.getGroups(eq("u3"))).thenReturn(Optional.of(Collections.singletonList("g1")));
    Optional<AssumeRoleRequest> optionalAssumeRoleRequest = managedPolicyBasedUserRoleMapper
        .getMapping("u3");
    assertThat(optionalAssumeRoleRequest.isPresent(), is(true));
    AssumeRoleRequest assumeRoleRequest = optionalAssumeRoleRequest.get();
    assertThat(assumeRoleRequest.getRoleArn(), is(MAPPING_ROLE_ARN));
    assertThat(assumeRoleRequest.getPolicyArns(), hasSize(3));
    assertThat(assumeRoleRequest.getPolicyArns().get(0).getArn(), is("arn:aws:s3:::Group1-P1"));
    assertThat(assumeRoleRequest.getPolicyArns().get(1).getArn(), is("arn:aws:s3:::Group1-P2"));
    assertThat(assumeRoleRequest.getPolicyArns().get(2).getArn(), is("arn:aws:s3:::Group-Common"));
  }
}
