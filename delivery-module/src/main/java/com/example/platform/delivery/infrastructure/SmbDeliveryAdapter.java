package com.example.platform.delivery.infrastructure;

import com.example.platform.delivery.domain.DeliveryProtocol;
import com.example.platform.delivery.spi.DeliveryAdapter;
import com.example.platform.delivery.spi.DeliveryContext;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SmbDeliveryAdapter implements DeliveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(SmbDeliveryAdapter.class);

    @Override
    public DeliveryProtocol protocol() {
        return DeliveryProtocol.SMB;
    }

    @Override
    public ProbeResult probe(DeliveryContext context) {
        try (SmbSession session = open(context)) {
            session.share().list("");
            return ProbeResult.success();
        } catch (Exception e) {
            return ProbeResult.failure(e.getMessage());
        }
    }

    @Override
    public DeliveryResult deliver(DeliveryContext context) {
        try (SmbSession session = open(context)) {
            byte[] bytes = context.sourceStream().readAllBytes();
            String remotePath = smbPath(context);
            mkdirs(session.share(), parentPath(remotePath));
            try (com.hierynomus.smbj.share.File file = session.share().openFile(
                    remotePath,
                    EnumSet.of(AccessMask.GENERIC_WRITE),
                    EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                    EnumSet.of(SMB2ShareAccess.FILE_SHARE_WRITE),
                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                    EnumSet.noneOf(SMB2CreateOptions.class))) {
                try (OutputStream out = file.getOutputStream()) {
                    out.write(bytes);
                }
            }
            String remoteUri = "smb://" + host(context) + "/" + shareName(context) + "/" + remotePath.replace('\\', '/');
            log.info("SMB delivery job={} path={} bytes={}", context.deliveryJobId(), remotePath, bytes.length);
            return DeliveryResult.ok(context.remotePath(), remoteUri, bytes.length);
        } catch (Exception e) {
            log.warn("SMB delivery failed job={}: {}", context.deliveryJobId(), e.getMessage());
            return DeliveryResult.fail(e.getMessage());
        }
    }

    private static SmbSession open(DeliveryContext context) throws Exception {
        Map<String, Object> config = context.destinationConfig();
        Map<String, String> creds = context.credentials();
        String host = DeliveryConfigParser.stringVal(config, "host");
        String share = DeliveryConfigParser.stringVal(config, "share");
        int port = DeliveryConfigParser.intVal(config, "port", 445);
        String username = creds.getOrDefault("username", DeliveryConfigParser.stringVal(config, "username"));
        String password = creds.getOrDefault("password", "");
        String domain = creds.getOrDefault("domain", DeliveryConfigParser.stringVal(config, "domain"));
        if (host.isBlank() || share.isBlank() || username.isBlank()) {
            throw new IllegalArgumentException("SMB requires host, share, and username");
        }
        SMBClient client = new SMBClient();
        Connection connection = client.connect(host, port);
        AuthenticationContext auth = new AuthenticationContext(username, password.toCharArray(), domain);
        Session session = connection.authenticate(auth);
        DiskShare diskShare = (DiskShare) session.connectShare(share);
        return new SmbSession(client, connection, session, diskShare);
    }

    private static String smbPath(DeliveryContext context) {
        String base = DeliveryConfigParser.stringVal(context.destinationConfig(), "basePath", "");
        String rel = context.remotePath().replace('\\', '/');
        if (base.isBlank()) {
            return rel.replace('/', '\\');
        }
        String combined = base.endsWith("/") || base.endsWith("\\")
                ? base + rel
                : base + "\\" + rel;
        return combined.replace('/', '\\');
    }

    private static String parentPath(String path) {
        int idx = path.lastIndexOf('\\');
        return idx > 0 ? path.substring(0, idx) : "";
    }

    private static void mkdirs(DiskShare share, String dir) throws Exception {
        if (dir == null || dir.isBlank()) {
            return;
        }
        String[] parts = dir.split("\\\\");
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!current.isEmpty()) {
                current.append('\\');
            }
            current.append(part);
            String segment = current.toString();
            try {
                if (!share.folderExists(segment)) {
                    share.mkdir(segment);
                }
            } catch (Exception ignored) {
                share.mkdir(segment);
            }
        }
    }

    private static String host(DeliveryContext context) {
        return DeliveryConfigParser.stringVal(context.destinationConfig(), "host");
    }

    private static String shareName(DeliveryContext context) {
        return DeliveryConfigParser.stringVal(context.destinationConfig(), "share");
    }

    private record SmbSession(SMBClient client, Connection connection, Session session, DiskShare share)
            implements AutoCloseable {
        @Override
        public void close() {
            try {
                share.close();
            } catch (Exception ignored) {
                /* ignore */
            }
            try {
                session.close();
            } catch (Exception ignored) {
                /* ignore */
            }
            try {
                connection.close();
            } catch (Exception ignored) {
                /* ignore */
            }
            try {
                client.close();
            } catch (Exception ignored) {
                /* ignore */
            }
        }
    }
}
