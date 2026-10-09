package cc.wuersan008.zhusheng;

import android.content.Context;

/**
 * Explains the output of a root script to the person who ran it.
 *
 * <p>What the dialog after a store installation or removal shows is the output of a
 * shell script: a few markers such as {@code ZHUSHENG_COPIED=1} and
 * {@code ZHUSHENG_DONE}, which say everything the script has to say and nothing at all
 * to someone who has not read the script - and nothing whatsoever to a reader of a
 * language the app is not running in. The output itself is left exactly as it came
 * back, because that is what a bug report should quote, and the markers the app
 * recognises are explained underneath it in the language the app is set to.
 *
 * <p>The explanations follow the order of the markers in the output, so a line in the
 * explanation can be matched with the line above it.
 */
public final class ScriptNotes {

    /**
     * What the script prints, and what else can end up in its output, in the order they
     * are looked for. Each entry belongs to the same position of {@link #NEEDLE_RES}.
     */
    private static final String[] NEEDLES = {
            "ZHUSHENG_COPIED=",
            "ZHUSHENG_REMOVED=",
            "ZHUSHENG_DEDUPED=",
            "ZHUSHENG_DONE",
            "no system-installed.txt",
            "could not write",
            "[zhusheng] command timed out",
            "exec failed",
    };

    /** The explanation of each needle, in the order of {@link #NEEDLES}. */
    private static final int[] NEEDLE_RES = {
            R.string.output_note_copied,
            R.string.output_note_removed,
            R.string.output_note_deduped,
            R.string.output_note_done,
            R.string.output_note_manifest,
            R.string.output_note_no_script,
            R.string.output_note_timeout,
            R.string.output_note_exec,
    };

    private ScriptNotes() {
    }

    /**
     * The explanations of everything in {@code log} the app recognises, one per line, in
     * the order the recognised lines appear - or an empty string when the output holds
     * nothing recognisable.
     *
     * <p>An output that is empty altogether is answered differently: a script that was
     * handed to {@code su} and printed nothing at all is the shape a refused request for
     * root takes, which is worth saying because nothing else in the app can tell that
     * apart from a script that ran and had no work to do.
     */
    public static String explain(Context context, String log) {
        if (log == null || log.trim().isEmpty()) {
            return context.getString(R.string.output_note_no_output);
        }
        StringBuilder notes = new StringBuilder();
        boolean[] used = new boolean[NEEDLES.length];
        // Pick the needle that appears first, then the one after it, and so on: the
        // explanations come out in the order of the output itself.
        while (true) {
            int pick = -1;
            int pickedAt = Integer.MAX_VALUE;
            for (int i = 0; i < NEEDLES.length; i++) {
                if (used[i]) {
                    continue;
                }
                int at = log.indexOf(NEEDLES[i]);
                if (at >= 0 && at < pickedAt) {
                    pickedAt = at;
                    pick = i;
                }
            }
            if (pick < 0) {
                break;
            }
            used[pick] = true;
            if (notes.length() > 0) {
                notes.append('\n');
            }
            notes.append("• ").append(context.getString(NEEDLE_RES[pick]));
        }
        return notes.toString();
    }
}