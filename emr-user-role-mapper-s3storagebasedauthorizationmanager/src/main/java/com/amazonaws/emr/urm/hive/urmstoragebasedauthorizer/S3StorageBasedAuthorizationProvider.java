package com.amazonaws.emr.urm.hive.urmstoragebasedauthorizer;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3URI;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.metastore.HiveMetaStore;
import org.apache.hadoop.hive.metastore.Warehouse;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.hadoop.hive.ql.metadata.AuthorizationException;
import org.apache.hadoop.hive.ql.metadata.Hive;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.metadata.Partition;
import org.apache.hadoop.hive.ql.metadata.Table;
import org.apache.hadoop.hive.ql.security.authorization.HiveAuthorizationProviderBase;
import org.apache.hadoop.hive.ql.security.authorization.HiveMetastoreAuthorizationProvider;
import org.apache.hadoop.hive.ql.security.authorization.Privilege;
import org.apache.hadoop.hive.metastore.api.Database;
import org.apache.hadoop.hive.metastore.IHMSHandler;
import org.apache.hadoop.hive.ql.security.authorization.StorageBasedAuthorizationProvider;

import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAccessControlException;
import org.eclipse.jetty.http.HttpStatus;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import java.security.AccessControlException;
import java.util.UUID;

public class S3StorageBasedAuthorizationProvider extends HiveAuthorizationProviderBase
        implements HiveMetastoreAuthorizationProvider{

    private static final String HDFS_SCHEME = "hdfs://";
    private static final Log LOG = LogFactory.getLog(S3StorageBasedAuthorizationProvider.class);
    private static final boolean SKIP_READ_PERMISSIONS_DEFAULT = false;

    //private static FileWriter myWriter;
    private Warehouse wh;
    private boolean isRunFromMetaStore = false;

    static final String SKIP_READ_PERMISSIONS_CONF = "hive.metastore.authorization.s3sba.urm.skipreadpermissions";

    private URMCredentialsRetriever urmCredentialsRetriever;

    S3StorageBasedAuthorizationProvider(URMCredentialsRetriever urmCredentialsRetriever) {
        this.urmCredentialsRetriever = urmCredentialsRetriever;
    }

    public S3StorageBasedAuthorizationProvider() {
        this.urmCredentialsRetriever = new URMCredentialsRetriever();
    }

    /**
     * Make sure that the warehouse variable is set up properly.
     * @throws MetaException if unable to instantiate
     */
    private void initWh() throws MetaException, HiveException, MetaException {
        if (wh == null){
            if(!isRunFromMetaStore){
                // Note, although HiveProxy has a method that allows us to check if we're being
                // called from the metastore or from the client, we don't have an initialized HiveProxy
                // till we explicitly initialize it as being from the client side. So, we have a
                // chicken-and-egg problem. So, we now track whether or not we're running from client-side
                // in the SBAP itself.
                hive_db = new HiveProxy(Hive.get(getConf(), StorageBasedAuthorizationProvider.class));
                this.wh = new Warehouse(getConf());
            } else {
                // not good if we reach here, this was initialized at setMetaStoreHandler() time.
                // this means handler.getWh() is returning null. Error out.
                throw new IllegalStateException("Uninitialized Warehouse from MetastoreHandler");
            }
        }
    }

    @Override
    public void init(Configuration conf) throws HiveException {
        hive_db = new HiveProxy();
    }

    @Override
    public void authorize(Privilege[] readRequiredPriv, Privilege[] writeRequiredPriv)
            throws HiveException, AuthorizationException {
        Path root = null;
        try {
            initWh();
            root = (Path) wh.getWhRoot();
            authorize(root.toString(), readRequiredPriv, writeRequiredPriv);
        } catch (MetaException ex) {
            throw hiveException(ex);
        }
    }

    @Override
    public void authorize(Database db, Privilege[] readRequiredPriv, Privilege[] writeRequiredPriv)
            throws HiveException, AuthorizationException {
        authorize(db.getLocationUri(), readRequiredPriv, writeRequiredPriv);
    }

    @Override
    public void authorize(Table table, Privilege[] readRequiredPriv, Privilege[] writeRequiredPriv)
            throws HiveException, AccessControlException {
        //Authorize view, not supported.
        if(table.isView() || table.isMaterializedView()){
            return;
        }
        authorize(table.getDataLocation().toString(), readRequiredPriv, writeRequiredPriv);
    }

    @Override
    public void authorize(Partition part, Privilege[] readRequiredPriv, Privilege[] writeRequiredPriv)
            throws HiveException, AuthorizationException {
        authorize(part.getTable(), part, readRequiredPriv, writeRequiredPriv);
    }

    private void authorize(Table table, Partition part, Privilege[] readRequiredPriv,
                           Privilege[] writeRequiredPriv)
            throws HiveException, AuthorizationException {
        // Partition path can be null in the case of a new create partition - in this case,
        // we try to default to checking the permissions of the parent table.
        // Partition itself can also be null, in cases where this gets called as a generic
        // catch-all call in cases like those with CTAS onto an unpartitioned table (see HIVE-1887)
        if ((part == null) || (part.getLocation() == null)) {
            // this should be the case only if this is a create partition.
            // The privilege needed on the table should be ALTER_DATA, and not CREATE
            authorize(table, new Privilege[]{}, new Privilege[]{Privilege.ALTER_DATA});
        } else {
            authorize(part.getDataLocation().toString(), readRequiredPriv, writeRequiredPriv);
        }
    }

    @Override
    public void authorize(Table table, Partition part, List<String> columns,
                          Privilege[] readRequiredPriv, Privilege[] writeRequiredPriv) throws HiveException,
            AuthorizationException {
        // In a simple storage-based auth, we have no information about columns
        // living in different files, so we do simple partition-auth and ignore
        // the columns parameter.
        authorize(table, part, readRequiredPriv, writeRequiredPriv);
    }

    @Override
public void setMetaStoreHandler(HiveMetaStore.HMSHandler handler) {        
	hive_db.setHandler(handler);
        this.wh = handler.getWh();
        this.isRunFromMetaStore = true;
    }

    @Override
    public void authorizeAuthorizationApiInvocation() throws HiveException, AuthorizationException {
        // no-op - SBA does not attempt to authorize auth api call. Allow it
    }

    /**
     * Authorization privileges against a path.
     *
     * @param path
     *          a filesystem path
     * @param readRequiredPriv
     *          a list of privileges needed for inputs.
     * @param writeRequiredPriv
     *          a list of privileges needed for outputs.
     */
    @VisibleForTesting
    void authorize(String path, Privilege[] readRequiredPriv, Privilege[] writeRequiredPriv)
            throws HiveException, AuthorizationException {
        //if path is a hdfs one, skip and return
        if(path.startsWith(HDFS_SCHEME)) {
            LOG.info("A hdfs path has encountered, do nothing");
            return;
        }

        try {
            EnumSet<S3Action> actions = getS3Actions(readRequiredPriv);
            actions.addAll(getS3Actions(writeRequiredPriv));
            if (actions.isEmpty()) {
                return;
            }
            checkPermissions(path, actions);
        } catch (AccessControlException ex) {
            throw authorizationException(ex);
        } catch (Exception e) {
            e.printStackTrace();
            throw new HiveAccessControlException("Failed to authorize request. ", e);
        }
    }

    /**
     * Given a Privilege[], find out what all S3Actions are required
     */
    protected EnumSet<S3Action> getS3Actions(Privilege[] privs) {
        EnumSet<S3Action> actions = EnumSet.noneOf(S3Action.class);
        if (privs == null) {
            return actions;
        }
        for (Privilege priv : privs) {
            actions.add(getS3Action(priv));
        }
        return actions;
    }

    /**
     * Given a privilege, return what S3Actions are required
     */
    protected S3Action getS3Action(Privilege priv) {

        switch (priv.getPriv()) {
            case ALL:
                return S3Action.ALL;
            case ALTER_DATA:
            case ALTER_METADATA:
            case CREATE:
            case DROP:
                return S3Action.WRITE;
            case LOCK:
                throw new AuthorizationException(
                        "StorageBasedAuthorizationProvider cannot handle LOCK privilege");
            case SELECT:
            case SHOW_DATABASE:
                return S3Action.READ;
            case UNKNOWN:
            default:
                throw new AuthorizationException("Unknown privilege: " + priv.toString());
        }
    }

    private void checkPermissions(String path, EnumSet<S3Action> actions) throws AccessControlException {
        String userName = this.authenticator.getUserName();

        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("Checking permissions for user: %s for path: %s for actions: %s", userName, path, actions.toString()));
        }

        AWSCredentials sessionCredentials = urmCredentialsRetriever.getCredentialsForUser(userName);
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                    .withCredentials(new AWSStaticCredentialsProvider(sessionCredentials))
                    .build();

        for(S3Action action : actions){
            checkActionS3(s3Client, action, path, userName);
        }
    }

    private void checkActionS3(AmazonS3 s3Client, S3Action action, String path, String userName) throws AccessControlException  {
        AmazonS3URI s3URIparser = new AmazonS3URI(path);

        String bucketName = s3URIparser.getBucket();
        String objectKey = s3URIparser.getKey();
        String prefix = addSlashIfNotExists(objectKey);

        if (action.equals(S3Action.READ) || action.equals(S3Action.ALL)) {
            if (!getConf().getBoolean(SKIP_READ_PERMISSIONS_CONF, SKIP_READ_PERMISSIONS_DEFAULT)) {
                testReadPath(s3Client, action, bucketName, prefix, userName);
            }
        }

        //Validate if Write permissions are available with returned credentials.
        if (action.equals(S3Action.WRITE) || action.equals(S3Action.ALL)) {
            testWritePath(s3Client, action, userName, bucketName, prefix);
        }
    }

    private String addSlashIfNotExists(String objectKey)
    {
        if (objectKey == null) {
            return "";
        }
        if (!objectKey.endsWith("/")) {
            return objectKey + "/";
        }
        return objectKey;
    }

    private void testWritePath(AmazonS3 s3Client, S3Action action, String userName, String bucketName, String prefix)
    {
        String writeObjectPrefix = prefix + RandomString() + userName;
        InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(bucketName, writeObjectPrefix);
        String uploadId = null;
        try {
            uploadId = s3Client.initiateMultipartUpload(initRequest).getUploadId();
        } catch (AmazonServiceException ex) {
            checkAwsStatusCode(action, bucketName, writeObjectPrefix, userName, ex);
        } finally {
            if (uploadId != null) {
                s3Client.abortMultipartUpload(new AbortMultipartUploadRequest(bucketName,
                        writeObjectPrefix, uploadId));
            }
        }
    }

    private void testReadPath(AmazonS3 s3Client, S3Action action, String bucketName, String path, String userName) {
        ListObjectsV2Request listObjectsV2Request = new ListObjectsV2Request()
                .withBucketName(bucketName)
                .withPrefix(path)
                .withMaxKeys(1);
        try {
            s3Client.listObjectsV2(listObjectsV2Request);
        } catch (AmazonServiceException ex) {
            checkAwsStatusCode(action, bucketName, path, userName, ex);
        }
    }

    private void checkAwsStatusCode(S3Action action, String bucketName, String path, String userName, AmazonServiceException ex)
    {
        if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED_401 ||
                ex.getStatusCode() == HttpStatus.FORBIDDEN_403) {
            throw new AccessControlException("User: " + userName + " does not have privilege: "
                    + action.toString() + " for path: " + bucketName + "/" + path);
        }
        throw new RuntimeException("Caught unexpected exception when calling S3: " + ex.getMessage(), ex);
    }

    private HiveException hiveException(Exception e) {
        return new HiveException(e);
    }

    private AuthorizationException authorizationException(Exception e) {
        return new AuthorizationException(e);
    }

    private String RandomString() {
        return UUID.randomUUID().toString();
    }
}
