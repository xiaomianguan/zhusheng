package cc.wuersan008.zhusheng;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Helpers for running shell commands and detecting root.
 *
 * <p>Every method here blocks, so callers must invoke them from a background
 * thread. {@code su} may display a permission prompt, so a generous timeout is
 * used before giving up.</p>
 */
public final class RootUtils {

    /** How long we wait for a root shell (the user may need to tap "Allow"). */
    private static final long TIMEOUT_MS = 120000L;

    private RootUtils() {
    }

    public static class Result {
        public final int exitCode;
        public final String output;

        public Result(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
        }

        public boolean ok() {
            return exitCode == 0;
        }
    }

    /** Returns true when {@code su} exists and grants a uid 0 shell. */
    public static boolean isRootAvailable() {
        Result r = run(new String[]{"su", "-c", "id"});
        return r.output.contains("uid=0");
    }

    /** Runs a script through {@code su -c}. */
    public static Result runRoot(String script) {
        return run(new String[]{"su", "-c", script});
    }

    /** Runs a script through {@code sh -c} (no root). */
    public static Result runShell(String script) {
        return run(new String[]{"sh", "-c", script});
    }

    private static Result run(String[] command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            final Process proc = process;
            final StringBuilder output = new StringBuilder();

            Thread reader = new Thread(new Runnable() {
                public void run() {
                    try {
                        BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                        String line;
                        while ((line = br.readLine()) != null) {
                            output.append(line).append('\n');
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
            reader.setDaemon(true);
            reader.start();

            long deadline = System.currentTimeMillis() + TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                try {
                    int code = process.exitValue();
                    reader.join(2000);
                    return new Result(code, output.toString());
                } catch (IllegalThreadStateException notYet) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            return new Result(-1, output.toString() + "\n[zhusheng] command timed out");
        } catch (Exception e) {
            return new Result(-1, "exec failed: " + e.getMessage());
        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
