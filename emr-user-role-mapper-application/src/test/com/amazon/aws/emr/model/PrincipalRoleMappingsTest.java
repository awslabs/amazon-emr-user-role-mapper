// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.model;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class PrincipalRoleMappingsTest {
    final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
            .setPrettyPrinting()
            .create();

    @Test
    public void serialization() {
        PrincipalRoleMappings mappings = new PrincipalRoleMappings();

        PrincipalRoleMapping e1 = new PrincipalRoleMapping();
        e1.setUsername("u1");
        e1.setRoleArn("arn:aws:s3:::u1");
        List<String> policies = new ArrayList<>();
        policies.add("arn:aws:s3:::NewHireOrientation1");
        policies.add("arn:aws:s3:::NewHireOrientation2");
        policies.add("arn:aws:s3:::NewHireOrientation3");
        policies.add("arn:aws:s3:::NewHireOrientation4");
        e1.setPolicyArns(policies);
        e1.setDurationSeconds(900);
        e1.setExternalId("test-id");
        e1.setTokenCode("test-code");

        PrincipalRoleMapping e2 = new PrincipalRoleMapping();
        String inline = "{\n" +
                "\"Version\":\"2012-10-17\"," +
                "\"Statement\":[{\n" +
                "    \"Sid\":\"Statement1\"," +
                "    \"Effect\":\"Allow\"," +
                "    \"Action\":[\"s3:GetBucket\", \"s3:GetObject\"]," +
                "    \"Resource\": [\"arn:aws:s3:::NewHireOrientation\", \"arn:aws:s3:::NewHireOrientation/*\"]" +
                "    }]" +
                "} ";
        e2.setGroupname("u1");
        e2.setRoleArn("arn:aws:s3:::u1");
        e2.setDurationSeconds(20);
        e2.setPolicy(inline);
        mappings.principalRoleMappings = new PrincipalRoleMapping[2];
        mappings.principalRoleMappings[0] = e1;
        mappings.principalRoleMappings[1] = e2;

        String expected = "{\n" +
                "  \"PrincipalRoleMappings\": [\n" +
                "    {\n" +
                "      \"username\": \"u1\",\n" +
                "      \"rolearn\": \"arn:aws:s3:::u1\",\n" +
                "      \"policies\": [\n" +
                "        \"arn:aws:s3:::NewHireOrientation1\",\n" +
                "        \"arn:aws:s3:::NewHireOrientation2\",\n" +
                "        \"arn:aws:s3:::NewHireOrientation3\",\n" +
                "        \"arn:aws:s3:::NewHireOrientation4\"\n" +
                "      ],\n" +
                "      \"duration\": 900,\n" +
                "      \"externalid\": \"test-id\",\n" +
                "      \"tokencode\": \"test-code\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"groupname\": \"u1\",\n" +
                "      \"rolearn\": \"arn:aws:s3:::u1\",\n" +
                "      \"textpolicy\": \"{\\n\\\"Version\\\":\\\"2012-10-17\\\",\\\"Statement\\\":[{\\n    \\\"Sid\\\":\\\"Statement1\\\",    \\\"Effect\\\":\\\"Allow\\\",    \\\"Action\\\":[\\\"s3:GetBucket\\\", \\\"s3:GetObject\\\"],    \\\"Resource\\\": [\\\"arn:aws:s3:::NewHireOrientation\\\", \\\"arn:aws:s3:::NewHireOrientation/*\\\"]    }]} \",\n" +
                "      \"duration\": 20\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        assertThat(expected, is(GSON.toJson(mappings)));
    }

    @Test
    public void deserialization() {
        String json = "{\n" +
                "  \"PrincipalRoleMappings\": [\n" +
                "    {\n" +
                "      \"username\": \"u1\",\n" +
                "      \"rolearn\": \"arn:aws:iam::176430881729:role/u1\",\n" +
                "      \"duration\": 900,\n" +
                "      \"externalid\": \"test-id\",\n" +
                "      \"tokencode\": \"test-code\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"username\": \"u2\",\n" +
                "      \"rolearn\": \"arn:aws:iam::176430881729:role/u2\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"groupname\": \"g1\",\n" +
                "      \"rolearn\": \"arn:aws:iam::176430881729:role/g1\",\n" +
                "      \"duration\": 900\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        PrincipalRoleMappings principalRoleMappings = GSON.fromJson(json, PrincipalRoleMappings.class);
        assertThat(principalRoleMappings.getPrincipalRoleMappings().length, is(3));

        PrincipalRoleMapping mapping1 = principalRoleMappings.getPrincipalRoleMappings()[0];
        assertThat(mapping1, is(PrincipalRoleMapping.builder()
                .username("u1")
                .roleArn("arn:aws:iam::176430881729:role/u1")
                .durationSeconds(900)
                .externalId("test-id")
                .tokenCode("test-code")
                .build()));
        PrincipalRoleMapping mapping2 = principalRoleMappings.getPrincipalRoleMappings()[1];
        assertThat(mapping2, is(PrincipalRoleMapping.builder()
                .username("u2")
                .roleArn("arn:aws:iam::176430881729:role/u2")
                .build()));
        PrincipalRoleMapping mapping3 = principalRoleMappings.getPrincipalRoleMappings()[2];
        assertThat(mapping3, is(PrincipalRoleMapping.builder()
                .groupname("g1")
                .roleArn("arn:aws:iam::176430881729:role/g1")
                .durationSeconds(900)
                .build()));
    }
}
