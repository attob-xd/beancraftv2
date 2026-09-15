package com.mojang.logging;

/**
 * The launcher's log window pulled lines off a queue; there is no such window here, so
 * nothing is ever queued. See LogUtils.
 */
public class LogQueues {

	public static String getNextLogEvent(String name) {
		return null;
	}

	public static void createQueue(String name, int capacity) {
	}
}
