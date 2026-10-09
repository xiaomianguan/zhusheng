package cc.wuersan008.zhusheng;

import java.io.File;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the certificates a device already trusts, so that an install can leave them
 * alone.
 *
 * <p>Both stores are visible through the {@code AndroidCAStore} key store: the
 * preinstalled roots in {@code /system/etc/security/cacerts} and the ones the user
 * added, which is where this app puts its certificates. Asking it means a second run
 * of the installer only asks for the roots that are genuinely missing, and it is also
 * how the app finds out what a bulk install actually managed to add.
 *
 * <p>The two stores are told apart by the prefix of the alias they come back under,
 * {@code system:} and {@code user:}, which the trust store itself sets. That
 * distinction is what {@link #systemTrusted} is about: a certificate the SYSTEM store
 * already holds cannot be installed into the user store at all, because Android keeps
 * one copy of a certificate and reports success either way.
 */
public final class TrustedRoots {

    /**
     * The alias prefix {@code TrustedCertificateStore} gives the certificates of
     * {@code /system/etc/security/cacerts} (the user store's is {@code user:}).
     */
    private static final String SYSTEM_PREFIX = "system:";

    private TrustedRoots() {
    }

    /** SHA-256 fingerprints of every CA the device trusts at this moment. */
    public static Set<String> fingerprints() {
        return fingerprints(null);
    }

    /**
     * SHA-256 fingerprints of the certificates that live in the SYSTEM store, which are
     * the ones Android will not also keep in the user store.
     */
    public static Set<String> systemFingerprints() {
        return fingerprints(SYSTEM_PREFIX);
    }

    /**
     * The certificates of {@code certs} that the SYSTEM store already holds.
     *
     * <p>These are the ones a USER store install cannot do anything about. Android
     * ties its two stores together by content: {@code
     * TrustedCertificateStore#installCertificate} looks the certificate up in the
     * system store first and returns without writing anything when it is already
     * there, and the certificate installer reports success all the same. So the run
     * looks like it worked, the certificate shows up under the system roots of the
     * settings instead of the user ones, and no message anywhere says why. This is how
     * a caller finds out beforehand, so it can say so instead of leaving an empty user
     * store behind (see {@code MainActivity#startUserStoreInstall}).
     */
    public static List<X509Certificate> systemTrusted(List<X509Certificate> certs) {
        Set<String> system = systemFingerprints();
        Set<String> seen = new HashSet<String>();
        List<X509Certificate> result = new ArrayList<X509Certificate>();
        for (X509Certificate cert : certs) {
            String fingerprint = HashUtils.sha256Hex(cert);
            if (fingerprint.length() == 0 || !system.contains(fingerprint)) {
                continue;
            }
            if (seen.add(fingerprint)) {
                result.add(cert);
            }
        }
        return result;
    }

    /**
     * SHA-256 fingerprints of the certificates the store holds under aliases that start
     * with {@code prefix}, or of every certificate it holds when {@code prefix} is
     * {@code null}.
     *
     * <p>An entry whose certificate cannot be read is skipped rather than fatal. A
     * device that this app has been used on holds certificates of every shape the
     * providers publish, and one unreadable entry must not hide the rest of them - a
     * fingerprint that never made it into the set would send an install after a
     * certificate the device already trusts.
     */
    private static Set<String> fingerprints(String prefix) {
        Set<String> result = new HashSet<String>();
        try {
            KeyStore store = KeyStore.getInstance("AndroidCAStore");
            store.load(null, null);
            Enumeration<String> aliases = store.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (prefix != null && !alias.startsWith(prefix)) {
                    continue;
                }
                try {
                    Certificate cert = store.getCertificate(alias);
                    if (cert instanceof X509Certificate) {
                        String fingerprint = HashUtils.sha256Hex((X509Certificate) cert);
                        if (fingerprint.length() > 0) {
                            result.add(fingerprint);
                        }
                    }
                } catch (Exception e) {
                    // One entry the store cannot hand out does not hide the others.
                }
            }
        } catch (Exception e) {
            // A device whose store cannot be read simply gets nothing skipped.
        }
        return result;
    }

    /**
     * The certificates of {@code certs} that the device does not trust yet.
     *
     * <p>Duplicates are dropped on the way: the sources overlap - the Mozilla bundle
     * already carries several of the single providers - and the installer has no use
     * for the same certificate twice.
     */
    public static List<X509Certificate> missing(List<X509Certificate> certs) {
        Set<String> trusted = fingerprints();
        Set<String> seen = new HashSet<String>();
        List<X509Certificate> result = new ArrayList<X509Certificate>();
        for (X509Certificate cert : certs) {
            String fingerprint = HashUtils.sha256Hex(cert);
            if (fingerprint.length() == 0 || trusted.contains(fingerprint)) {
                continue;
            }
            if (seen.add(fingerprint)) {
                result.add(cert);
            }
        }
        return result;
    }

    /**
     * How many files the SYSTEM store holds that this app is not allowed to read.
     *
     * <p>A root the store lists but no app may open has a confusing symptom: every app
     * takes the certificate for absent, the certificate installer - which runs as the
     * system and can read the file - refuses to add it because it is already there, and
     * the install therefore finishes having done nothing at all. The cause is a file
     * whose permissions are not the ones the preinstalled roots carry, which a stray
     * {@code adb push} or a half-done install leaves behind.
     *
     * <p>Counting them needs no root: the directory itself is world readable, so the
     * names are visible even for a file whose contents are not.
     */
    public static int unreadableSystemRoots() {
        String dir = CertInstaller.SYSTEM_CACERTS_DIR;
        String[] names = new File(dir).list();
        if (names == null) {
            return 0;
        }
        int count = 0;
        for (String name : names) {
            if (isRootFileName(name) && !new File(dir, name).canRead()) {
                count++;
            }
        }
        return count;
    }

    /** True for the {@code <subject_hash_old>.<n>} names the store's roots carry. */
    private static boolean isRootFileName(String name) {
        if (name.length() < 10 || name.charAt(8) != '.') {
            return false;
        }
        for (int i = 0; i < 8; i++) {
            char c = name.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        for (int i = 9; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
