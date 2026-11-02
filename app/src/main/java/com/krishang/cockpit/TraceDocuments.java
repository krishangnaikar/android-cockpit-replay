package com.krishang.cockpit;

import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Calls are serialized across Activity recreation and repeated selections. */
final class TraceDocuments {
    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    final ReplayTrace trace;
    final String key;

    private TraceDocuments(ReplayTrace trace, String key) {
        this.trace = trace;
        this.key = key;
    }

    static TraceDocuments demo() {
        return new TraceDocuments(ReplayTrace.demo(), "demo");
    }

    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), "selected-trace.csv"));
    }

    static TraceDocuments restore(Context context) throws IOException {
        AtomicFile saved = file(context);
        try (InputStream source = saved.openRead()) {
            return parse(readBytes(source));
        } catch (java.io.FileNotFoundException missing) {
            if (saved.getBaseFile().exists()) throw missing;
            return demo();
        }
    }

    static TraceDocuments reset(Context context) throws IOException {
        AtomicFile saved = file(context);
        saved.delete();
        if (saved.getBaseFile().exists()) throw new IOException("Could not clear the selected trace.");
        return demo();
    }

    static TraceDocuments importUri(Context context, Uri uri) throws IOException {
        byte[] bytes;
        try (InputStream source = context.getContentResolver().openInputStream(uri)) {
            if (source == null) throw new IOException("The document provider could not open the file.");
            bytes = readBytes(source);
        }
        TraceDocuments selection = parse(bytes);
        AtomicFile saved = file(context);
        FileOutputStream dest = saved.startWrite();
        try {
            dest.write(bytes);
            saved.finishWrite(dest);
        } catch (IOException failure) {
            saved.failWrite(dest);
            throw failure;
        }
        return selection;
    }

    private static byte[] readBytes(InputStream source) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = source.read(buffer)) != -1) {
            if (bytes.size() + count > TraceCsv.MAX_BYTES) throw new IOException("Trace exceeds 256 KiB.");
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }

    private static TraceDocuments parse(byte[] bytes) throws IOException {
        ReplayTrace trace = TraceCsv.read(new InputStreamReader(
                new ByteArrayInputStream(bytes), StandardCharsets.ISO_8859_1));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return new TraceDocuments(trace, android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
