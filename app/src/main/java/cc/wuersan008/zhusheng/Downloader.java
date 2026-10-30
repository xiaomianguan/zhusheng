package cc.wuersan008.zhusheng;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Tiny downloader that falls back to plain HTTP.
 *
 * <p>The app runs on devices whose trust store is broken - that is the whole point of
 * the app - so a download from e.g. letsencrypt.org can fail with a normal certificate
 * validation error. The attempts therefore run in the order that costs the least
 * trust: the URL as given with its certificate validated, the same URL accepting any
 * certificate, the same URL over plain HTTP, and finally the plain-HTTP copy the source
 * carries for the hosts that answer only over https. A device with an Android 4.0 TLS
 * stack cannot complete the handshake these hosts want at all, so for it the plain
 * HTTP attempts are the ones that work.</p>
 *
 * <p>What makes the download trustworthy is none of that: the payload is checked
 * afterwards, root by root, against the fingerprints the source carries (see
 * {@code MainActivity#verify}), so a tampered download cannot install a forged
 * certificate.</p>
 */
public final class Downloader {

    private Downloader() {
    }

    /**
     * Downloads {@code url}, falling back on {@code httpUrl} when the host behind
     * {@code url} can only be reached over https.
     */
    public static byte[] download(String url, String httpUrl) throws IOException {
        IOException failure;
        try {
            return open(url, false);
        } catch (IOException firstAttempt) {
            failure = firstAttempt;
        }
        if (url.startsWith("https://")) {
            try {
                return open(url, true);
            } catch (IOException secondAttempt) {
                failure = secondAttempt;
            }
        }
        final String plain = plainHttp(url);
        if (plain != null) {
            try {
                return open(plain, false);
            } catch (IOException thirdAttempt) {
                failure = thirdAttempt;
            }
        }
        if (httpUrl != null && !httpUrl.equals(plain)) {
            try {
                return open(httpUrl, false);
            } catch (IOException fourthAttempt) {
                failure = fourthAttempt;
            }
        }
        throw failure;
    }

    /** The same URL over plain HTTP, or {@code null} when it already is plain HTTP. */
    private static String plainHttp(String url) {
        return url.startsWith("https://")
                ? "http://" + url.substring("https://".length())
                : null;
    }

    private static byte[] open(String url, boolean trustAll) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(40000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent",
                    "zhusheng/" + BuildConfig.VERSION_NAME
                            + " (Android; root certificate installer)");
            conn.setRequestProperty("Accept", "*/*");

            if (trustAll && conn instanceof HttpsURLConnection) {
                HttpsURLConnection https = (HttpsURLConnection) conn;
                https.setSSLSocketFactory(trustAllFactory());
                https.setHostnameVerifier(TRUST_ALL_HOSTS);
            }

            conn.connect();
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }

            InputStream in = conn.getInputStream();
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    bos.write(buffer, 0, n);
                }
                return bos.toByteArray();
            } finally {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static SSLSocketFactory trustAllFactory() throws IOException {
        try {
            TrustManager[] trustManagers = new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }};
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers, new SecureRandom());
            return context.getSocketFactory();
        } catch (Exception e) {
            throw new IOException("SSL init failed: " + e);
        }
    }

    private static final HostnameVerifier TRUST_ALL_HOSTS = new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };
}
