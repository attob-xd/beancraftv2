package org.teavm.classlib.java.nio.file;

/**
 * SYNC and DSYNC are accepted and ignored: writes here go to TeaVM's filesystem, which has
 * no separate device cache to flush.
 */
public enum TStandardOpenOption implements TOpenOption {
	READ, WRITE, APPEND, TRUNCATE_EXISTING, CREATE, CREATE_NEW, DELETE_ON_CLOSE, SPARSE, SYNC, DSYNC
}
