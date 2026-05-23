package com.example.platform.delivery.infrastructure;

import com.example.platform.delivery.domain.DeliveryProtocol;
import com.example.platform.delivery.spi.DeliveryAdapter;
import com.example.platform.delivery.spi.DeliveryContext;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SftpDeliveryAdapter implements DeliveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(SftpDeliveryAdapter.class);

    @Override
    public DeliveryProtocol protocol() {
        return DeliveryProtocol.SFTP;
    }

    @Override
    public ProbeResult probe(DeliveryContext context) {
        Session session = null;
        try {
            session = openSession(context);
            session.connect(10_000);
            return ProbeResult.success();
        } catch (Exception e) {
            return ProbeResult.failure(e.getMessage());
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    @Override
    public DeliveryResult deliver(DeliveryContext context) {
        Session session = null;
        ChannelSftp channel = null;
        try {
            byte[] bytes = context.sourceStream().readAllBytes();
            session = openSession(context);
            session.connect(30_000);
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(10_000);
            String remotePath = absoluteRemotePath(context);
            mkdirs(channel, parentDir(remotePath));
            channel.put(new ByteArrayInputStream(bytes), remotePath, ChannelSftp.OVERWRITE);
            String remoteUri = "sftp://" + host(context) + remotePath;
            log.info("SFTP delivery job={} path={} bytes={}", context.deliveryJobId(), remotePath, bytes.length);
            return DeliveryResult.ok(context.remotePath(), remoteUri, bytes.length);
        } catch (Exception e) {
            log.warn("SFTP delivery failed job={}: {}", context.deliveryJobId(), e.getMessage());
            return DeliveryResult.fail(e.getMessage());
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private static Session openSession(DeliveryContext context) throws Exception {
        Map<String, Object> config = context.destinationConfig();
        Map<String, String> creds = context.credentials();
        String host = DeliveryConfigParser.stringVal(config, "host");
        int port = DeliveryConfigParser.intVal(config, "port", 22);
        String username = creds.getOrDefault("username", DeliveryConfigParser.stringVal(config, "username"));
        String password = creds.get("password");
        String privateKey = creds.get("privateKey");
        if (host.isBlank() || username.isBlank()) {
            throw new IllegalArgumentException("SFTP requires host and username");
        }
        JSch jsch = new JSch();
        if (privateKey != null && !privateKey.isBlank()) {
            jsch.addIdentity("delivery-key", privateKey.getBytes(), null, null);
        }
        Session session = jsch.getSession(username, host, port);
        if (password != null) {
            session.setPassword(password);
        }
        Properties props = new Properties();
        props.put("StrictHostKeyChecking", DeliveryConfigParser.stringVal(config, "strictHostKeyChecking", "no"));
        session.setConfig(props);
        return session;
    }

    private static String host(DeliveryContext context) {
        return DeliveryConfigParser.stringVal(context.destinationConfig(), "host");
    }

    private static String absoluteRemotePath(DeliveryContext context) {
        String base = DeliveryConfigParser.stringVal(context.destinationConfig(), "basePath", "/");
        if (!base.startsWith("/")) {
            base = "/" + base;
        }
        if (base.endsWith("/")) {
            return base + context.remotePath();
        }
        return base + "/" + context.remotePath();
    }

    private static String parentDir(String path) {
        int idx = path.lastIndexOf('/');
        return idx > 0 ? path.substring(0, idx) : "/";
    }

    private static void mkdirs(ChannelSftp channel, String dir) throws Exception {
        if (dir == null || dir.isBlank() || "/".equals(dir)) {
            return;
        }
        String[] parts = dir.split("/");
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            current.append('/').append(part);
            try {
                channel.cd(current.toString());
            } catch (Exception e) {
                channel.mkdir(current.toString());
            }
        }
    }
}
