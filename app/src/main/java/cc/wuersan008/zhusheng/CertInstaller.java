package cc.wuersan008.zhusheng;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.security.KeyChain;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Installs certificates into either the SYSTEM store (root required) or the
 * USER store (no root, uses the Android certificate installer), and takes them
 * out again.
 */
public final class CertInstaller {

    /** Where Android keeps the system trusted roots on 4.0 - 6.0. */
    public static final String SYSTEM_CACERTS_DIR = "/system/etc/security/cacerts";

    /** Where Android keeps the certificates the user added, on 4.0 - 6.0. */
    public static final String USER_CACERTS_DIR = "/data/misc/keychain/cacerts-added";

    /** The same directory on 7.0 and newer, where it moved below the user. */
    public static final String USER_CACERTS_DIR_MULTIUSER = "/data/misc/user/0/cacerts-added";

    /**
     * Records the files this app copied into the SYSTEM store, one name per line.
     *
     * <p>Removing needs it: a stock root and a root of the same bundle can be the very
     * same bytes, so content alone cannot tell a file this app added from one the
     * device shipped with. A file it did not put there is never touched.
     */
    public static final String SYSTEM_MANIFEST = "system-installed.txt";

    private CertInstaller() {
    }

    public static class InstallReport {
        public int requested;
        public int copied;
        public int removed;
        /**
         * Redundant copies of a root the store already held, deleted by a removal. The
         * install used to leave one behind for every preinstalled root whose file
         * carries a text dump (see {@link #appendDedupe}).
         */
        public int deduped;
        public boolean rootOk;
        public String log = "";
    }

    /**
     * A shell function that prints the base64 body of the first PEM block of a file.
     *
     * <p>This is how a certificate is compared with one inside a store file: the files
     * are not byte for byte comparable, because a preinstalled root usually carries an
     * {@code openssl x509 -text} dump after its PEM block, while the base64 of the
     * certificate itself is the same whatever the file looks like around it. The
     * function is defined inside the script rather than calling an external tool,
     * because the devices this app runs on do not all have {@code openssl}.
     */
    private static final String PEM_B64_FUNCTION =
            "pem_b64() {\n"
            + "  sed -n '/-----BEGIN CERTIFICATE-----/,/-----END CERTIFICATE-----/"
            + "{/-----BEGIN CERTIFICATE-----/d;/-----END CERTIFICATE-----/d;p}' \"$1\""
            + " 2>/dev/null | tr -d ' \\t\\r\\n'\n"
            + "}\n";

    /**
     * Copies the certificates the store does not already hold into
     * {@link #SYSTEM_CACERTS_DIR} using root. Each one is written as a PEM file into
     * the app's private cache directory first and then copied into place by a root
     * shell, named {@code <subject_hash_old>.<n>} exactly like the preinstalled roots,
     * and the names it added are recorded in {@link #SYSTEM_MANIFEST} so that
     * {@link #removeFromSystemStore} can take back exactly those.
     *
     * <p>The shell script is written to a file and only that file is handed to
     * {@code su}. A batch of certificates makes the script tens of kilobytes long,
     * and a command line that size is not portable: an {@code su} that passes the
     * command through a socket - the one on CyanogenMod 13, for instance - refuses
     * anything past a few kilobytes without a word, so the install would look like
     * it ran while nothing at all happened. Through a file the command stays three
     * words long and the size of the script stops mattering.
     */
    public static InstallReport installToSystemStore(Context ctx, List<X509Certificate> certs) {
        InstallReport report = new InstallReport();
        report.requested = certs.size();

        File dir = new File(ctx.getCacheDir(), "cacerts");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // Root appends to the record; the app itself only makes sure the file is there,
        // so that the first append has somewhere to go.
        File manifest = new File(ctx.getFilesDir(), SYSTEM_MANIFEST);
        if (!manifest.exists()) {
            writeFile(manifest, "");
        }

        StringBuilder script = new StringBuilder();
        // Remount /system read-write (covers the common layouts used on 4.x - 6.x).
        script.append("mount -o rw,remount /system 2>/dev/null\n");
        script.append("mount -o rw,remount -t yaffs2 /dev/block/mtdblock3 /system 2>/dev/null\n");
        script.append("mount -o rw,remount / 2>/dev/null\n");
        script.append("DIR=").append(SYSTEM_CACERTS_DIR).append('\n');
        script.append("mkdir -p $DIR\n");
        script.append(PEM_B64_FUNCTION);
        // Counted here rather than assumed by the caller: a copy can fail for
        // reasons only the shell sees - no space left on /system, a read-only
        // remount that did not take - and then the report has to say so.
        script.append("COPIED=0\n");

        int index = 0;
        for (X509Certificate cert : certs) {
            String pem = HashUtils.toPem(cert);
            if (pem == null) {
                continue;
            }
            String hash = HashUtils.subjectHashOld(cert);
            File tmp = new File(dir, "cert_" + index + "_" + hash + ".pem");
            if (!writeFile(tmp, pem)) {
                continue;
            }
            // A root the store already holds is left where it is: a second copy
            // would only mean a second copy to remove later. What is compared is the
            // certificate, not the file: the store's own copy of a root is usually the
            // PEM block plus an openssl text dump, so it never matched byte for byte -
            // and that mismatch is what used to put a second copy of every such root
            // into the store.
            script.append("H=").append(hash).append('\n');
            script.append("WANT='").append(HashUtils.base64(cert)).append("'\n");
            script.append("SKIP=0\n");
            script.append("for N in 0 1 2 3 4 5 6 7 8 9; do\n");
            script.append("  [ -f $DIR/$H.$N ] || continue\n");
            script.append("  [ -n \"$WANT\" ] && [ \"$(pem_b64 $DIR/$H.$N)\" = \"$WANT\" ]")
                    .append(" && SKIP=1 && break\n");
            script.append("done\n");
            script.append("if [ $SKIP -eq 0 ]; then\n");
            // Find the next free <hash>.<n> slot and copy the PEM into place.
            script.append("  N=0\n");
            script.append("  while [ -e $DIR/$H.$N ]; do N=$((N+1)); done\n");
            script.append("  cp '").append(tmp.getAbsolutePath()).append("' $DIR/$H.$N\n");
            script.append("  [ -f $DIR/$H.$N ] && COPIED=$((COPIED+1)) && echo \"$H.$N\" >> '")
                    .append(manifest.getAbsolutePath()).append("'\n");
            script.append("  chmod 644 $DIR/$H.$N\n");
            script.append("  chown 0:0 $DIR/$H.$N 2>/dev/null\n");
            script.append("  chcon u:object_r:system_file:s0 $DIR/$H.$N 2>/dev/null\n");
            script.append("fi\n");
            index++;
        }

        // Restore read-only mounts.
        script.append("mount -o ro,remount /system 2>/dev/null\n");
        script.append("mount -o ro,remount / 2>/dev/null\n");
        script.append("echo ZHUSHENG_COPIED=$COPIED\n");
        script.append("echo ZHUSHENG_DONE\n");

        // The script goes into the app's private cache directory, which is readable
        // by root; only its path is passed to su, so the command stays short.
        File scriptFile = new File(ctx.getCacheDir(), "install.sh");
        if (!writeFile(scriptFile, script.toString())) {
            report.log = "could not write " + scriptFile.getAbsolutePath();
            return report;
        }

        RootUtils.Result result = RootUtils.runRoot("sh '" + scriptFile.getAbsolutePath() + "'");
        report.log = result.output;
        report.rootOk = result.output.contains("ZHUSHENG_DONE");
        report.copied = countedFrom(result.output, "ZHUSHENG_COPIED=", index);
        return report;
    }

    /** The number a script reported for {@code marker}, or {@code fallback} without it. */
    private static int countedFrom(String output, String marker, int fallback) {
        for (String line : output.split("\n")) {
            if (line.startsWith(marker)) {
                try {
                    return Integer.parseInt(line.substring(marker.length()).trim());
                } catch (NumberFormatException e) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    // ------------------------------------------------------------------ uninstall

    /**
     * Removes the certificates this app copied into the SYSTEM store, and only those.
     *
     * <p>What to remove is read back from {@link #SYSTEM_MANIFEST} instead of being
     * worked out from the store itself: a root the device shipped with and the same
     * root inside one of the bundles are the same bytes, so the files alone cannot
     * tell one from the other, and a preinstalled root must never be deleted. Every
     * name is checked against the certificate it is meant to hold before it goes.
     *
     * <p>As with the install, the script is written to a file and only that file is
     * handed to {@code su}: a batch of certificates makes it tens of kilobytes long,
     * and an {@code su} that passes the command through a socket refuses a command
     * line of that size without a word.
     */
    public static InstallReport removeFromSystemStore(Context ctx, List<X509Certificate> certs) {
        InstallReport report = new InstallReport();
        report.requested = certs.size();

        File dir = new File(ctx.getCacheDir(), "cacerts");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File manifest = new File(ctx.getFilesDir(), SYSTEM_MANIFEST);
        if (!manifest.exists()) {
            // Nothing was ever recorded, so there is nothing this app may take back.
            report.log = "no " + SYSTEM_MANIFEST + " in " + ctx.getFilesDir().getAbsolutePath();
            return report;
        }

        StringBuilder script = new StringBuilder();
        script.append("mount -o rw,remount /system 2>/dev/null\n");
        script.append("mount -o rw,remount -t yaffs2 /dev/block/mtdblock3 /system 2>/dev/null\n");
        script.append("mount -o rw,remount / 2>/dev/null\n");
        script.append("DIR=").append(SYSTEM_CACERTS_DIR).append('\n');
        script.append("MAN='").append(manifest.getAbsolutePath()).append("'\n");
        script.append("REMOVED=0\n");
        script.append("DEDUPED=0\n");
        script.append(PEM_B64_FUNCTION);
        // The redundant copies go first, so that the record is tidied afterwards with
        // their names gone from the store already.
        appendDedupe(script, "$DIR");
        appendRemoval(script, dir, certs, "$DIR", true);
        script.append("mount -o ro,remount /system 2>/dev/null\n");
        script.append("mount -o ro,remount / 2>/dev/null\n");
        script.append("echo ZHUSHENG_REMOVED=$REMOVED\n");
        script.append("echo ZHUSHENG_DEDUPED=$DEDUPED\n");
        script.append("echo ZHUSHENG_DONE\n");

        return runRemoval(ctx, script, "uninstall.sh", report);
    }

    /**
     * Adds the shell that deletes the redundant copies of a root the store holds more
     * than once.
     *
     * <p>A copy is redundant when the certificate inside it is the one the lowest
     * numbered file of the same subject hash already holds. That is exactly what a copy
     * this app left behind looks like: the installer used to compare the preinstalled
     * file - PEM block plus an openssl text dump - with the PEM it was about to write,
     * find them different and add the root a second time under the next free index.
     *
     * <p>Comparing the certificates is what makes this safe: two roots that merely share
     * a subject hash are different certificates and never match, so a file the device
     * shipped with is not touched unless it really is a second copy of the same root -
     * and then one copy of it stays.
     */
    private static void appendDedupe(StringBuilder script, String dirExpr) {
        script.append("for F in ").append(dirExpr).append("/*.0; do\n");
        script.append("  [ -f $F ] || continue\n");
        script.append("  B=${F##*/}\n");
        script.append("  H=${B%.0}\n");
        // Reading a file costs a couple of processes, so only the subject hashes that
        // really carry a second file are read at all - on a full store that is a few
        // dozen instead of every root in it.
        script.append("  SIB=0\n");
        script.append("  for N in 1 2 3 4 5 6 7 8 9; do\n");
        script.append("    [ -f ").append(dirExpr).append("/$H.$N ] && SIB=1 && break\n");
        script.append("  done\n");
        script.append("  [ $SIB -eq 1 ] || continue\n");
        script.append("  BASE=$(pem_b64 \"$F\")\n");
        script.append("  [ -n \"$BASE\" ] || continue\n");
        script.append("  for N in 1 2 3 4 5 6 7 8 9; do\n");
        script.append("    [ -f ").append(dirExpr).append("/$H.$N ] || continue\n");
        script.append("    if [ \"$(pem_b64 ").append(dirExpr)
                .append("/$H.$N)\" = \"$BASE\" ]; then\n");
        script.append("      rm -f ").append(dirExpr)
                .append("/$H.$N && DEDUPED=$((DEDUPED+1))\n");
        script.append("    fi\n");
        script.append("  done\n");
        script.append("done\n");
    }

    /**
     * Removes the certificates this app added to the USER store.
     *
     * <p>The user store has no API for removing a certificate and its files belong to
     * the system, so this needs root just as the SYSTEM store does. The directories
     * are read directly, and a file is deleted only when its content is one of the
     * certificates handed in: as precise as it gets without a record, and precise
     * enough, because nothing else in there came from this app.
     */
    public static InstallReport removeFromUserStore(Context ctx, List<X509Certificate> certs) {
        InstallReport report = new InstallReport();
        report.requested = certs.size();

        File dir = new File(ctx.getCacheDir(), "cacerts");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        StringBuilder script = new StringBuilder();
        script.append("REMOVED=0\n");
        script.append("for DIR in ").append(USER_CACERTS_DIR).append(' ')
                .append(USER_CACERTS_DIR_MULTIUSER).append("; do\n");
        script.append("[ -d $DIR ] || continue\n");
        appendRemoval(script, dir, certs, "$DIR", false);
        script.append("done\n");
        script.append("echo ZHUSHENG_REMOVED=$REMOVED\n");
        script.append("echo ZHUSHENG_DONE\n");

        return runRemoval(ctx, script, "uninstall-user.sh", report);
    }

    /** Hands a removal script to {@code su} and reads its count back. */
    private static InstallReport runRemoval(Context ctx, StringBuilder script, String name,
                                            InstallReport report) {
        File scriptFile = new File(ctx.getCacheDir(), name);
        if (!writeFile(scriptFile, script.toString())) {
            report.log = "could not write " + scriptFile.getAbsolutePath();
            return report;
        }
        RootUtils.Result result = RootUtils.runRoot("sh '" + scriptFile.getAbsolutePath() + "'");
        report.log = result.output;
        report.rootOk = result.output.contains("ZHUSHENG_DONE");
        report.removed = countedFrom(result.output, "ZHUSHENG_REMOVED=", 0);
        report.deduped = countedFrom(result.output, "ZHUSHENG_DEDUPED=", 0);
        return report;
    }

    /**
     * Adds the shell that deletes the copies of every certificate to {@code script}.
     *
     * <p>A copy is deleted only when it is byte for byte one of the certificates this
     * app is holding, either as a PEM block or as the raw DER - the two stores do not
     * agree on which form they keep - and, when {@code manifestDriven} is set, only
     * when its name is one the install recorded.
     */
    private static void appendRemoval(StringBuilder script, File dir, List<X509Certificate> certs,
                                      String dirExpr, boolean manifestDriven) {
        int index = 0;
        for (X509Certificate cert : certs) {
            String pem = HashUtils.toPem(cert);
            if (pem == null) {
                continue;
            }
            String hash = HashUtils.subjectHashOld(cert);
            File pemFile = new File(dir, "del_" + index + "_" + hash + ".pem");
            File derFile = new File(dir, "del_" + index + "_" + hash + ".der");
            if (!writeFile(pemFile, pem) || !writeDer(derFile, cert)) {
                continue;
            }
            script.append("H=").append(hash).append('\n');
            script.append("for N in 0 1 2 3 4 5 6 7 8 9; do\n");
            script.append("  F=").append(dirExpr).append("/$H.$N\n");
            script.append("  [ -e $F ] || continue\n");
            if (manifestDriven) {
                script.append("  grep -qx \"$H.$N\" $MAN 2>/dev/null || continue\n");
            }
            script.append("  if cmp -s $F '").append(pemFile.getAbsolutePath())
                    .append("' || cmp -s $F '").append(derFile.getAbsolutePath()).append("'; then\n");
            script.append("    rm -f $F && REMOVED=$((REMOVED+1))\n");
            script.append("  fi\n");
            script.append("done\n");
            index++;
        }
        if (manifestDriven) {
            // Drop the names that are gone, so a later run reads a truthful record.
            script.append("if [ -f $MAN ]; then\n");
            script.append("  while read -r n; do [ -n \"$n\" ] && [ -e ").append(dirExpr)
                    .append("/$n ] && echo \"$n\"; done < $MAN > $MAN.tmp\n");
            script.append("  mv $MAN.tmp $MAN\n");
            script.append("fi\n");
        }
    }

    /** Writes the raw DER of a certificate, the other form a store may keep it in. */
    private static boolean writeDer(File file, X509Certificate cert) {
        try {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(cert.getEncoded());
            fos.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * Builds the intent that asks the Android certificate installer to add a
     * certificate to the USER store. On Android versions below 7.0 the user store
     * is trusted by every app, so this fixes HTTPS system-wide without root.
     */
    public static Intent userStoreInstallIntent(X509Certificate cert) throws Exception {
        Intent intent = KeyChain.createInstallIntent();
        intent.putExtra(KeyChain.EXTRA_CERTIFICATE, cert.getEncoded());
        intent.putExtra(KeyChain.EXTRA_NAME, commonName(cert));
        return intent;
    }

    /**
     * The prefix Android puts in front of a CA certificate inside a credentials
     * bundle. It is declared in the hidden {@code android.security.Credentials}
     * class, so it is spelled out here; the certificate installer matches on the
     * string, not on the constant.
     */
    private static final String CA_CERTIFICATE_PREFIX = "CACERT_";

    /**
     * Builds the intent that asks the Android certificate installer to add a whole
     * set of certificates to the USER store in a single operation.
     *
     * <p>A credentials bundle may hold any number of entries, one per CA, and the
     * installer walks them in one pass: that is what the prefix above is for, and it
     * saves one confirmation dialog per certificate. The values are PEM blocks, the
     * form the bundle is defined in. Returns {@code null} when there is nothing to
     * install.
     *
     * <p>The caller must not assume the bundle was accepted - an installer that does
     * not understand it ignores it and returns normally - so what is trusted
     * afterwards is read back through {@link TrustedRoots} and anything still missing
     * is installed one certificate at a time.
     */
    public static Intent userStoreBulkInstallIntent(List<X509Certificate> certs) throws Exception {
        Bundle bundle = new Bundle();
        int index = 0;
        for (X509Certificate cert : certs) {
            String pem = HashUtils.toPem(cert);
            if (pem == null) {
                continue;
            }
            bundle.putByteArray(CA_CERTIFICATE_PREFIX + index, pem.getBytes("US-ASCII"));
            index++;
        }
        if (index == 0) {
            return null;
        }
        Intent intent = KeyChain.createInstallIntent();
        intent.putExtras(bundle);
        return intent;
    }

    /** Best-effort human readable name for a certificate. */
    public static String commonName(X509Certificate cert) {
        try {
            String dn = cert.getSubjectX500Principal().getName();
            String[] parts = dn.split(",");
            for (String part : parts) {
                String p = part.trim();
                if (p.regionMatches(true, 0, "CN=", 0, 3)) {
                    return p.substring(3).trim();
                }
            }
            return dn;
        } catch (Exception e) {
            return "certificate";
        }
    }

    private static boolean writeFile(File file, String content) {
        try {
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter writer = new OutputStreamWriter(fos, "US-ASCII");
            writer.write(content);
            writer.flush();
            writer.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
