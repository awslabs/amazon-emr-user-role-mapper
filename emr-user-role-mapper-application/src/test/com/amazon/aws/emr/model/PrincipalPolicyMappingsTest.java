// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import com.google.common.collect.Lists;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PrincipalPolicyMappingsTest {
    final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
            .setPrettyPrinting()
            .create();

    @Test
    public void serialization() {
        PrincipalPolicyMappings mappings = new PrincipalPolicyMappings();

        PrincipalPolicyMapping e1 = new PrincipalPolicyMapping();
        e1.setUsername("u1");
        List<String> policies = new ArrayList<>();
        policies.add("arn:aws:s3:::NewHireOrientation1");
        policies.add("arn:aws:s3:::NewHireOrientation2");
        policies.add("arn:aws:s3:::NewHireOrientation3");
        policies.add("arn:aws:s3:::NewHireOrientation4");
        e1.setPolicyArns(policies);

        PrincipalPolicyMapping e2 = new PrincipalPolicyMapping();
        e2.setGroupname("g1");
        List<String> gpPolicies = new ArrayList<>();
        gpPolicies.add("arn:aws:s3:::MyBucket");
        e2.setPolicyArns(gpPolicies);

        mappings.principalPolicyMappings = new PrincipalPolicyMapping[2];
        mappings.principalPolicyMappings[0] = e1;
        mappings.principalPolicyMappings[1] = e2;

        String expected = "{\n" +
                "  \"PrincipalPolicyMappings\": [\n" +
                "    {\n" +
                "      \"username\": \"u1\",\n" +
                "      \"policies\": [\n" +
                "        \"arn:aws:s3:::NewHireOrientation1\",\n" +
                "        \"arn:aws:s3:::NewHireOrientation2\",\n" +
                "        \"arn:aws:s3:::NewHireOrientation3\",\n" +
                "        \"arn:aws:s3:::NewHireOrientation4\"\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"groupname\": \"g1\",\n" +
                "      \"policies\": [\n" +
                "        \"arn:aws:s3:::MyBucket\"\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        assertThat(expected, is(GSON.toJson(mappings)));
    }

    @Test
    public void deserialization() {
        String json = "{\n" +
            "  \"PrincipalPolicyMappings\": [\n" +
            "    {\n" +
            "      \"username\": \"u1\",\n" +
            "      \"policies\": [\n" +
            "        \"arn:aws:s3:::NewHireOrientation1\",\n" +
            "        \"arn:aws:s3:::NewHireOrientation2\",\n" +
            "        \"arn:aws:s3:::NewHireOrientation3\",\n" +
            "        \"arn:aws:s3:::NewHireOrientation4\"\n" +
            "      ]\n" +
            "    },\n" +
            "    {\n" +
            "      \"groupname\": \"g1\",\n" +
            "      \"policies\": [\n" +
            "        \"arn:aws:s3:::MyBucket\"\n" +
            "      ]\n" +
            "    }\n" +
            "  ]\n" +
            "}";
        PrincipalPolicyMappings principalPolicyMappings = GSON
            .fromJson(json, PrincipalPolicyMappings.class);
        assertThat(principalPolicyMappings.getPrincipalPolicyMappings().length, is(2));

        PrincipalPolicyMapping mapping1 = principalPolicyMappings.getPrincipalPolicyMappings()[0];
        assertThat(mapping1, is(PrincipalPolicyMapping.builder()
            .username("u1")
            .policyArns(Arrays.asList("arn:aws:s3:::NewHireOrientation1",
                "arn:aws:s3:::NewHireOrientation2", "arn:aws:s3:::NewHireOrientation3",
                "arn:aws:s3:::NewHireOrientation4"))
            .build()));

        PrincipalPolicyMapping mapping2 = principalPolicyMappings.getPrincipalPolicyMappings()[1];
        assertThat(mapping2, is(PrincipalPolicyMapping.builder()
            .groupname("g1")
            .policyArns(Arrays.asList("arn:aws:s3:::MyBucket"))
            .build()));
    }
}
