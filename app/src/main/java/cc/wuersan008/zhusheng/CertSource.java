package cc.wuersan008.zhusheng;

import java.util.Set;

/**
 * Describes a single downloadable certificate source: either a bundle that holds
 * several certificates or a single root certificate published by a CA.
 */
public class CertSource {

    public static final int STATE_IDLE = 0;
    public static final int STATE_DOWNLOADING = 1;
    public static final int STATE_DONE = 2;
    public static final int STATE_ERROR = 3;

    /** Human readable provider name shown in the list. */
    public final String name;

    /** Official download URL. */
    public final String url;

    /**
     * Plain-HTTP copy of {@link #url}, or {@code null} when the host of {@link #url}
     * itself answers plain HTTP - the downloader simply retries there. A device with
     * an Android 4.0 TLS stack cannot complete the handshake these hosts require, so
     * for it plain HTTP is the only way left to the file; the fingerprints below are
     * what keeps that safe.
     */
    public final String httpUrl;

    /** Short note describing what the source contains. */
    public final String note;

    /** True when the source belongs to the recommended minimal set. */
    public final boolean recommended;

    /**
     * Lower-case SHA-256 fingerprint (no separators) of the single certificate
     * expected at {@link #url}, or {@code null} for bundles (which hold many
     * certificates and cannot be pinned to a single fingerprint).
     */
    public final String expectedSha256;

    /**
     * The fingerprints a bundle may hold, or {@code null} for a single-certificate
     * source. A bundle is accepted root by root against this set, so a payload
     * fetched over plain HTTP cannot smuggle in a root of its own (see
     * {@link KnownRoots}).
     */
    public final Set<String> knownSha256;

    // ---- mutable runtime state -------------------------------------------------

    public boolean selected;

    public volatile int state = STATE_IDLE;
    public volatile String message = "";
    public volatile int certCount = 0;

    public CertSource(String name, String url, String httpUrl, String note, boolean recommended,
                      String expectedSha256, Set<String> knownSha256) {
        this.name = name;
        this.url = url;
        this.httpUrl = httpUrl;
        this.note = note;
        this.recommended = recommended;
        this.expectedSha256 = expectedSha256;
        this.knownSha256 = knownSha256;
        this.selected = recommended;
    }

    public void reset() {
        state = STATE_IDLE;
        message = "";
        certCount = 0;
    }
}
