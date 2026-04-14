package com.colligendis.server.logger;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.boot.logging.LogLevel;

import com.colligendis.server.parser.numista.NumistaPage;

import lombok.extern.slf4j.Slf4j;

/**
 * Collects pipeline step log lines and prints them at the end.
 * <p>
 * Intended to be attached to a single {@link NumistaPage} instance.
 */
@Slf4j
public class BaseLogger {

	protected final ConcurrentLinkedQueue<IndentLine> indentLines = new ConcurrentLinkedQueue<>();

	// private final LogLevel indentLevel = LogLevel.valueOf(
	// System.getProperty("logging.level.com.colligendis", "DEBUG").toUpperCase());

	private final LogLevel indentLevel = LogLevel.TRACE;

	public static final String ANSI_BLACK = "\u001B[30m";
	public static final String ANSI_RED = "\u001B[31m";

	public static final String ANSI_YELLOW = "\u001B[33m";
	public static final String ANSI_LIGHT_YELLOW = "\u001B[93m";

	public static final String ANSI_ORANGE = "\u001B[38;5;208m";

	public static final String ANSI_GREEN = "\u001B[32m";
	public static final String ANSI_LIGHT_GREEN = "\u001B[92m";
	public static final String ANSI_DARK_GREEN = "\u001B[38;5;28m";

	public static final String ANSI_BLUE = "\u001B[34m";

	public static final String ANSI_RESET = "\u001B[0m";

	private void log(String line, LogLevel level) {
		if (line == null) {
			return;
		}
		indentLines.add(new IndentLine(line, level));
	}

	public BaseLogger() {
		System.out.println("BaseLogger indentLevel: " + indentLevel);
	}

	/**
	 * Same placeholder rules as SLF4J: use {@code {}} for arguments (not {@code %s}
	 * from
	 * {@link String#format}).
	 */
	private String slf4jFormat(String pattern, Object... args) {
		FormattingTuple tuple = MessageFormatter.arrayFormat(pattern, args);
		String message = tuple.getMessage();
		Throwable t = tuple.getThrowable();
		if (t != null) {
			message = message + " — " + t;
		}
		return message;
	}

	public void error(String format, Object... args) {
		log(ANSI_RED + slf4jFormat(format, args) + ANSI_RESET, LogLevel.ERROR);
	}

	public void warning(String format, Object... args) {
		log(ANSI_ORANGE + slf4jFormat(format, args) + ANSI_RESET, LogLevel.WARN);
	}

	public void info(String format, Object... args) {
		log(ANSI_BLACK + slf4jFormat(format, args) + ANSI_RESET, LogLevel.INFO);
	}

	public void infoGreen(String format, Object... args) {
		log(ANSI_GREEN + slf4jFormat(format, args) + ANSI_RESET, LogLevel.INFO);
	}

	public void infoOrange(String format, Object... args) {
		log(ANSI_ORANGE + slf4jFormat(format, args) + ANSI_RESET, LogLevel.INFO);
	}

	public void infoBlue(String format, Object... args) {
		log(ANSI_BLUE + slf4jFormat(format, args) + ANSI_RESET, LogLevel.INFO);
	}

	public void debug(String format, Object... args) {
		log(ANSI_BLACK + slf4jFormat(format, args) + ANSI_RESET, LogLevel.DEBUG);
	}

	public void debugOrange(String format, Object... args) {
		log(ANSI_ORANGE + slf4jFormat(format, args) + ANSI_RESET, LogLevel.DEBUG);
	}

	public void debugGreen(String format, Object... args) {
		log(ANSI_DARK_GREEN + slf4jFormat(format, args) + ANSI_RESET, LogLevel.DEBUG);
	}

	public void debugRed(String format, Object... args) {
		log(ANSI_RED + slf4jFormat(format, args) + ANSI_RESET, LogLevel.DEBUG);
	}

	public void trace(String format, Object... args) {
		log(ANSI_BLACK + slf4jFormat(format, args) + ANSI_RESET, LogLevel.TRACE);
	}

	public void traceRed(String format, Object... args) {
		log(ANSI_RED + slf4jFormat(format, args) + ANSI_RESET, LogLevel.TRACE);
	}

	public void traceOrange(String format, Object... args) {
		log(ANSI_ORANGE + slf4jFormat(format, args) + ANSI_RESET, LogLevel.TRACE);
	}

	public void traceGreen(String format, Object... args) {
		log(ANSI_DARK_GREEN + slf4jFormat(format, args) + ANSI_RESET, LogLevel.TRACE);
	}

	/**
	 * Prints collected lines to stdout line-by-line, then clears the buffer.
	 */
	public void flushToTerminal() {
		IndentLine line;
		while ((line = indentLines.poll()) != null) {
			LogLevel threshold = null;
			try {
				threshold = this.indentLevel;
			} catch (Exception e) {
				// Fallback, show all if indentLevel is absent
			}
			if (threshold == null || line.level.ordinal() >= threshold.ordinal()) {
				System.out.println(line.line);
			}

		}
	}

	private record IndentLine(String line, LogLevel level) {
	}
}
