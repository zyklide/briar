package org.briarproject.db;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import javax.inject.Inject;

import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.db.DbException;
import org.briarproject.api.system.Clock;
import org.briarproject.api.system.FileUtils;
import org.briarproject.util.StringUtils;

/** Contains all the H2-specific code for the database. */
class H2Database extends JdbcDatabase {

	private static final String HASH_TYPE = "BINARY(48)";
	private static final String BINARY_TYPE = "BINARY";
	private static final String COUNTER_TYPE = "INT NOT NULL AUTO_INCREMENT";
	private static final String SECRET_TYPE = "BINARY(32)";

	private final DatabaseConfig config;
	private final FileUtils fileUtils;
	private final String url;

	@Inject
	H2Database(DatabaseConfig config, FileUtils fileUtils, Clock clock) {
		super(HASH_TYPE, BINARY_TYPE, COUNTER_TYPE, SECRET_TYPE, clock);
		this.config = config;
		this.fileUtils = fileUtils;
		String path = new File(config.getDatabaseDirectory(), "db").getPath();
		// FIXME: Remove WRITE_DELAY=0 after implementing BTPv2?
		url = "jdbc:h2:split:" + path + ";CIPHER=AES;MULTI_THREADED=1"
				+ ";WRITE_DELAY=0;DB_CLOSE_ON_EXIT=false";
	}

	public boolean open() throws DbException, IOException {
		boolean reopen = config.databaseExists();
		if(!reopen) config.getDatabaseDirectory().mkdirs();
		super.open("org.h2.Driver", reopen);
		return reopen;
	}

	public void close() throws DbException {
		// H2 will close the database when the last connection closes
		try {
			super.closeAllConnections();
		} catch(SQLException e) {
			throw new DbException(e);
		}
	}

	public long getFreeSpace() throws DbException {
		File dir = config.getDatabaseDirectory();
		long maxSize = config.getMaxSize();
		try {
			long free = fileUtils.getFreeSpace(dir);
			long used = getDiskSpace(dir);
			long quota = maxSize - used;
			long min =  Math.min(free, quota);
			return min;
		} catch(IOException e) {
			throw new DbException(e);
		}
	}

	private long getDiskSpace(File f) {
		if(f.isDirectory()) {
			long total = 0;
			for(File child : f.listFiles()) total += getDiskSpace(child);
			return total;
		} else if(f.isFile()) {
			return f.length();
		} else {
			return 0;
		}
	}

	protected Connection createConnection() throws SQLException {
		byte[] key = config.getEncryptionKey();
		if(key == null) throw new IllegalStateException();
		char[] password = encodePassword(key);
		Properties props = new Properties();
		props.setProperty("user", "user");
		props.put("password", password);
		try {
			return DriverManager.getConnection(url, props);
		} finally {
			for(int i = 0; i < password.length; i++) password[i] = 0;
		}
	}

	private char[] encodePassword(byte[] key) {
		// The database password is the hex-encoded key
		char[] hex = StringUtils.toHexChars(key);
		// Separate the database password from the user password with a space
		char[] user = "password".toCharArray();
		char[] combined = new char[hex.length + 1 + user.length];
		System.arraycopy(hex, 0, combined, 0, hex.length);
		combined[hex.length] = ' ';
		System.arraycopy(user, 0, combined, hex.length + 1, user.length);
		// Erase the hex-encoded key
		for(int i = 0; i < hex.length; i++) hex[i] = 0;
		return combined;
	}

	protected void flushBuffersToDisk(Statement s) throws SQLException {
		// FIXME: Remove this after implementing BTPv2?
		s.execute("CHECKPOINT SYNC");
	}
}
