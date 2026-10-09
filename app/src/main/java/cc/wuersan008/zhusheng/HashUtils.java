package cc.wuersan008.zhusheng;

import android.util.Base64;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Locale;

/**
 * Certificate helpers: PEM/DER encoding, SHA-256 fingerprints and the OpenSSL
 * "subject hash (old)" value that Android uses as the file name for certificates
 * stored in {@code /system/etc/security/cacerts}.
 */
public final class HashUtils {

    private HashUtils() {
    }

    /**
     * Reproduces {@code openssl x509 -subject_hash_old}. Android names the files
     * in its system CA directory {@code <hash>.<n>} where {@code hash} is this
     * value: the little-endian first four bytes of the MD5 of the DER encoded
     * subject name, formatted as eight lower-case hex digits.
     */
    public static String subjectHashOld(X509Certificate cert) {
        try {
            byte[] encodedSubject = cert.getSubjectX500Principal().getEncoded();
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(encodedSubject);
            int value = (digest[0] & 0xff)
                    | ((digest[1] & 0xff) << 8)
                    | ((digest[2] & 0xff) << 16)
                    | ((digest[3] & 0xff) << 24);
            return String.format(Locale.US, "%08x", value);
        } catch (Exception e) {
            return "00000000";
        }
    }

    /** Lower-case hex SHA-256 fingerprint of the certificate (DER encoded). */
    public static String sha256Hex(X509Certificate cert) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(cert.getEncoded());
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format(Locale.US, "%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Encodes a certificate as a PEM block (base64 wrapped at 64 columns). */
    public static String toPem(X509Certificate cert) {
        try {
            String b64 = Base64.encodeToString(cert.getEncoded(), Base64.NO_WRAP);
            StringBuilder sb = new StringBuilder();
            sb.append("-----BEGIN CERTIFICATE-----\n");
            for (int i = 0; i < b64.length(); i += 64) {
                sb.append(b64, i, Math.min(i + 64, b64.length())).append('\n');
            }
            sb.append("-----END CERTIFICATE-----\n");
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The base64 body of the certificate, on one line - the part of a PEM block that is
     * the certificate itself.
     *
     * <p>Two files that hold the same certificate hold the same DER, and the same DER
     * encodes to the same base64, which makes this the way to compare a certificate with
     * one inside a file whose formatting is unknown. The store's own files are not byte
     * for byte comparable: a preinstalled root usually carries an
     * {@code openssl x509 -text} dump after its PEM block.
     */
    public static String base64(X509Certificate cert) {
        try {
            return Base64.encodeToString(cert.getEncoded(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }
}
