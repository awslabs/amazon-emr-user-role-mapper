// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.model;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class PrincipalPolicyMapping {

    @SerializedName("username")
    private String username;

    @SerializedName("groupname")
    private String groupname;

    @SerializedName("policies")
    private java.util.List<String> policyArns;
}