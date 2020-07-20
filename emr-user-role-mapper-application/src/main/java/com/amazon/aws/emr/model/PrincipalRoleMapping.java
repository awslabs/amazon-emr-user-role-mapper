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
public class PrincipalRoleMapping {

    @SerializedName("username")
    private String username;

    @SerializedName("groupname")
    private String groupname;

    @SerializedName("rolearn")
    private String roleArn;

    @SerializedName("session")
    private String roleSessionName;

    @SerializedName("policies")
    private java.util.List<String> policyArns;

    @SerializedName("textpolicy")
    private String policy;

    @SerializedName("duration")
    private Integer durationSeconds;

    @SerializedName("externalid")
    private String externalId;

    @SerializedName("serialnumber")
    private String serialNumber;

    @SerializedName("tokencode")
    private String tokenCode;
}