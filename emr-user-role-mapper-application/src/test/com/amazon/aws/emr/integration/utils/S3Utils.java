package com.amazon.aws.emr.integration.utils;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.S3Object;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class S3Utils {

  final static AmazonS3 s3 = AmazonS3ClientBuilder.standard()
      .build();

  public static Bucket createBucket(String bucket_name) {
    Bucket b = null;
    if (s3.doesBucketExistV2(bucket_name)) {
      System.out.format("Bucket %s already exists.\n", bucket_name);
      b = getBucket(bucket_name);
    } else {
      b = s3.createBucket(bucket_name);
    }
    return b;
  }

  public static Bucket getBucket(String bucket_name) {
    Bucket named_bucket = null;
    List<Bucket> buckets = s3.listBuckets();
    for (Bucket b : buckets) {
      if (b.getName().equals(bucket_name)) {
        named_bucket = b;
      }
    }
    return named_bucket;
  }

  public static void uploadObject(String bucket, String key, String fileContents) {
    s3.putObject(bucket, key, fileContents);
  }

  public static void deleteObject(String bucket, String key) {
    try {
      s3.deleteObject(bucket, key);
    } catch (AmazonServiceException e) {
      log.warn("Could not delete the object {}/{}", bucket, key);
    }
  }

  public static void deleteBucket(String bucket) {
    try {
      s3.deleteBucket(bucket);
    } catch (AmazonServiceException e) {
      log.warn("Could not delete the bucket {}", bucket);
    }
  }

  public static String getS3FileAsString(S3Object s3Object) throws IOException {
    InputStream is = s3Object.getObjectContent();
    if (is == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(is, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line);
      }
      return sb.toString();
    }
  }
}
