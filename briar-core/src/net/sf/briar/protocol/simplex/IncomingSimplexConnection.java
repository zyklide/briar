package net.sf.briar.protocol.simplex;

import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.plugins.simplex.SimplexTransportReader;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.ProtocolReader;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.UnverifiedBatch;
import net.sf.briar.api.protocol.VerificationExecutor;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionRegistry;
import net.sf.briar.util.ByteUtils;

class IncomingSimplexConnection {

	private static final Logger LOG =
			Logger.getLogger(IncomingSimplexConnection.class.getName());

	private final Executor dbExecutor, verificationExecutor;
	private final DatabaseComponent db;
	private final ConnectionRegistry connRegistry;
	private final ConnectionReaderFactory connFactory;
	private final ProtocolReaderFactory protoFactory;
	private final ConnectionContext ctx;
	private final SimplexTransportReader transport;
	private final ContactId contactId;
	private final TransportId transportId;

	IncomingSimplexConnection(@DatabaseExecutor Executor dbExecutor,
			@VerificationExecutor Executor verificationExecutor,
			DatabaseComponent db, ConnectionRegistry connRegistry,
			ConnectionReaderFactory connFactory,
			ProtocolReaderFactory protoFactory, ConnectionContext ctx,
			SimplexTransportReader transport) {
		this.dbExecutor = dbExecutor;
		this.verificationExecutor = verificationExecutor;
		this.db = db;
		this.connRegistry = connRegistry;
		this.connFactory = connFactory;
		this.protoFactory = protoFactory;
		this.ctx = ctx;
		this.transport = transport;
		contactId = ctx.getContactId();
		transportId = ctx.getTransportId();
	}

	void read() {
		connRegistry.registerConnection(contactId, transportId);
		try {
			ConnectionReader conn = connFactory.createConnectionReader(
					transport.getInputStream(), ctx, true, true);
			InputStream in = conn.getInputStream();
			ProtocolReader reader = protoFactory.createProtocolReader(in);
			// Read packets until EOF
			while(!reader.eof()) {
				if(reader.hasAck()) {
					Ack a = reader.readAck();
					dbExecutor.execute(new ReceiveAck(a));
				} else if(reader.hasBatch()) {
					UnverifiedBatch b = reader.readBatch();
					verificationExecutor.execute(new VerifyBatch(b));
				} else if(reader.hasSubscriptionUpdate()) {
					SubscriptionUpdate s = reader.readSubscriptionUpdate();
					dbExecutor.execute(new ReceiveSubscriptionUpdate(s));
				} else if(reader.hasTransportUpdate()) {
					TransportUpdate t = reader.readTransportUpdate();
					dbExecutor.execute(new ReceiveTransportUpdate(t));
				} else {
					throw new FormatException();
				}
			}
			dispose(false, true);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			dispose(true, true);
		} finally {
			connRegistry.unregisterConnection(contactId, transportId);
		}
	}

	private void dispose(boolean exception, boolean recognised) {
		ByteUtils.erase(ctx.getSecret());
		try {
			transport.dispose(exception, recognised);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private class ReceiveAck implements Runnable {

		private final Ack ack;

		private ReceiveAck(Ack ack) {
			this.ack = ack;
		}

		public void run() {
			try {
				db.receiveAck(contactId, ack);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class VerifyBatch implements Runnable {

		private final UnverifiedBatch batch;

		private VerifyBatch(UnverifiedBatch batch) {
			this.batch = batch;
		}

		public void run() {
			try {
				Batch b = batch.verify();
				dbExecutor.execute(new ReceiveBatch(b));
			} catch(GeneralSecurityException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class ReceiveBatch implements Runnable {

		private final Batch batch;

		private ReceiveBatch(Batch batch) {
			this.batch = batch;
		}

		public void run() {
			try {
				db.receiveBatch(contactId, batch);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class ReceiveSubscriptionUpdate implements Runnable {

		private final SubscriptionUpdate update;

		private ReceiveSubscriptionUpdate(SubscriptionUpdate update) {
			this.update = update;
		}

		public void run() {
			try {
				db.receiveSubscriptionUpdate(contactId, update);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private class ReceiveTransportUpdate implements Runnable {

		private final TransportUpdate update;

		private ReceiveTransportUpdate(TransportUpdate update) {
			this.update = update;
		}

		public void run() {
			try {
				db.receiveTransportUpdate(contactId, update);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}
}
