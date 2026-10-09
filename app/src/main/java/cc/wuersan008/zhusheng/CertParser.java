package cc.wuersan008.zhusheng;

import java.io.ByteArrayInputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Parses certificate payloads in either PEM or DER form.
 *
 * <p>PEM files may contain several certificates (a bundle) as well as unrelated
 * comment lines (the Google bundle starts each entry with {@code #} lines); only
 * the {@code -----BEGIN CERTIFICATE-----} blocks are extracted.</p>
 */
public final class CertParser {

    private static final String BEGIN = "-----BEGIN CERTIFICATE-----";
    private static final String END = "-----END CERTIFICATE-----";

    private CertParser() {
    }

    public static List<X509Certificate> parse(byte[] data) {
        List<X509Certificate> out = new ArrayList<X509Certificate>();
        if (data == null || data.length == 0) {
            return out;
        }

        String text = null;
        try {
            text = new String(data, "ISO-8859-1");
        } catch (Exception ignored) {
        }

        if (text != null && text.indexOf(BEGIN) >= 0) {
            parsePem(text, out);
        } else {
            parseDer(data, out);
        }
        return out;
    }

    private static void parsePem(String text, List<X509Certificate> out) {
        CertificateFactory cf;
        try {
            cf = CertificateFactory.getInstance("X.509");
        } catch (Exception e) {
            return;
        }
        int index = 0;
        while (true) {
            int start = text.indexOf(BEGIN, index);
            if (start < 0) {
                break;
            }
            int end = text.indexOf(END, start);
            if (end < 0) {
                break;
            }
            end += END.length();
            String block = text.substring(start, end);
            index = end;
            try {
                byte[] blockBytes = block.getBytes("ISO-8859-1");
                X509Certificate cert =
                        (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(blockBytes));
                out.add(cert);
            } catch (Exception ignored) {
                // Skip malformed block and continue with the rest of the bundle.
            }
        }
    }

    private static void parseDer(byte[] data, List<X509Certificate> out) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> collection =
                    cf.generateCertificates(new ByteArrayInputStream(data));
            for (Certificate c : collection) {
                if (c instanceof X509Certificate) {
                    out.add((X509Certificate) c);
                }
            }
        } catch (Exception e) {
            try {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                out.add((X509Certificate) cf.generateCertificate(new ByteArrayInputStream(data)));
            } catch (Exception ignored) {
                // Not a certificate.
            }
        }
    }
}
