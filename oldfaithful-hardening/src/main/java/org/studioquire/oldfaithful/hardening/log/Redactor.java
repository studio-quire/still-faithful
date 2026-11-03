package org.studioquire.oldfaithful.hardening.log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Takes secrets out of log lines before they reach the console or our log file: the session the launcher passed
 * (found in the launch arguments, so it's caught however it's printed), and anything shaped like a legacy session
 * ("token:...") or a JWT, which is what Microsoft-account access tokens are.
 */
public final class Redactor {
	static final String MARK = "<redacted>";

	private static final List<String> SECRET_FLAGS = Arrays.asList("--session", "--sessionid", "--accessToken", "--password");
	private static final Pattern LEGACY_SESSION = Pattern.compile("token:[^\\s,]{8,}");
	private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]*");
	// Shorter values ("-", "0", a player name) would blank out ordinary text.
	private static final int MIN_SECRET = 8;

	private final List<String> secrets;

	public Redactor(List<String> secrets) {
		List<String> list = new ArrayList<String>();
		for (String s : secrets) if (s != null && s.length() >= MIN_SECRET && !list.contains(s)) list.add(s);
		// Longest first, so a whole session is replaced before the token inside it.
		list.sort(Comparator.comparingInt(String::length).reversed());
		this.secrets = list;
	}

	/** The values of the launcher's secret arguments, and the access token inside a "token:<access>:<uuid>" session. */
	public static Redactor fromLaunchArguments(String[] args) {
		List<String> secrets = new ArrayList<String>();
		for (int i = 0; i + 1 < args.length; i++) {
			if (!SECRET_FLAGS.contains(args[i])) continue;
			String value = args[i + 1];
			secrets.add(value);
			if (value.startsWith("token:")) {
				String[] parts = value.split(":");
				if (parts.length >= 2) secrets.add(parts[1]);
			}
		}
		return new Redactor(secrets);
	}

	public String redact(String text) {
		for (String s : secrets) {
			if (text.contains(s)) text = text.replace(s, MARK);
		}
		if (text.contains("token:")) text = LEGACY_SESSION.matcher(text).replaceAll("token:" + MARK);
		if (text.contains("eyJ")) text = JWT.matcher(text).replaceAll(MARK);
		return text;
	}
}
