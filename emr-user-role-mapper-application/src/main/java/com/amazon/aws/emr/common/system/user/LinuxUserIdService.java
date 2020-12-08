// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.common.system.user;

import com.amazon.aws.emr.common.Constants;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates users via /proc/net/tpc(6) by searching for matching Linux UID in the file.
 *
 * <p>
 * The requests are received here for IMDS due to iptables routing. The remote addr/port in /proc/net/tpc(6)
 * for TCP socket will be the IMDS server and port. The local addr/port will be the callers address and port.
 * Note that the local entry will match the HTTP socket remote addr/port. For such an entry we check for established
 * TCP state and return back UID.
 */
@Slf4j
public class LinuxUserIdService implements UserIdService {

    public static final int TCP_ESTABLISHED = 1;
    /**
     * Details on /proc/net/tpc(6) format: http://lkml.iu.edu/hypermail/linux/kernel/0409.1/2166.html
     */
    private static Pattern pattern = Pattern.compile("\\s*\\d+: ([0-9A-Fa-f]+):([0-9A-Fa-f]+) ([0-9A-Fa-f]+):([0-9A-Fa-f]+) ([0-9A-Fa-f]{2}) [0-9A-Fa-f]+:[0-9A-Fa-f]+ [0-9A-Fa-f]+:[0-9A-Fa-f]+ [0-9A-Fa-f]+\\s+([0-9]+).+");

    private final String ipV4Path;
    private final String ipV6Path;

    public LinuxUserIdService() {
        this.ipV4Path = Constants.Network.MODULE_PROC_NET_TCP_PATH;
        this.ipV6Path = Constants.Network.MODULE_PROC_NET_TCP6_PATH;
    }

    public LinuxUserIdService(String ipV4Path, String ipV6Path) {
        this.ipV4Path = ipV4Path;
        this.ipV6Path = ipV6Path;
    }

    /**
     * Check is ip address is loopback address. Method name uses localhost since it usually means 127.0.0.1
     *
     * @param ipAddr
     * @return
     */
    public static boolean isLocalhost(String ipAddr) {
        try {
            InetAddress remoteInetAddress = InetAddress.getByName(ipAddr);
            return remoteInetAddress.isLoopbackAddress();
        } catch (UnknownHostException ex) {
            throw new RuntimeException(String.format("Unexpected IP address (%s)", ipAddr));
        }
    }

    /**
     * Resolve linux user id via linux /proc/net/tcp(6)
     *
     * For intercepted IMDS call, a validation is performed to ensure that the target is indeed IMDS,
     *  and the call is made locally.
     *
     * For non-IMDS call, only ensure that the call is made locally
     *
     * @param localAddr
     * @param localPort
     * @param remoteAddr
     * @param remotePort
     * @param isNativeIMDSApi
     * @return
     */
    public OptionalInt resolveSystemUID(String localAddr, int localPort,
                                        String remoteAddr, int remotePort,
                                        boolean isNativeIMDSApi) {

        if (!isLocalhost(localAddr)) {
            log.debug("Local address is not localhost on the HTTP socket!");
            return OptionalInt.empty();
        }

        OptionalInt uid;
        try (BufferedReader br = new BufferedReader(new FileReader(ipV4Path))) {
            String line;
            while ((line = br.readLine()) != null) {
                uid = getUID(line, localPort, remotePort, remoteAddr, isNativeIMDSApi);
                if (uid.isPresent()) {
                    return uid;
                }
            }
        } catch (IOException e) {
            log.error("Exception reading {} file. ", ipV4Path, e);
            // May be this succeeds with TCP6 socket!
        }

        try (BufferedReader br = new BufferedReader(new FileReader(ipV6Path))) {
            String line;
            while ((line = br.readLine()) != null) {
                uid = getUID(line, localPort, remotePort, remoteAddr, isNativeIMDSApi);
                if (uid.isPresent()) {
                    return uid;
                }
            }
        } catch (IOException e) {
            log.error("Exception reading {} file. ", ipV6Path, e);
            // TODO: re-throw this
        }

        return OptionalInt.empty();
    }

    private OptionalInt getUID(String line, int reqLocalPort,
                               int reqRemotePort, String procRemoteAddress,
                               boolean isNativeIMDSApi) {
        return getUID(
                line,
                procRemoteAddress,
                reqLocalPort, reqRemotePort, isNativeIMDSApi);
    }

    private OptionalInt getUID(String line, String remoteAddr,
                               int reqLocalPort, int reqRemotePort,
                               boolean isNativeIMDSApi) {
        Matcher matcher = pattern.matcher(line);
        if (!matcher.matches()) {
            return OptionalInt.empty();
        }

        int groupCount = matcher.groupCount();
        if (groupCount <= 5) {
            return OptionalInt.empty();
        }

        long procLocalPort = Long.parseLong(matcher.group(2), 16);
        String procRemoteAddress = matcher.group(3);
        long procRemotePort = Long.parseLong(matcher.group(4), 16);
        long state = Long.parseLong(matcher.group(5), 16);
        int uid = Integer.parseInt(matcher.group(6));

        if (isNativeIMDSApi) {
            if ((procRemoteAddress.equals(Constants.Network.IPV4_IMDS_ADDR_IN_HEX_REVERSED_BYTE_ORDER) ||
                    procRemoteAddress.equals(Constants.Network.IPV6_IMDS_ADDR_IN_HEX_REVERSED_BYTE_ORDER))
                    && procLocalPort == reqRemotePort
                    && procRemotePort == 80
                    && state == TCP_ESTABLISHED
            ) {
                return OptionalInt.of(uid);
            }
        } else {
            // Socket established directly from caller process to the server for below use cases:
            // 1/ impersonation request from EMR-FS
            if ((procLocalPort == reqRemotePort && procRemotePort == Constants.JETTY_PORT)
                    && (procRemoteAddress.equals(Constants.Network.IPV4_LOCALHOST_ADDR_IN_HEX_REVERSED_BYTE_ORDER)
                    || procRemoteAddress.equals(Constants.Network.IPV6_LOCALHOST_ADDR_IN_HEX_REVERSED_BYTE_ORDER)
                    || procRemoteAddress.equals(Constants.Network.IPV4_MAPPED_IPV6_LOCALHOST_ADDR_IN_HEX_REVERSED_BYTE_ORDER))) {
                return OptionalInt.of(uid);
            }
        }

        return OptionalInt.empty();
    }
}
