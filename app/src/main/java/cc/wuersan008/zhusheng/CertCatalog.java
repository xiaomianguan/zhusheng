package cc.wuersan008.zhusheng;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The catalog of official root-certificate sources.
 *
 * <p>Every URL below is an official endpoint operated by the certificate
 * authority itself (or, for the Mozilla bundle, by the curl.se project which
 * republishes the Mozilla NSS trust store). What is trusted is the SHA-256
 * fingerprints, not the transport: a downloaded certificate is accepted only when
 * it matches, whether it arrived over https or over the plain-HTTP copy a source
 * carries for the hosts that answer only over https.</p>
 *
 * <p>The provider names and the notes are string resources, so they follow the
 * language chosen in the app. The catalog therefore has to be built with a
 * context that already carries that language - from an activity after
 * {@code AppLanguage.apply()}, not from a field initializer, which would run
 * before the choice is applied.</p>
 */
public final class CertCatalog {

    private CertCatalog() {
    }

    /**
     * A plain-HTTP copy of a source: a fixed Internet Archive snapshot of it, in the
     * archive's raw form, which hands back the bytes the official host served rather
     * than a rewritten page. Only the hosts that refuse plain HTTP need one; the
     * fingerprint the source carries is what makes an archived copy safe to accept.
     */
    private static String archived(String snapshot, String url) {
        return "http://web.archive.org/web/" + snapshot + "id_/" + url;
    }

    private static CertSource single(Context context, int nameRes, String url, int noteRes,
                                     boolean recommended, String sha256) {
        return single(context, nameRes, url, null, noteRes, recommended, sha256);
    }

    /** A single certificate whose host answers only over https, so it needs a copy. */
    private static CertSource single(Context context, int nameRes, String url, String httpUrl,
                                     int noteRes, boolean recommended, String sha256) {
        return new CertSource(context.getString(nameRes), url, httpUrl,
                context.getString(noteRes), recommended, sha256, null);
    }

    private static CertSource bundle(Context context, int nameRes, String url, String httpUrl,
                                     int noteRes, boolean recommended, Set<String> knownSha256) {
        return new CertSource(context.getString(nameRes), url, httpUrl,
                context.getString(noteRes), recommended, null, knownSha256);
    }

    /** Builds a fresh catalog with all sources selected according to "recommended". */
    public static List<CertSource> build(Context context) {
        List<CertSource> list = new ArrayList<CertSource>();

        // ---------------------------------------------------------------- bundles
        // The pinned copy has to hold the same roots as the live bundle, because the
        // devices that need it are exactly the ones that cannot reach curl.se at all:
        // an older snapshot silently loses whatever Mozilla added since, and those roots
        // then have no other way in. KnownRoots.MOZILLA is generated from the live
        // bundle, so when it is refreshed this pin has to be refreshed with it.
        list.add(bundle(context,
                R.string.src_mozilla_name,
                "https://curl.se/ca/cacert.pem",
                archived("20261001155516", "https://curl.se/ca/cacert.pem"),
                R.string.src_mozilla_note,
                true,
                KnownRoots.MOZILLA));

        list.add(bundle(context,
                R.string.src_goog_bundle_name,
                "https://pki.goog/roots.pem",
                null,
                R.string.src_goog_bundle_note,
                false,
                KnownRoots.GOOGLE));

        // ------------------------------------------------------------ Let's Encrypt
        list.add(single(context,
                R.string.src_isrg_x1_name,
                "https://letsencrypt.org/certs/isrgrootx1.pem",
                archived("20241230233918", "https://letsencrypt.org/certs/isrgrootx1.pem"),
                R.string.src_isrg_x1_note,
                true,
                "96bcec06264976f37460779acf28c5a7cfe8a3c0aae11a8ffcee05c0bddf08c6"));

        list.add(single(context,
                R.string.src_isrg_x2_name,
                "https://letsencrypt.org/certs/isrg-root-x2.pem",
                archived("20250114015254", "https://letsencrypt.org/certs/isrg-root-x2.pem"),
                R.string.src_isrg_x2_note,
                true,
                "69729b8e15a86efc177a57afb7171dfc64add28c2fca8cf1507e34453ccb1470"));

        // ------------------------------------------------- Google Trust Services
        list.add(single(context,
                R.string.src_gts_r1_name,
                "https://pki.goog/repo/certs/gtsr1.pem",
                R.string.src_gts_r1_note,
                true,
                "d947432abde7b7fa90fc2e6b59101b1280e0e1c7e4e40fa3c6887fff57a7f4cf"));

        list.add(single(context,
                R.string.src_gts_r2_name,
                "https://pki.goog/repo/certs/gtsr2.pem",
                R.string.src_gts_r2_note,
                true,
                "8d25cd97229dbf70356bda4eb3cc734031e24cf00fafcfd32dc76eb5841c7ea8"));

        list.add(single(context,
                R.string.src_gts_r3_name,
                "https://pki.goog/repo/certs/gtsr3.pem",
                R.string.src_gts_r3_note,
                true,
                "34d8a73ee208d9bcdb0d956520934b4e40e69482596e8b6f73c8426b010a6f48"));

        list.add(single(context,
                R.string.src_gts_r4_name,
                "https://pki.goog/repo/certs/gtsr4.pem",
                R.string.src_gts_r4_note,
                true,
                "349dfa4058c5e263123b398ae795573c4e1313c83fe68f93556cd5e8031b3c7d"));

        // -------------------------------------------------------------- Amazon
        list.add(single(context,
                R.string.src_amazon_r1_name,
                "https://www.amazontrust.com/repository/AmazonRootCA1.pem",
                archived("20250128195554",
                        "https://www.amazontrust.com/repository/AmazonRootCA1.pem"),
                R.string.src_amazon_r1_note,
                true,
                "8ecde6884f3d87b1125ba31ac3fcb13d7016de7f57cc904fe1cb97c6ae98196e"));

        list.add(single(context,
                R.string.src_amazon_r2_name,
                "https://www.amazontrust.com/repository/AmazonRootCA2.pem",
                archived("20240523002258",
                        "https://www.amazontrust.com/repository/AmazonRootCA2.pem"),
                R.string.src_amazon_r2_note,
                true,
                "1ba5b2aa8c65401a82960118f80bec4f62304d83cec4713a19c39c011ea46db4"));

        list.add(single(context,
                R.string.src_amazon_r3_name,
                "https://www.amazontrust.com/repository/AmazonRootCA3.pem",
                archived("20241015230442",
                        "https://www.amazontrust.com/repository/AmazonRootCA3.pem"),
                R.string.src_amazon_r3_note,
                true,
                "18ce6cfe7bf14e60b2e347b8dfe868cb31d02ebb3ada271569f50343b46db3a4"));

        list.add(single(context,
                R.string.src_amazon_r4_name,
                "https://www.amazontrust.com/repository/AmazonRootCA4.pem",
                archived("20250114215113",
                        "https://www.amazontrust.com/repository/AmazonRootCA4.pem"),
                R.string.src_amazon_r4_note,
                true,
                "e35d28419ed02025cfa69038cd623962458da5c695fbdea3c22b0bfb25897092"));

        // ------------------------------------------------------------- DigiCert
        list.add(single(context,
                R.string.src_digicert_global_name,
                "https://cacerts.digicert.com/DigiCertGlobalRootCA.crt.pem",
                R.string.src_digicert_global_note,
                true,
                "4348a0e9444c78cb265e058d5e8944b4d84f9662bd26db257f8934a443c70161"));

        list.add(single(context,
                R.string.src_digicert_g2_name,
                "https://cacerts.digicert.com/DigiCertGlobalRootG2.crt.pem",
                R.string.src_digicert_g2_note,
                true,
                "cb3ccbb76031e5e0138f8dd39a23f9de47ffc35e43c1144cea27d46a5ab1cb5f"));

        list.add(single(context,
                R.string.src_digicert_g3_name,
                "https://cacerts.digicert.com/DigiCertGlobalRootG3.crt.pem",
                R.string.src_digicert_g3_note,
                true,
                "31ad6648f8104138c738f39ea4320133393e3a18cc02296ef97c2ac9ef6731d0"));

        list.add(single(context,
                R.string.src_digicert_g4_name,
                "https://cacerts.digicert.com/DigiCertTrustedRootG4.crt.pem",
                R.string.src_digicert_g4_note,
                true,
                "552f7bdcf1a7af9e6ce672017f4f12abf77240c78e761ac203d1d9d20ac89988"));

        list.add(single(context,
                R.string.src_digicert_ev_name,
                "https://cacerts.digicert.com/DigiCertHighAssuranceEVRootCA.crt.pem",
                R.string.src_digicert_ev_note,
                true,
                "7431e5f4c3c1ce4690774f0b61e05440883ba9a01ed00ba6abd7806ed3b118cf"));

        // ----------------------------------------------------------- GlobalSign
        list.add(single(context,
                R.string.src_globalsign_r1_name,
                "https://secure.globalsign.com/cacert/root-r1.crt",
                R.string.src_globalsign_r1_note,
                true,
                "ebd41040e4bb3ec742c9e381d31ef2a41a48b6685c96e7cef3c1df6cd4331c99"));

        list.add(single(context,
                R.string.src_globalsign_r3_name,
                "https://secure.globalsign.com/cacert/root-r3.crt",
                R.string.src_globalsign_r3_note,
                true,
                "cbb522d7b7f127ad6a0113865bdf1cd4102e7d0759af635a7cf4720dc963c53b"));

        list.add(single(context,
                R.string.src_globalsign_r6_name,
                "https://secure.globalsign.com/cacert/root-r6.crt",
                R.string.src_globalsign_r6_note,
                true,
                "2cabeafe37d06ca22aba7391c0033d25982952c453647349763a3ab5ad6ccf69"));

        // -------------------------------------------------------- Sectigo / Comodo
        list.add(single(context,
                R.string.src_usertrust_rsa_name,
                "http://crt.sectigo.com/USERTrustRSACertificationAuthority.crt",
                R.string.src_usertrust_rsa_note,
                true,
                "e793c9b02fd8aa13e21c31228accb08119643b749c898964b1746d46c3d4cbd2"));

        list.add(single(context,
                R.string.src_comodo_rsa_name,
                "http://crt.sectigo.com/COMODORSACertificationAuthority.crt",
                R.string.src_comodo_rsa_note,
                true,
                "52f0e1c4e58ec629291b60317f074671b85d7ea80d5b07273463534b32b40234"));

        list.add(single(context,
                R.string.src_comodo_ecc_name,
                "http://crt.sectigo.com/COMODOECCCertificationAuthority.crt",
                R.string.src_comodo_ecc_note,
                true,
                "1793927a0614549789adce2f8f34f7f0b66d0f3ae3a3b84d21ec15dbba4fadc7"));

        // --------------------------------------------------------------- Entrust
        list.add(single(context,
                R.string.src_entrust_g2_name,
                "https://web.entrust.com/root-certificates/entrust_g2_ca.cer",
                archived("20241006103630",
                        "https://web.entrust.com/root-certificates/entrust_g2_ca.cer"),
                R.string.src_entrust_g2_note,
                true,
                "43df5774b03e7fef5fe40d931a7bedf1bb2e6b42738c4e6d3841103d3aa7f339"));

        // ---------------------------------------------------- GoDaddy / Starfield
        list.add(single(context,
                R.string.src_godaddy_g2_name,
                "https://certs.godaddy.com/repository/gdroot-g2.crt",
                archived("20250101020645", "https://certs.godaddy.com/repository/gdroot-g2.crt"),
                R.string.src_godaddy_g2_note,
                true,
                "45140b3247eb9cc8c5b4f0d7b53091f73292089e6e5a63e2749dd3aca9198eda"));

        // ------------------------------------------------------------- Microsoft
        list.add(single(context,
                R.string.src_ms_rsa_2017_name,
                "https://www.microsoft.com/pkiops/certs/Microsoft%20RSA%20Root%20Certificate%20Authority%202017.crt",
                R.string.src_ms_rsa_2017_note,
                true,
                "c741f70f4b2a8d88bf2e71c14122ef53ef10eba0cfa5e64cfa20f418853073e0"));

        list.add(single(context,
                R.string.src_ms_ecc_2017_name,
                "https://www.microsoft.com/pkiops/certs/Microsoft%20ECC%20Root%20Certificate%20Authority%202017.crt",
                R.string.src_ms_ecc_2017_note,
                true,
                "358df39d764af9e1b766e9c972df352ee15cfac227af6ad1d70e8e4a6edcba02"));

        return list;
    }
}
