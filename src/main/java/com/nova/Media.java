package com.nova;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads "now playing" from the operating system (any app: Spotify, browser videos, VLC...),
 * and fetches time-synced lyrics from LRCLIB (https://lrclib.net) while you play.
 * Windows: System Media Transport Controls (via a hidden PowerShell). Linux: playerctl.
 */
public final class Media {
	public static final Media I = new Media();

	public static final class Track {
		public String title = "", artist = "", album = "", app = "";
		public double pos, end;
		public long upd;
		public boolean playing;
	}

	public record Line(long ms, String text) {}

	public enum LState { NONE, LOADING, FOUND, MISSING }

	public volatile Track track;
	public volatile List<Line> lines = List.of();
	public volatile LState lstate = LState.NONE;
	public volatile String status = "Off";

	private volatile boolean run;
	private Process proc;
	private String key = "";
	private boolean hooked;
	private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "nova-lyrics");
		t.setDaemon(true);
		return t;
	});

	private Media() {}

	private static final String WIN_SCRIPT = """
			[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false
			Add-Type -AssemblyName System.Runtime.WindowsRuntime
			$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
			function Await($op, $type) { $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result }
			[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
			[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
			$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
			while ($true) {
			  try {
			    $s = $mgr.GetCurrentSession()
			    if ($s -ne $null) {
			      $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
			      $t = $s.GetTimelineProperties()
			      $pb = $s.GetPlaybackInfo()
			      $o = [ordered]@{ title = $p.Title; artist = $p.Artist; album = $p.AlbumTitle; pos = $t.Position.TotalSeconds; end = $t.EndTime.TotalSeconds; upd = $t.LastUpdatedTime.ToUnixTimeMilliseconds(); status = [string]$pb.PlaybackStatus; app = $s.SourceAppUserModelId }
			      $o | ConvertTo-Json -Compress
			    } else { '{"none":true}' }
			  } catch { '{"none":true}' }
			  [Console]::Out.Flush()
			  Start-Sleep -Milliseconds 500
			}
			""";

	public synchronized void start() {
		if (run) return;
		String os = System.getProperty("os.name", "").toLowerCase();
		if (!os.contains("win") && !os.contains("linux")) {
			status = "Not supported on this OS";
			return;
		}
		run = true;
		status = "Starting...";
		if (!hooked) {
			hooked = true;
			Runtime.getRuntime().addShutdownHook(new Thread(() -> { if (proc != null) proc.destroy(); }));
		}
		Thread t = new Thread(this::loop, "nova-media");
		t.setDaemon(true);
		t.start();
	}

	public synchronized void stop() {
		run = false;
		if (proc != null) proc.destroy();
		track = null;
		lines = List.of();
		lstate = LState.NONE;
		key = "";
		status = "Off";
	}

	private ProcessBuilder command() {
		String os = System.getProperty("os.name", "").toLowerCase();
		if (os.contains("win")) {
			String root = System.getenv("SystemRoot");
			String exe = root != null && new File(root + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe").exists()
					? root + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" : "powershell.exe";
			String b64 = Base64.getEncoder().encodeToString(WIN_SCRIPT.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
			return new ProcessBuilder(exe, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", b64);
		}
		String loop = "while true; do playerctl metadata --format '{{title}}|||{{artist}}|||{{album}}|||{{position}}|||{{mpris:length}}|||{{status}}|||{{playerName}}' 2>/dev/null || echo NONE; sleep 0.5; done";
		return new ProcessBuilder("sh", "-c", loop);
	}

	private void loop() {
		while (run) {
			try {
				ProcessBuilder pb = command();
				pb.redirectError(ProcessBuilder.Redirect.DISCARD);
				proc = pb.start();
				status = "Listening";
				try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
					String ln;
					while (run && (ln = r.readLine()) != null) handle(ln.replace("\uFEFF", "").trim());
				}
			} catch (Exception e) {
				status = "Error: " + e.getClass().getSimpleName();
			}
			if (run) {
				status = "Restarting...";
				try { Thread.sleep(3000); } catch (InterruptedException ignored) { }
			}
		}
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	private static double num(JsonObject o, String k) {
		JsonElement e = o.get(k);
		try { return e == null || e.isJsonNull() ? 0 : e.getAsDouble(); } catch (Exception ex) { return 0; }
	}

	private void handle(String ln) {
		if (ln.isEmpty()) return;
		long now = System.currentTimeMillis();
		Track t = new Track();
		try {
			if (ln.startsWith("{")) {
				JsonObject o = JsonParser.parseString(ln).getAsJsonObject();
				if (o.has("none")) { setTrack(null); return; }
				t.title = str(o, "title");
				t.artist = str(o, "artist");
				t.album = str(o, "album");
				t.app = str(o, "app");
				t.pos = num(o, "pos");
				t.end = num(o, "end");
				t.upd = (long) num(o, "upd");
				t.playing = "Playing".equalsIgnoreCase(str(o, "status"));
				if (t.upd <= 0 || Math.abs(now - t.upd) > 600_000) t.upd = now;
			} else {
				if (ln.equals("NONE")) { setTrack(null); return; }
				String[] p = ln.split("\\|\\|\\|", -1);
				if (p.length < 6) return;
				t.title = p[0];
				t.artist = p[1];
				t.album = p[2];
				try { t.pos = Double.parseDouble(p[3]) / 1e6; } catch (Exception ignored) { }
				try { t.end = Double.parseDouble(p[4]) / 1e6; } catch (Exception ignored) { }
				t.playing = "Playing".equalsIgnoreCase(p[5]);
				t.app = p.length > 6 ? p[6] : "";
				t.upd = now;
			}
		} catch (Exception e) {
			return;
		}
		setTrack(t.title.isBlank() ? null : t);
	}

	private void setTrack(Track t) {
		if (t == null) {
			track = null;
			if (!key.isEmpty()) { key = ""; lines = List.of(); lstate = LState.NONE; }
			return;
		}
		String k = t.title + "|" + t.artist;
		if (!k.equals(key)) {
			key = k;
			fetchLyrics(t, k);
		}
		track = t;
	}

	/** Estimated playback position in ms (keeps counting between OS updates). */
	public long estPosMs() {
		Track t = track;
		if (t == null) return 0;
		double p = t.pos;
		if (t.playing) p += (System.currentTimeMillis() - t.upd) / 1000.0;
		if (t.end > 0) p = Math.min(p, t.end);
		return (long) (Math.max(0, p) * 1000);
	}

	// ---------------- lyrics ----------------

	private void fetchLyrics(Track t, String k) {
		lstate = LState.LOADING;
		lines = List.of();
		pool.submit(() -> {
			List<Line> l = List.of();
			try { l = find(t); } catch (Exception ignored) { }
			if (!k.equals(key)) return; // track changed meanwhile
			lines = l;
			lstate = l.isEmpty() ? LState.MISSING : LState.FOUND;
		});
	}

	private static String clean(String s) {
		return s.replaceAll("(?i)\\(.*?\\)|\\[.*?\\]|official (music )?video|\\blyrics?\\b|\\baudio\\b|\\bhd\\b|\\b4k\\b|\\bft\\..*|\\bfeat\\..*", " ")
				.replaceAll("\\s+", " ").trim();
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String get(String url) {
		try {
			HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
			c.setConnectTimeout(5000);
			c.setReadTimeout(8000);
			c.setRequestProperty("User-Agent", "Nova-Client/1.0");
			if (c.getResponseCode() != 200) return null;
			try (InputStream in = c.getInputStream()) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
		} catch (Exception e) {
			return null;
		}
	}

	private List<Line> find(Track t) {
		String a = clean(t.artist), ti = clean(t.title);
		List<String[]> tries = new ArrayList<>();
		tries.add(new String[] { t.artist, t.title });
		tries.add(new String[] { a, ti });
		int d = t.title.indexOf(" - ");
		if (d > 0) tries.add(new String[] { clean(t.title.substring(0, d)), clean(t.title.substring(d + 3)) });
		for (String[] x : tries) {
			if (x[0].isBlank() || x[1].isBlank()) continue;
			String body = get("https://lrclib.net/api/get?track_name=" + enc(x[1]) + "&artist_name=" + enc(x[0])
					+ (t.end > 0 ? "&duration=" + Math.round(t.end) : ""));
			if (body == null) continue;
			try {
				List<Line> l = fromObj(JsonParser.parseString(body).getAsJsonObject());
				if (!l.isEmpty()) return l;
			} catch (Exception ignored) { }
		}
		String q = d > 0 ? clean(t.title) : (a + " " + ti).trim();
		String body = get("https://lrclib.net/api/search?q=" + enc(q));
		if (body != null) {
			try {
				JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
				for (JsonElement e : arr) {
					List<Line> l = fromObj(e.getAsJsonObject());
					if (!l.isEmpty()) return l;
				}
			} catch (Exception ignored) { }
		}
		return List.of();
	}

	private static final Pattern LRC = Pattern.compile("^\\[(\\d+):(\\d+)(?:[.:](\\d+))?\\]\\s*(.*)$");

	private static List<Line> fromObj(JsonObject o) {
		JsonElement e = o.get("syncedLyrics");
		if (e == null || e.isJsonNull()) return List.of();
		List<Line> out = new ArrayList<>();
		for (String ln : e.getAsString().split("\\r?\\n")) {
			Matcher m = LRC.matcher(ln.trim());
			if (!m.matches()) continue;
			long ms = Long.parseLong(m.group(1)) * 60_000L + Long.parseLong(m.group(2)) * 1000L;
			String f = m.group(3);
			if (f != null && !f.isEmpty()) ms += Long.parseLong(f) * (f.length() == 1 ? 100 : f.length() == 2 ? 10 : 1);
			out.add(new Line(ms, m.group(4).trim()));
		}
		out.sort(Comparator.comparingLong(Line::ms));
		return out;
	}
}
