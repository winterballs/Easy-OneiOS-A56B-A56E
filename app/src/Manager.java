package local.oneios.manager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Manager extends Activity {
    private static final String STAGE = "/data/local/tmp/oneios";
    private static final String PATH = STAGE + "/bin/oneios_root.sh";
    /** SHA-256 of bin/oneios_root.sh inside assets/bootstrap.tar.gz; build.py refuses an APK where they differ. */
    private static final String ROOT_SHA = "6c635500bb42807d70b493cbc6a16dc7f56f198f92d2cf6aa43ed94e9b9362db";
    private static final String BOOTSTRAP_ASSET = "bootstrap.tar.gz";
    private static final String IMAGE_BASE = "ios16.5-a56-image.tar.gz";
    // The A56 kernel with kernel patches 0079 (touch in iOS 16.5's event layout) and 0080 (configd's IONetworkStack
    // entitlement: Wi-Fi's en0). The release image carries an older kernel; this one replaces it in image/ and
    // stage_ios16.sh records it. Pinned by SHA-256: a download that differs is refused.
    private static final String KERNEL_ASSET = "a56-kernel.kc";
    private static final String KERNEL_SHA = "00d8b9cced5e8f02b65e60bd6dc2be676dbc6bf081ec5ee8799b17081a12c6d9";
    private static final long KERNEL_SIZE = 95731712L;
    private static final String KERNEL_HASHES_ASSET = "a56-kernel.kc.hashes";
    private static final String KERNEL_HASHES_SHA = "c081d62eddd4b779a16b55cf400a8ad0a2801363870fb60e0ef5772092c5c485";
    private static final long KERNEL_HASHES_SIZE = 2976L;
    private static final String RELEASE_API = "https://api.github.com/repos/Musicalista/OneiOS-A56/releases/tags/v1.0.0";
    private static final String RELEASE_DL = "https://github.com/Musicalista/OneiOS-A56/releases/download/v1.0.0/";
    private volatile boolean alive = true;
    private TextView status;
    private TextView badgeText;
    private View badgeDot;
    private Button start;
    private Button btnDownload;
    private ScrollView scroll;
    private boolean busy;

    private int dp(int val) {
        return (int) (val * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable makeBox(int fillColor, int strokeColor, int strokeWidthDp, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        if (strokeWidthDp > 0) {
            gd.setStroke(dp(strokeWidthDp), strokeColor);
        }
        gd.setCornerRadius(dp(radiusDp));
        return gd;
    }

    private GradientDrawable makeTopRound(int fillColor, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        float r = dp(radiusDp);
        gd.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return gd;
    }

    private GradientDrawable makeBottomRound(int fillColor, int strokeColor, int strokeWidthDp, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        if (strokeWidthDp > 0) {
            gd.setStroke(dp(strokeWidthDp), strokeColor);
        }
        float r = dp(radiusDp);
        gd.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        return gd;
    }

    private RippleDrawable makeRipple(int normalColor, int rippleColor, int radiusDp) {
        GradientDrawable normal = makeBox(normalColor, 0, 0, radiusDp);
        GradientDrawable mask = makeBox(0xFFFFFFFF, 0, 0, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), normal, mask);
    }

    private RippleDrawable makeStrokeRipple(int normalColor, int strokeColor, int strokeWidthDp, int rippleColor, int radiusDp) {
        GradientDrawable normal = makeBox(normalColor, strokeColor, strokeWidthDp, radiusDp);
        GradientDrawable mask = makeBox(0xFFFFFFFF, 0, 0, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), normal, mask);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B0C10"));
        root.setPadding(dp(20), dp(44), dp(20), dp(20));

        // --- TOP BAR (Title + Status Badge) ---
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams topBarLp = new LinearLayout.LayoutParams(-1, -2);
        topBarLp.bottomMargin = dp(14);

        TextView title = new TextView(this);
        title.setText("OneiOS");
        title.setTextSize(26);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        title.setTextColor(Color.WHITE);
        topBar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        // Status Pill Badge (OneUI / iOS Dynamic Island style)
        LinearLayout badge = new LinearLayout(this);
        badge.setOrientation(LinearLayout.HORIZONTAL);
        badge.setGravity(Gravity.CENTER_VERTICAL);
        badge.setBackground(makeBox(Color.parseColor("#181920"), Color.parseColor("#2C2D35"), 1, 20));
        badge.setPadding(dp(10), dp(6), dp(12), dp(6));

        badgeDot = new View(this);
        badgeDot.setBackground(makeBox(Color.parseColor("#FFA000"), 0, 0, 10));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotLp.rightMargin = dp(6);
        badge.addView(badgeDot, dotLp);

        badgeText = new TextView(this);
        badgeText.setText("VERIFICANDO");
        badgeText.setTextSize(11);
        badgeText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        badgeText.setTextColor(Color.parseColor("#E0E0E0"));
        badge.addView(badgeText);

        topBar.addView(badge);
        root.addView(topBar, topBarLp);

        // --- DEVICE SPEC CARD (One UI Squircle Card) ---
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(makeBox(Color.parseColor("#14151C"), Color.parseColor("#232530"), 1, 16));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.bottomMargin = dp(14);

        TextView devName = new TextView(this);
        devName.setText("Samsung Galaxy A56 5G (SM-A566E)");
        devName.setTextSize(14);
        devName.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        devName.setTextColor(Color.WHITE);
        card.addView(devName);

        TextView devDetails = new TextView(this);
        devDetails.setText("SoC: Exynos 1580 (s5e8855) · GPU: Xclipse 540\nKernel: XNU 25.5.0 · Imagem: iOS 16.5 (20F66)");
        devDetails.setTextSize(12);
        devDetails.setLineSpacing(dp(2), 1f);
        devDetails.setTextColor(Color.parseColor("#8E8E93"));
        LinearLayout.LayoutParams detLp = new LinearLayout.LayoutParams(-1, -2);
        detLp.topMargin = dp(4);
        card.addView(devDetails, detLp);

        root.addView(card, cardLp);

        // --- PRIMARY BUTTON: INICIAR SESSÃO IOS ---
        start = new Button(this);
        start.setText("▶   INICIAR SESSÃO IOS");
        start.setTextSize(15);
        start.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        start.setTextColor(Color.WHITE);
        start.setBackground(makeRipple(Color.parseColor("#262833"), 0x33FFFFFF, 16));
        start.setEnabled(false);
        start.setElevation(dp(2));
        start.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Iniciar iOS 16.5?")
            .setMessage("A interface da One UI será pausada e o runtime do iOS 16.5 assumirá o painel.\n\nPara voltar à One UI a qualquer momento, segure Volume + e Volume - por 3 segundos.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Iniciar", (dialog, which) -> run("switch 115 1")).show());

        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(-1, dp(54));
        startLp.bottomMargin = dp(10);
        root.addView(start, startLp);

        // --- IN-APP GITHUB DOWNLOAD / IMPORT BUTTON ---
        btnDownload = new Button(this);
        btnDownload.setText("📥   BAIXAR / IMPORTAR IMAGEM IOS 16.5");
        btnDownload.setTextSize(13);
        btnDownload.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btnDownload.setTextColor(Color.parseColor("#00B0FF"));
        btnDownload.setBackground(makeStrokeRipple(Color.parseColor("#0D1B2A"), Color.parseColor("#00B0FF"), 1, 0x3300B0FF, 16));
        btnDownload.setOnClickListener(v -> showDownloadDialog());

        LinearLayout.LayoutParams dlLp = new LinearLayout.LayoutParams(-1, dp(48));
        dlLp.bottomMargin = dp(10);
        root.addView(btnDownload, dlLp);

        // --- SECONDARY BUTTON: VOLTAR À ONE UI ---
        Button btnReturn = new Button(this);
        btnReturn.setText("↺   VOLTAR À ONE UI (SAFE RECOVERY)");
        btnReturn.setTextSize(13);
        btnReturn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btnReturn.setTextColor(Color.parseColor("#FF5252"));
        btnReturn.setBackground(makeStrokeRipple(Color.parseColor("#1B1214"), Color.parseColor("#FF5252"), 1, 0x22FF5252, 16));
        btnReturn.setOnClickListener(v -> run("return"));

        LinearLayout.LayoutParams retLp = new LinearLayout.LayoutParams(-1, dp(48));
        retLp.bottomMargin = dp(12);
        root.addView(btnReturn, retLp);

        // --- ACTION BUTTONS ROW (STATUS & LOGS) ---
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(42));
        rowLp.bottomMargin = dp(12);

        Button btnStatus = new Button(this);
        btnStatus.setText("⟳  Atualizar Status");
        btnStatus.setTextSize(12);
        btnStatus.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btnStatus.setTextColor(Color.parseColor("#5E9CFF"));
        btnStatus.setBackground(makeStrokeRipple(Color.parseColor("#141824"), Color.parseColor("#28354D"), 1, 0x335E9CFF, 12));
        btnStatus.setOnClickListener(v -> run("status"));
        LinearLayout.LayoutParams r1 = new LinearLayout.LayoutParams(0, -1, 1f);
        r1.rightMargin = dp(6);
        row.addView(btnStatus, r1);

        Button btnLogs = new Button(this);
        btnLogs.setText("≡  Logs da Sessão");
        btnLogs.setTextSize(12);
        btnLogs.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btnLogs.setTextColor(Color.parseColor("#B0BEC5"));
        btnLogs.setBackground(makeStrokeRipple(Color.parseColor("#16181F"), Color.parseColor("#2A2D38"), 1, 0x33FFFFFF, 12));
        btnLogs.setOnClickListener(v -> run("logfile session 0 12000"));
        LinearLayout.LayoutParams r2 = new LinearLayout.LayoutParams(0, -1, 1f);
        r2.leftMargin = dp(6);
        row.addView(btnLogs, r2);

        root.addView(row, rowLp);

        // --- CONSOLE / TERMINAL (macOS / iOS Terminal Style) ---
        LinearLayout termHeader = new LinearLayout(this);
        termHeader.setOrientation(LinearLayout.HORIZONTAL);
        termHeader.setGravity(Gravity.CENTER_VERTICAL);
        termHeader.setBackground(makeTopRound(Color.parseColor("#1B1C24"), 14));
        termHeader.setPadding(dp(12), dp(8), dp(12), dp(8));

        // Window Traffic Lights (Red, Yellow, Green)
        View dotRed = new View(this);
        dotRed.setBackground(makeBox(Color.parseColor("#FF5F56"), 0, 0, 6));
        LinearLayout.LayoutParams d1 = new LinearLayout.LayoutParams(dp(10), dp(10));
        d1.rightMargin = dp(6);
        termHeader.addView(dotRed, d1);

        View dotYellow = new View(this);
        dotYellow.setBackground(makeBox(Color.parseColor("#FFBD2E"), 0, 0, 6));
        LinearLayout.LayoutParams d2 = new LinearLayout.LayoutParams(dp(10), dp(10));
        d2.rightMargin = dp(6);
        termHeader.addView(dotYellow, d2);

        View dotGreen = new View(this);
        dotGreen.setBackground(makeBox(Color.parseColor("#27C93F"), 0, 0, 6));
        LinearLayout.LayoutParams d3 = new LinearLayout.LayoutParams(dp(10), dp(10));
        d3.rightMargin = dp(10);
        termHeader.addView(dotGreen, d3);

        TextView termTitle = new TextView(this);
        termTitle.setText("console.session — xnuhost darwin");
        termTitle.setTextSize(11);
        termTitle.setTextColor(Color.parseColor("#6C6E7E"));
        termTitle.setTypeface(Typeface.MONOSPACE);
        termHeader.addView(termTitle);

        root.addView(termHeader, new LinearLayout.LayoutParams(-1, -2));

        scroll = new ScrollView(this);
        scroll.setBackground(makeBottomRound(Color.parseColor("#07080A"), Color.parseColor("#1B1C24"), 1, 14));
        scroll.setPadding(dp(12), dp(10), dp(12), dp(12));

        status = new TextView(this);
        status.setTextIsSelectable(true);
        status.setTypeface(Typeface.MONOSPACE);
        status.setTextSize(11);
        status.setLineSpacing(dp(2), 1f);
        status.setTextColor(Color.parseColor("#00E676"));
        scroll.addView(status);

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        ensureBootstrap();
    }

    @Override
    protected void onDestroy() {
        alive = false;
        super.onDestroy();
    }

    // --- small helpers -------------------------------------------------------

    /** Runs on the UI thread only while the activity is still alive. */
    private void ui(Runnable r) {
        runOnUiThread(() -> {
            if (alive && !isDestroyed()) r.run();
        });
    }

    private void setBadge(String color, String text) {
        badgeDot.setBackground(makeBox(Color.parseColor(color), 0, 0, 10));
        badgeText.setText(text);
        badgeText.setTextColor(Color.parseColor(color.equals("#FFA000") || color.equals("#29B6F6") ? "#E0E0E0" : color));
    }

    private void say(String line) {
        status.append(line + "\n");
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void setStartReady(boolean ready) {
        start.setEnabled(ready);
        start.setBackground(makeRipple(Color.parseColor(ready ? "#00C853" : "#262833"), 0x33FFFFFF, 16));
    }

    /** A single-quoted shell word. */
    private static String shq(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static String sha256(InputStream in) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[65536];
        for (int r; (r = in.read(buf)) != -1;) md.update(buf, 0, r);
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format(Locale.US, "%02x", b));
        return sb.toString();
    }

    /** Runs a command as root; every output line goes to sink (on the worker thread). Returns the exit code. */
    private static int su(String command, LineSink sink) throws Exception {
        Process p = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
        p.getOutputStream().close();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            for (String line; (line = reader.readLine()) != null;) sink.line(line);
        }
        return p.waitFor();
    }

    private interface LineSink { void line(String l); }

    /** The shell guard used before anything that rewrites the stage: no open session of this boot. */
    private static final String SESSION_GUARD =
        "S=" + STAGE + "; "
        + "if [ \"$(cat $S/control/boot 2>/dev/null)\" = \"$(cat /proc/sys/kernel/random/boot_id)\" ]; then "
        + "case \"$(cat $S/control/state 2>/dev/null)\" in starting|running|returning) "
        + "echo '[!] Uma sessão iOS está aberta: volte à One UI antes.'; exit 9 ;; esac; fi; ";

    /** Where this app downloads to: its own external folder (no storage permission needed, survives scoped storage). */
    private File downloadDir() {
        File base = getExternalFilesDir(null);
        File d = new File(base != null ? base : getFilesDir(), "download");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    // --- download / import ---------------------------------------------------

    private void showDownloadDialog() {
        if (busy) return;
        final String[] options = {
            "📥   Baixar direto do GitHub Releases (iOS 16.5)",
            "📂   Importar arquivo baixado (/sdcard/Download)",
            "🔗   Inserir URL personalizada",
            "⬆   Atualizar só o kernel (toque + Wi-Fi)"
        };

        new AlertDialog.Builder(this)
            .setTitle("Instalar Imagem iOS 16.5")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    startDownloadGithub();
                } else if (which == 1) {
                    importFromLocal();
                } else if (which == 3) {
                    startKernelUpdate();
                } else {
                    showCustomUrlDialog();
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private static final class Part {
        final String name;
        final String url;
        final long size; // -1 = unknown
        Part(String name, String url, long size) { this.name = name; this.url = url; this.size = size; }
    }

    /** The image parts of the release, read from the GitHub API; the two known names when the API is unreachable. */
    private static List<Part> releaseParts() {
        List<Part> parts = new ArrayList<>();
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(RELEASE_API).openConnection();
            c.setRequestProperty("User-Agent", "OneiOS-Manager/1.1");
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            if (c.getResponseCode() == HttpURLConnection.HTTP_OK) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                    for (String l; (l = r.readLine()) != null;) sb.append(l);
                }
                JSONArray assets = new JSONObject(sb.toString()).getJSONArray("assets");
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    String n = a.getString("name");
                    if (n.startsWith(IMAGE_BASE + ".part")) {
                        parts.add(new Part(n, a.getString("browser_download_url"), a.optLong("size", -1)));
                    }
                }
            }
            c.disconnect();
        } catch (Exception ignored) {
            parts.clear();
        }
        if (parts.isEmpty()) {
            parts.add(new Part(IMAGE_BASE + ".part00", RELEASE_DL + IMAGE_BASE + ".part00", -1));
            parts.add(new Part(IMAGE_BASE + ".part01", RELEASE_DL + IMAGE_BASE + ".part01", -1));
        }
        Collections.sort(parts, (a, b) -> a.name.compareTo(b.name));
        return parts;
    }

    private void startDownloadGithub() {
        if (busy) return;
        busy = true;
        setStartReady(false);
        setBadge("#29B6F6", "BAIXANDO");
        status.setText("[$] Lendo a lista de partes do GitHub Releases...\n");

        new Thread(() -> {
            File dir = downloadDir();
            try {
                List<Part> parts = releaseParts();
                ui(() -> say("[$] " + parts.size() + " parte(s): destino " + dir.getAbsolutePath()));
                for (int i = 0; i < parts.size(); i++) {
                    Part p = parts.get(i);
                    String label = "Parte " + (i + 1) + "/" + parts.size();
                    download(p.url, new File(dir, p.name), p.size, label);
                }
                downloadKernel(dir);
                File first = new File(dir, parts.get(0).name);
                ui(() -> {
                    say("[$] Download de todas as partes concluído.");
                    busy = false;
                    extractAndStage(first.getAbsolutePath(), true);
                });
            } catch (Exception e) {
                final String err = String.valueOf(e.getMessage());
                ui(() -> {
                    busy = false;
                    say("[!] Erro durante o download: " + err);
                    say("    O que já baixou fica guardado: tocar de novo continua de onde parou.");
                    setBadge("#FF5252", "ERRO DOWNLOAD");
                });
            }
        }).start();
    }

    /**
     * Downloads url to target. Resumes a partial file with a Range request; skips a file already
     * complete (expected size known). Redirects (GitHub -> its CDN) are followed by HttpURLConnection.
     */
    private void download(String fileUrl, File target, long expected, String label) throws Exception {
        if (expected > 0 && target.length() == expected) {
            ui(() -> say("[=] " + label + ": " + target.getName() + " já está completo."));
            return;
        }
        long have = target.exists() ? target.length() : 0;
        if (expected > 0 && have > expected) { target.delete(); have = 0; }

        HttpURLConnection conn = (HttpURLConnection) new URL(fileUrl).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "OneiOS-Manager/1.1");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        if (have > 0) conn.setRequestProperty("Range", "bytes=" + have + "-");
        int code = conn.getResponseCode();
        boolean append;
        if (code == HttpURLConnection.HTTP_PARTIAL && have > 0) {
            append = true;
        } else if (code == HttpURLConnection.HTTP_OK) {
            append = false;
            have = 0;
        } else if (code == 416 && have > 0) {
            // Nothing left to send: the file is already whole.
            conn.disconnect();
            return;
        } else {
            conn.disconnect();
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new java.io.IOException("Pacote indisponível neste endereço (HTTP 404): " + target.getName());
            }
            throw new java.io.IOException("O servidor respondeu HTTP " + code + " para " + target.getName());
        }
        long rest = conn.getContentLengthLong();
        long total = rest > 0 ? have + rest : expected;
        final long startAt = have;
        ui(() -> say("[$] " + label + ": " + target.getName() + (startAt > 0 ? " (continuando de " + (startAt >> 20) + " MB)" : "")));

        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(target, append)) {
            byte[] buffer = new byte[65536];
            long done = have;
            long lastUpdate = System.currentTimeMillis();
            long startTime = lastUpdate;
            for (int read; (read = in.read(buffer)) != -1;) {
                out.write(buffer, 0, read);
                done += read;
                long now = System.currentTimeMillis();
                if (now - lastUpdate > 500) {
                    lastUpdate = now;
                    double speed = ((done - startAt) / 1048576.0) / Math.max(0.5, (now - startTime) / 1000.0);
                    final String prog = total > 0
                        ? String.format(Locale.US, "%s: %.1f / %.1f MB (%.1f%%) — %.2f MB/s",
                            label, done / 1048576.0, total / 1048576.0, done * 100.0 / total, speed)
                        : String.format(Locale.US, "%s: %.1f MB — %.2f MB/s", label, done / 1048576.0, speed);
                    final String badge = total > 0 ? String.format(Locale.US, "BAIXANDO %d%%", (int) (done * 100 / total)) : "BAIXANDO";
                    ui(() -> {
                        badgeText.setText(badge);
                        progressLine(prog);
                    });
                }
            }
        } finally {
            conn.disconnect();
        }
        if (expected > 0 && target.length() != expected) {
            throw new java.io.IOException(target.getName() + " ficou com " + target.length() + " bytes, esperado " + expected);
        }
    }

    /** Replaces the last console line when it is a progress line, so progress does not flood the console. */
    private void progressLine(String text) {
        CharSequence cur = status.getText();
        String s = cur.toString();
        int cut = s.lastIndexOf('\n', s.length() - 2);
        String last = s.substring(cut + 1);
        if (last.startsWith("    ↳ ")) status.setText(s.substring(0, cut + 1));
        say("    ↳ " + text);
    }

    /** Downloads the pinned kernel pair into dir and checks both hashes. Throws on a mismatch. */
    private void downloadKernel(File dir) throws Exception {
        File kc = new File(dir, KERNEL_ASSET), kh = new File(dir, KERNEL_HASHES_ASSET);
        download(RELEASE_DL + KERNEL_ASSET, kc, KERNEL_SIZE, "Kernel");
        download(RELEASE_DL + KERNEL_HASHES_ASSET, kh, KERNEL_HASHES_SIZE, "Kernel (hashes)");
        String[][] want = { { kc.getAbsolutePath(), KERNEL_SHA }, { kh.getAbsolutePath(), KERNEL_HASHES_SHA } };
        for (String[] w : want) {
            String got;
            try (InputStream in = new java.io.FileInputStream(w[0])) { got = sha256(in); }
            if (!got.equals(w[1])) {
                new File(w[0]).delete();
                throw new java.io.IOException(new File(w[0]).getName() + ": SHA-256 " + got.substring(0, 12) + "… não confere com o do app");
            }
        }
        ui(() -> say("[$] Kernel baixado e conferido (SHA-256 " + KERNEL_SHA.substring(0, 12) + "…)."));
    }

    /** Shell lines that put the downloaded kernel pair (if present in the app's download folder) into $I. */
    private String kernelInstallShell() {
        String d = shq(downloadDir().getAbsolutePath());
        return "KD=" + d + "; if [ -f \"$KD/" + KERNEL_ASSET + "\" ] && [ -f \"$KD/" + KERNEL_HASHES_ASSET + "\" ]; then "
            + "[ \"$(sha256sum \"$KD/" + KERNEL_ASSET + "\" | cut -d' ' -f1)\" = " + KERNEL_SHA + " ] "
            + "&& [ \"$(sha256sum \"$KD/" + KERNEL_HASHES_ASSET + "\" | cut -d' ' -f1)\" = " + KERNEL_HASHES_SHA + " ] "
            + "|| { echo '[!] O kernel baixado não confere com o SHA-256 do app.'; exit 1; }; "
            + "cat \"$KD/" + KERNEL_ASSET + "\" > \"$I/kernel.kc\" && cat \"$KD/" + KERNEL_HASHES_ASSET + "\" > \"$I/kernel.kc.hashes\" || exit 1; "
            + "chown 0:0 \"$I/kernel.kc\" \"$I/kernel.kc.hashes\"; chmod 644 \"$I/kernel.kc\" \"$I/kernel.kc.hashes\"; "
            + "rm -f \"$KD/" + KERNEL_ASSET + "\" \"$KD/" + KERNEL_HASHES_ASSET + "\"; "
            + "echo '[$] Kernel novo instalado (toque + Wi-Fi).'; fi; ";
    }

    /** Replaces only the kernel of an image already staged, then records it again (stage_ios16.sh). */
    private void startKernelUpdate() {
        if (busy) return;
        busy = true;
        setStartReady(false);
        setBadge("#29B6F6", "BAIXANDO");
        status.setText("[$] Baixando o kernel novo do GitHub Releases...\n");
        new Thread(() -> {
            try {
                downloadKernel(downloadDir());
                String sh = SESSION_GUARD
                    + "I=" + STAGE + "/image; "
                    + "[ -f \"$I/manifest.json\" ] || { echo '[!] Nenhuma imagem instalada: instale a imagem primeiro.'; exit 1; }; "
                    + kernelInstallShell()
                    + "/system/bin/sh $S/stage_ios16.sh > $S/stage_ios16.log 2>&1 || { echo '[!] stage_ios16.sh falhou:'; tail -n 15 $S/stage_ios16.log; exit 1; }; "
                    + "echo '=== Kernel atualizado ==='";
                ui(() -> { busy = false; runShell("Atualizar kernel", sh); });
            } catch (Exception e) {
                final String err = String.valueOf(e.getMessage());
                ui(() -> {
                    busy = false;
                    say("[!] Erro ao baixar o kernel: " + err);
                    setBadge("#FF5252", "ERRO DOWNLOAD");
                });
            }
        }).start();
    }

    private void showCustomUrlDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(10), dp(20), dp(10));

        final EditText input = new EditText(this);
        input.setHint("https://…/imagem.tar.gz");
        input.setTextColor(Color.WHITE);
        input.setTextSize(13);
        container.addView(input);

        new AlertDialog.Builder(this)
            .setTitle("URL da Imagem iOS 16.5")
            .setMessage("Digite ou cole a URL do arquivo de imagem (.tar ou .tar.gz):")
            .setView(container)
            .setPositiveButton("Baixar", (dialog, which) -> {
                String url = input.getText().toString().trim();
                if (url.isEmpty()) return;
                if (!url.startsWith("https://")) {
                    status.setText("Informe uma URL HTTPS para o pacote da imagem.\n");
                    return;
                }
                startDownload(url);
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void startDownload(String fileUrl) {
        if (busy) return;
        busy = true;
        setStartReady(false);
        setBadge("#29B6F6", "BAIXANDO");
        status.setText("[$] Conectando ao servidor...\nURL: " + fileUrl + "\n");

        new Thread(() -> {
            String name = Uri.parse(fileUrl).getLastPathSegment();
            if (name == null || !name.matches("[A-Za-z0-9._+-]+")
                || !(name.endsWith(".tar") || name.endsWith(".tar.gz") || name.endsWith(".tgz") || name.endsWith(".zip"))) {
                name = "custom-image.tar.gz";
            }
            File target = new File(downloadDir(), name);
            try {
                download(fileUrl, target, -1, "Imagem");
                ui(() -> {
                    say("[$] Download concluído (" + (target.length() >> 20) + " MB).");
                    busy = false;
                    extractAndStage(target.getAbsolutePath(), true);
                });
            } catch (Exception e) {
                final String err = String.valueOf(e.getMessage());
                ui(() -> {
                    busy = false;
                    say("[!] Erro durante o download: " + err);
                    say("    Você também pode baixar o arquivo no navegador e importar de /sdcard/Download.");
                    setBadge("#FF5252", "ERRO DOWNLOAD");
                });
            }
        }).start();
    }

    /**
     * Lists the packages in /sdcard/Download (two levels) and in this app's download folder. Read as root:
     * with scoped storage the app itself cannot see files that the browser or a PC put in Download.
     */
    private void importFromLocal() {
        if (busy) return;
        busy = true;
        status.setText("[$] Procurando pacotes em /sdcard/Download...\n");
        new Thread(() -> {
            List<String> paths = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            String cmd = "for d in /sdcard/Download " + shq(downloadDir().getAbsolutePath()) + "; do "
                + "[ -d \"$d\" ] && find \"$d\" -maxdepth 2 -type f \\( -iname '*.tar' -o -iname '*.tar.gz' -o -iname '*.tgz' "
                + "-o -iname '*.zip' -o -iname '*.part00' \\) 2>/dev/null; done | sort -u | while read -r f; do "
                + "echo \"@@F $(stat -c %s \"$f\") $f\"; done";
            try {
                su(cmd, l -> {
                    if (!l.startsWith("@@F ")) return;
                    int sp = l.indexOf(' ', 4);
                    if (sp < 0) return;
                    String path = l.substring(sp + 1);
                    String lower = path.toLowerCase(Locale.US);
                    long mb = 0;
                    try { mb = Long.parseLong(l.substring(4, sp)) >> 20; } catch (NumberFormatException ignored) {}
                    paths.add(path);
                    String shown = path.startsWith(downloadDir().getAbsolutePath()) ? "[app] " + new File(path).getName() : path.replace("/sdcard/Download/", "");
                    labels.add(shown + (lower.endsWith(".part00") ? "  (+ partes seguintes)" : "") + "  — " + mb + " MB");
                });
            } catch (Exception e) {
                final String err = String.valueOf(e.getMessage());
                ui(() -> { busy = false; say("[!] Não foi possível listar como root: " + err); });
                return;
            }
            // A merged leftover (old app versions joined the parts on disk) beside its parts: offer the parts only.
            for (int i = paths.size() - 1; i >= 0; i--) {
                if (paths.contains(paths.get(i) + ".part00")) { paths.remove(i); labels.remove(i); }
            }
            ui(() -> {
                busy = false;
                if (paths.isEmpty()) {
                    say("Nenhum pacote encontrado.");
                    new AlertDialog.Builder(this)
                        .setTitle("Nenhum arquivo encontrado")
                        .setMessage("Nenhum pacote (.tar, .tar.gz, .zip ou .part00) foi encontrado em /sdcard/Download (nem uma pasta abaixo dela).\n\nBaixe o arquivo pelo navegador ou copie para o aparelho.")
                        .setPositiveButton("OK", null)
                        .show();
                    return;
                }
                say(paths.size() + " pacote(s) encontrado(s).");
                new AlertDialog.Builder(this)
                    .setTitle("Selecione o arquivo de imagem")
                    .setItems(labels.toArray(new String[0]), (dialog, which) -> extractAndStage(paths.get(which), false))
                    .setNegativeButton("Cancelar", null)
                    .show();
            });
        }).start();
    }

    // --- staging -------------------------------------------------------------

    /**
     * Unpacks a package into a fresh image/ and stages it (stage_ios16.sh: image.conf, image.sha256, image.ids).
     * Parts (NAME.part00, .part01, ...) are streamed straight into tar, so no merged copy takes the space twice.
     * fromApp: the archive is this app's own download, deleted once the image is staged.
     */
    private void extractAndStage(String archivePath, boolean fromApp) {
        String img = STAGE + "/image";
        String sh = SESSION_GUARD
            + "A=" + shq(archivePath) + "; I=" + img + "; N=" + img + ".new; "
            + "[ -f $S/stage_ios16.sh ] || { echo '[!] stage_ios16.sh ausente: reabra o app para reinstalar o ambiente.'; exit 1; }; "
            + "[ -f \"$A\" ] || { echo \"[!] Arquivo não encontrado: $A\"; exit 1; }; "
            + "echo '=== Preparando imagem iOS 16.5 ==='; "
            + "echo \"[$] Livre em /data: $(df -h /data | tail -n 1 | awk '{print $4}')\"; "
            + "rm -rf \"$N\" && mkdir -p \"$N\" || exit 1; "
            + "case \"$A\" in "
            + "*.part00) B=${A%.part00}; echo '[$] Partes:'; ls -l \"$B\".part* | awk '{print \"    \" $5 \"  \" $NF}'; "
            + "  echo '[$] Descompactando as partes em sequência (sem juntar em disco)...'; "
            + "  cat \"$B\".part* | tar -xzf - -C \"$N\" ;; "
            + "*.zip|*.ZIP) echo '[$] Descompactando zip...'; unzip -q -o \"$A\" -d \"$N\" ;; "
            + "*) if [ \"$(head -c 2 \"$A\" | od -An -tx1 | tr -d ' \\n')\" = 1f8b ]; then Z=z; else Z=; fi; "
            + "  echo '[$] Descompactando...'; tar -x${Z}f \"$A\" -C \"$N\" ;; "
            + "esac || { echo '[!] A extração falhou (arquivo incompleto ou corrompido?). A imagem anterior foi mantida.'; rm -rf \"$N\"; exit 1; }; "
            // A package with everything inside one folder: take that folder's contents.
            + "if [ ! -e \"$N/manifest.json\" ]; then set -- \"$N\"/*; "
            + "  if [ $# -eq 1 ] && [ -d \"$1\" ]; then echo \"[$] Conteúdo dentro de $(basename \"$1\")/: subindo um nível\"; "
            + "    D=\"$1\"; mv \"$D\" \"$N.d\" && rmdir \"$N\" && mv \"$N.d\" \"$N\" || exit 1; fi; fi; "
            + "[ -e \"$N/manifest.json\" ] || echo '[!] Aviso: manifest.json não está na raiz do pacote.'; "
            + "[ -n \"$(ls -A \"$N\")\" ] || { echo '[!] O pacote está vazio.'; rm -rf \"$N\"; exit 1; }; "
            + "rm -rf \"$I\" && mv \"$N\" \"$I\" || exit 1; "
            + "chown -R 0:0 \"$I\"; find \"$I\" -type d -exec chmod 755 {} +; find \"$I\" -type f -exec chmod 644 {} +; "
            + kernelInstallShell()
            + "echo '[$] Calculando checksums (stage_ios16.sh)...'; "
            + "/system/bin/sh $S/stage_ios16.sh > $S/stage_ios16.log 2>&1 || { echo '[!] stage_ios16.sh falhou:'; tail -n 15 $S/stage_ios16.log; exit 1; }; "
            + "echo \"[$] $(wc -l < $S/image.sha256) arquivo(s) registrados; log completo em $S/stage_ios16.log\"; "
            + (fromApp
                ? "case \"$A\" in *.part00) rm -f \"${A%.part00}\".part* ;; *) rm -f \"$A\" ;; esac; echo '[$] Download apagado para liberar espaço.'; "
                : "")
            + "echo '=== Imagem pronta ==='";
        runShell("Extrair e configurar imagem iOS 16.5", sh);
    }

    private void runShell(String actionLabel, String command) {
        if (busy) return;
        busy = true;
        setStartReady(false);
        setBadge("#FFA000", "EXECUTANDO");
        status.setText("[$] " + actionLabel + "\n");

        new Thread(() -> {
            int exit;
            try {
                exit = su(command, l -> ui(() -> say(l)));
            } catch (Exception e) {
                final String err = String.valueOf(e.getMessage());
                ui(() -> say("Erro: " + err));
                exit = -1;
            }
            final int code = exit;
            ui(() -> {
                busy = false;
                if (code != 0) {
                    say("[!] Terminou com erro (código " + code + ").");
                    setBadge("#FF5252", "ERRO");
                    return;
                }
                run("status", true);
            });
        }).start();
    }

    // --- bootstrap -----------------------------------------------------------

    /**
     * Installs (or updates) the root scripts and the runtime from assets/bootstrap.tar.gz into the stage.
     * The check runs as root: the app cannot see inside /data/local/tmp. The installed bootstrap's SHA-256 is
     * kept in $STAGE/.bootstrap, so a new APK replaces an old bootstrap, and nothing is rewritten otherwise.
     * The image set and the session data are never touched.
     */
    private void ensureBootstrap() {
        busy = true;
        setBadge("#FFA000", "VERIFICANDO");
        new Thread(() -> {
            try {
                String want;
                try (InputStream in = getAssets().open(BOOTSTRAP_ASSET)) {
                    want = sha256(in);
                }
                StringBuilder probe = new StringBuilder();
                su("S=" + STAGE + "; [ \"$(cat $S/.bootstrap 2>/dev/null)\" = " + want + " ] && [ -f $S/bin/oneios_root.sh ] && echo @@CURRENT",
                    l -> probe.append(l).append('\n'));
                if (probe.indexOf("@@CURRENT") < 0) {
                    ui(() -> {
                        setBadge("#FFA000", "INSTALANDO");
                        status.setText("[$] Instalando o ambiente do OneiOS em " + STAGE + "...\n");
                    });
                    File tarFile = new File(getCacheDir(), BOOTSTRAP_ASSET);
                    try (InputStream in = getAssets().open(BOOTSTRAP_ASSET);
                         FileOutputStream fos = new FileOutputStream(tarFile)) {
                        byte[] buf = new byte[65536];
                        for (int r; (r = in.read(buf)) != -1;) fos.write(buf, 0, r);
                    }
                    String t = shq(tarFile.getAbsolutePath());
                    String cmd = SESSION_GUARD
                        + "mkdir -p $S && chown 0:0 $S && chmod 0700 $S || exit 1; "
                        + "rm -rf $S/bin $S/runtime $S/runtime.sha256 $S/runtime.conf $S/.bootstrap; "
                        + "tar -xzf " + t + " -C $S || { echo '[!] tar falhou'; rm -f " + t + "; exit 1; }; "
                        + "rm -f " + t + "; "
                        + "chown -R 0:0 $S/bin $S/runtime $S/runtime.sha256 $S/runtime.conf $S/*.sh; "
                        + "find $S/bin $S/runtime -type d -exec chmod 755 {} +; "
                        + "find $S/bin $S/runtime -type f -exec chmod 644 {} +; "
                        + "chmod 755 $S/bin/*.sh $S/runtime/bin/*; chmod 700 $S/oneios_switch.sh; chmod 755 $S/oneios_return.sh $S/stage_ios16.sh; "
                        + "chmod 644 $S/runtime.sha256 $S/runtime.conf; "
                        // The A56's USB gadget drops adb during a session: the switch's USB watcher and the return
                        // repair it only with this flag (oneios_switch.sh usb_save, "repair=").
                        + "touch $S/usb-repair && chmod 644 $S/usb-repair; "
                        + "[ \"$(sha256sum $S/bin/oneios_root.sh | cut -d' ' -f1)\" = " + ROOT_SHA + " ] || { echo '[!] oneios_root.sh do pacote não confere com o pin do app'; exit 1; }; "
                        + "echo " + want + " > $S/.bootstrap; "
                        + "echo '[$] Ambiente instalado.'";
                    int code = su(cmd, l -> ui(() -> say(l)));
                    if (code != 0) {
                        final int c = code;
                        ui(() -> say("[!] A instalação automática falhou (código " + c + ")."));
                    }
                }
            } catch (Exception e) {
                final String err = String.valueOf(e.getMessage());
                ui(() -> say("[!] Erro na instalação automática: " + err));
            }
            ui(() -> {
                busy = false;
                run("status", true);
            });
        }).start();
    }

    // --- oneios_root.sh ------------------------------------------------------

    private void run(String action) {
        run(action, false);
    }

    /** Runs one verb of the pinned root script. append: keep what the console already shows. */
    private void run(String action, boolean append) {
        if (busy) return;
        busy = true;
        setStartReady(false);
        setBadge("#FFA000", "EXECUTANDO");
        if (append) say("\n[$] oneios_root.sh " + action);
        else status.setText("[$] oneios_root.sh " + action + "\n");

        new Thread(() -> {
            String result;
            int exit = -1;
            try {
                String command = "test \"$(sha256sum " + PATH + " 2>/dev/null | cut -d' ' -f1)\" = " + ROOT_SHA
                    + " || { echo 'O script do OneiOS está ausente ou mudou (reabra o app para reinstalar).'; exit 1; }; "
                    + "/system/bin/sh " + PATH + " " + action;
                StringBuilder output = new StringBuilder();
                exit = su(command, l -> output.append(l).append('\n'));
                result = output.length() == 0
                    ? (exit == 0 ? "Comando concluído com sucesso." : "Erro: verifique a concessão de root no KernelSU.")
                    : output.toString();
            } catch (Exception error) {
                result = "Erro ao executar via root: " + error.getMessage();
            }
            final String message = result;
            ui(() -> {
                busy = false;
                if (append) say(message.trim());
                else {
                    status.setText(message);
                    scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
                }
                boolean isReady = message.contains("@@RESULT ready 1");
                setStartReady(isReady);
                if (isReady) {
                    setBadge("#00E676", "SISTEMA PRONTO");
                } else if (message.contains("@@RESULT session open")) {
                    setBadge("#29B6F6", "SESSÃO ABERTA");
                } else {
                    setBadge("#FF5252", "NÃO PREPARADO");
                }
            });
        }).start();
    }
}
