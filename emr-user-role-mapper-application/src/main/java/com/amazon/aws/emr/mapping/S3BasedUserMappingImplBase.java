package com.amazon.aws.emr.mapping;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;

/**
 * Common functionality to fetch and retrieve mappings stored in S3.
 */
@Slf4j
public abstract class S3BasedUserMappingImplBase {

  protected String bucketName;
  protected String key;
  protected String etag;
  protected static AmazonS3 s3Client = null;

  public void refresh() {
    log.debug("Checking if need to load mapping again from S3 from {}/{}", bucketName, key);
    ObjectMetadata objectMetadata = getS3Client().getObjectMetadata(bucketName, key);
    if (objectMetadata.getETag().equals(etag)) {
      log.debug("Nothing to do as current etag {} matches the last one.", objectMetadata.getETag());
    } else {
      log.info("Seems we have new mapping - reload it.");
      readMapping();
      log.info("Done with the reload.");
    }
  }

  /**
   * Process the contents of S3 mapping file.
   *
   * @param json the contents of the S3 mapping.
   */
  abstract void processFile(String json);

  private void readMapping() {
    log.info("Load the mapping from S3 from {}/{}", bucketName, key);
    try (S3Object s3object = getS3Client().getObject(new GetObjectRequest(
        bucketName, key))) {
      S3ObjectInputStream s3InputStream = s3object.getObjectContent();
      String jsonString = null;
      try {
        jsonString = getS3FileAsString(s3InputStream);
      } catch (IOException e) {
        throw new RuntimeException("Could not fetch the mapping file from S3.", e);
      }
      // Update the ETag
      etag = s3object.getObjectMetadata().getETag();
      processFile(jsonString);
    } catch (AmazonClientException ace) {
      log.error("AWS exception {}", ace.getMessage(), ace);
    } catch (IOException e) {
      log.error("Could not load mapping from S3", e);
    }
  }

  private static String getS3FileAsString(InputStream is) throws IOException {
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

  synchronized static AmazonS3 getS3Client() {
    if (s3Client == null) {
      s3Client = AmazonS3ClientBuilder
          .standard()
          .build();
    }
    return s3Client;
  }
}