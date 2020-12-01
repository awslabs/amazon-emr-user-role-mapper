package com.amazon.aws.emr.utils;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.io.File;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResourceUtils {

  final static AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.DEFAULT_REGION)
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

  public static void uploadObject(String bucket, String key, String file) {
    s3.putObject(bucket, key, new File(file));
  }

  public static void listBucket(String bucket) {
    ListObjectsV2Result result = s3.listObjectsV2(bucket);
    List<S3ObjectSummary> objects = result.getObjectSummaries();
    for (S3ObjectSummary os : objects) {
      System.out.println("* " + os.getKey());
    }
  }

  public static void deleteBucket(String bucket) {
    s3.deleteBucket(bucket);
  }
}
