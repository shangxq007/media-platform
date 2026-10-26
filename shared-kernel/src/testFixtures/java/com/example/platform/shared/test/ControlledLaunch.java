package com.example.platform.shared.test;

import java.io.DataInputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Mechanical Job binding. Authentication belongs to the protected OS/CI launcher. */
final class ControlledLaunch {
    record JobContext(String run, String attempt, String launch, String digest) {
        JobContext {
            if (run==null || attempt==null || !run.matches("[a-z][a-z0-9_]{0,19}") || !attempt.matches("[a-z][a-z0-9_]{0,19}")
                    || launch==null || !launch.matches("[a-f0-9]{32}") || digest==null || !digest.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("JOB_CONTEXT_INVALID");
        }
    }
    static final class Context {
        final RunConnectionSpec spec;
        final String credential;
        private Context(RunConnectionSpec spec,String credential) { this.spec=spec;this.credential=credential; }
    }
    private static Context context;
    private static boolean attempted;
    private static boolean secretChannelClosed;
    static synchronized void initialize(String[] args) throws Exception {
        if (attempted) throw new IllegalStateException("HANDOFF_ALREADY_CONSUMED");
        attempted=true;
        byte[] spec,credential;
        // Spec owns fd 0; stderr/fd 2 remains untouched. Secret uses an explicitly inherited pipe fd.
        try (var input=new DataInputStream(new FileInputStream(FileDescriptor.in));
             var password=openSecretChannel(args)) {
            if (args.length!=7) throw new IllegalArgumentException("JOB_ARGUMENTS");
            for (String name:System.getenv().keySet()) {
                if (name.startsWith("MEDIA_PLATFORM_TEST_") || name.startsWith("PG") || name.equals("DATABASE_URL"))
                    throw new IllegalArgumentException("ENV_OVERRIDE_REJECTED");
            }
            spec=readFrame(input,"SPEC"); credential=readFrame(password,"CREDENTIAL");
        } finally { System.setIn(java.io.InputStream.nullInputStream()); }
        if (FileDescriptor.in.valid() || !secretChannelClosed) throw new IllegalStateException("CHANNEL_NOT_CLOSED");
        JobContext job=new JobContext(args[0],args[1],args[2],args[3]);
        String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(spec));
        if (!actual.equals(job.digest())) throw new IllegalArgumentException("JOB_SPEC_DIGEST_MISMATCH");
        RunConnectionSpec parsed=RunConnectionSpec.parse(utf8(spec),job);
        try {
            byte[] header=("MPSECRET1\0"+job.launch()+"\0").getBytes(StandardCharsets.US_ASCII);
            if (credential.length<=header.length || credential.length>header.length+1024)
                throw new IllegalArgumentException("CREDENTIAL_CONTEXT_MISMATCH");
            for (int i=0;i<header.length;i++) if (credential[i]!=header[i])
                throw new IllegalArgumentException("CREDENTIAL_CONTEXT_MISMATCH");
            String secret=utf8(java.util.Arrays.copyOfRange(credential,header.length,credential.length));
            if (secret.isEmpty() || secret.chars().anyMatch(c -> c<32 || c==127))
                throw new IllegalArgumentException("CREDENTIAL_FORMAT");
            context=new Context(parsed,secret);
        } finally { java.util.Arrays.fill(credential,(byte)0); }
    }
    private static DataInputStream openSecretChannel(String[] args) throws Exception {
        if (args.length!=7 || !args[5].matches("[0-9]{1,7}")) throw new IllegalArgumentException("CREDENTIAL_DESCRIPTOR");
        int descriptor=Integer.parseInt(args[5]);
        if (descriptor<3) throw new IllegalArgumentException("CREDENTIAL_DESCRIPTOR");
        Path path=Path.of("/proc/self/fd/"+descriptor);
        if (!args[6].startsWith("pipe:[") || !args[6].endsWith("]")) throw new IllegalArgumentException("CREDENTIAL_DESCRIPTOR");
        if (!Files.readSymbolicLink(path).toString().equals(args[6])) throw new IllegalArgumentException("CREDENTIAL_DESCRIPTOR");
        FileInputStream duplicate=new FileInputStream(path.toFile());
        try {
            // Use a supported JDK FFM downcall to close the ACTUAL inherited descriptor.
            // The duplicate is owned by try-with-resources and closed on every read outcome.
            var linker=Linker.nativeLinker();
            var address=linker.defaultLookup().find("close").orElseThrow();
            var close=linker.downcallHandle(address,FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.JAVA_INT));
            int result;
            try { result=(int)close.invokeExact(descriptor); }
            catch (Throwable error) { throw new IllegalArgumentException("CREDENTIAL_CLOSE"); }
            if (result!=0) throw new IllegalArgumentException("CREDENTIAL_CLOSE");
            secretChannelClosed=true;
            return new DataInputStream(duplicate);
        } catch (Exception error) { duplicate.close(); throw error; }
    }
    static boolean channelsClosed() { return secretChannelClosed && !FileDescriptor.in.valid(); }
    private static String utf8(byte[] data) throws Exception {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
    }
    private static byte[] readFrame(DataInputStream in,String label) throws IOException {
        int length;
        try { length=in.readInt(); } catch (IOException e) { throw new IllegalArgumentException(label+"_FRAME_MISSING"); }
        if (length<1 || length>4096) throw new IllegalArgumentException(label+"_FRAME_SIZE");
        byte[] bytes=in.readNBytes(length);
        if (bytes.length!=length) throw new IllegalArgumentException(label+"_FRAME_TRUNCATED");
        if (in.read()!=-1) throw new IllegalArgumentException(label+"_FRAME_REPLAY_OR_EXTRA");
        return bytes;
    }
    static synchronized Context current() {
        if (context==null) throw new IllegalStateException("CONTROLLED_LAUNCH_REQUIRED");
        return context;
    }
}
