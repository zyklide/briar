package net.sf.briar.protocol.simplex;

import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.plugins.simplex.SimplexTransportWriter;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.ProtocolWriter;
import net.sf.briar.api.protocol.ProtocolWriterFactory;
import net.sf.briar.api.protocol.RawBatch;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionRegistry;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.util.ByteUtils;

class OutgoingSimplexConnection {

	private static final Logger LOG =
			Logger.getLogger(OutgoingSimplexConnection.class.getName());

	private final DatabaseComponent db;
	private final ConnectionRegistry connRegistry;
	private final ConnectionWriterFactory connFactory;
	private final ProtocolWriterFactory protoFactory;
	private final ConnectionContext ctx;
	private final SimplexTransportWriter transport;
	private final ContactId contactId;
	private final TransportId transportId;

	OutgoingSimplexConnection(DatabaseComponent db,
			ConnectionRegistry connRegistry,
			ConnectionWriterFactory connFactory,
			ProtocolWriterFactory protoFactory, ConnectionContext ctx,
			SimplexTransportWriter transport) {
		this.db = db;
		this.connRegistry = connRegistry;
		this.connFactory = connFactory;
		this.protoFactory = protoFactory;
		this.ctx = ctx;
		this.transport = transport;
		contactId = ctx.getContactId();
		transportId = ctx.getTransportId();
	}

	void write() {
		connRegistry.registerConnection(contactId, transportId);
		try {
			ConnectionWriter conn = connFactory.createConnectionWriter(
					transport.getOutputStream(), transport.getCapacity(),
					ctx, false, true);
			OutputStream out = conn.getOutputStream();
			ProtocolWriter writer = protoFactory.createProtocolWriter(out,
					transport.shouldFlush());
			// There should be enough space for a packet
			long capacity = conn.getRemainingCapacity();
			if(capacity < MAX_PACKET_LENGTH) throw new EOFException();
			// Write a transport update
			TransportUpdate t = db.generateTransportUpdate(contactId);
			if(t != null) writer.writeTransportUpdate(t);
			// If there's space, write a subscription update
			capacity = conn.getRemainingCapacity();
			if(capacity >= MAX_PACKET_LENGTH) {
				SubscriptionUpdate s = db.generateSubscriptionUpdate(contactId);
				if(s != null) writer.writeSubscriptionUpdate(s);
			}
			// Write acks until you can't write acks no more
			capacity = conn.getRemainingCapacity();
			int maxBatches = writer.getMaxBatchesForAck(capacity);
			Ack a = db.generateAck(contactId, maxBatches);
			while(a != null) {
				writer.writeAck(a);
				capacity = conn.getRemainingCapacity();
				maxBatches = writer.getMaxBatchesForAck(capacity);
				a = db.generateAck(contactId, maxBatches);
			}
			// Write batches until you can't write batches no more
			capacity = conn.getRemainingCapacity();
			capacity = writer.getMessageCapacityForBatch(capacity);
			RawBatch b = db.generateBatch(contactId, (int) capacity);
			while(b != null) {
				writer.writeBatch(b);
				capacity = conn.getRemainingCapacity();
				capacity = writer.getMessageCapacityForBatch(capacity);
				b = db.generateBatch(contactId, (int) capacity);
			}
			writer.flush();
			writer.close();
			dispose(false);
		} catch(DbException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			dispose(true);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			dispose(true);
		} finally {
			connRegistry.unregisterConnection(contactId, transportId);
		}
	}

	private void dispose(boolean exception) {
		ByteUtils.erase(ctx.getSecret());
		try {
			transport.dispose(exception);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}
}
