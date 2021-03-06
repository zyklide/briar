package org.briarproject.db;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import org.briarproject.api.Author;
import org.briarproject.api.AuthorId;
import org.briarproject.api.Contact;
import org.briarproject.api.ContactId;
import org.briarproject.api.LocalAuthor;
import org.briarproject.api.Settings;
import org.briarproject.api.TransportConfig;
import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.MessageHeader;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.GroupId;
import org.briarproject.api.messaging.GroupStatus;
import org.briarproject.api.messaging.Message;
import org.briarproject.api.messaging.MessageId;
import org.briarproject.api.messaging.RetentionAck;
import org.briarproject.api.messaging.RetentionUpdate;
import org.briarproject.api.messaging.SubscriptionAck;
import org.briarproject.api.messaging.SubscriptionUpdate;
import org.briarproject.api.messaging.TransportAck;
import org.briarproject.api.messaging.TransportUpdate;
import org.briarproject.api.transport.Endpoint;
import org.briarproject.api.transport.TemporarySecret;

// FIXME: Document the preconditions for calling each method

/**
 * A low-level interface to the database (DatabaseComponent provides a
 * high-level interface). Most operations take a transaction argument, which is
 * obtained by calling {@link #startTransaction()}. Every transaction must be
 * terminated by calling either {@link #abortTransaction(T)} or
 * {@link #commitTransaction(T)}, even if an exception is thrown.
 * <p>
 * Read-write locking is provided by the DatabaseComponent implementation.
 */
interface Database<T> {

	/**
	 * Opens the database and returns true if the database already existed.
	 * <p>
	 * Locking: write.
	 */
	boolean open() throws DbException, IOException;

	/**
	 * Prevents new transactions from starting, waits for all current
	 * transactions to finish, and closes the database.
	 * <p>
	 * Locking: write.
	 */
	void close() throws DbException, IOException;

	/** Starts a new transaction and returns an object representing it. */
	T startTransaction() throws DbException;

	/**
	 * Aborts the given transaction - no changes made during the transaction
	 * will be applied to the database.
	 */
	void abortTransaction(T txn);

	/**
	 * Commits the given transaction - all changes made during the transaction
	 * will be applied to the database.
	 */
	void commitTransaction(T txn) throws DbException;

	/**
	 * Returns the number of transactions started since the transaction count
	 * was last reset.
	 */
	int getTransactionCount();

	/**  Resets the transaction count. */
	void resetTransactionCount();

	/**
	 * Stores a contact associated with the given local and remote pseudonyms,
	 * and returns an ID for the contact.
	 * <p>
	 * Locking: write.
	 */
	ContactId addContact(T txn, Author remote, AuthorId local)
			throws DbException;

	/**
	 * Stores an endpoint.
	 * <p>
	 * Locking: write.
	 */
	void addEndpoint(T txn, Endpoint ep) throws DbException;

	/**
	 * Subscribes to a group, or returns false if the user already has the
	 * maximum number of subscriptions.
	 * <p>
	 * Locking: write.
	 */
	boolean addGroup(T txn, Group g) throws DbException;

	/**
	 * Stores a local pseudonym.
	 * <p>
	 * Locking: write.
	 */
	void addLocalAuthor(T txn, LocalAuthor a) throws DbException;

	/**
	 * Stores a message.
	 * <p>
	 * Locking: write.
	 */
	void addMessage(T txn, Message m, boolean local) throws DbException;

	/**
	 * Records that a message has been offered by the given contact.
	 * <p>
	 * Locking: write.
	 */
	void addOfferedMessage(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Stores the given temporary secrets and deletes any secrets that have
	 * been made obsolete.
	 * <p>
	 * Locking: write.
	 */
	void addSecrets(T txn, Collection<TemporarySecret> secrets)
			throws DbException;

	/**
	 * Initialises the status of the given message with respect to the given
	 * contact.
	 * <p>
	 * Locking: write.
	 * @param ack whether the message needs to be acknowledged.
	 * @param seen whether the contact has seen the message.
	 */
	void addStatus(T txn, ContactId c, MessageId m, boolean ack, boolean seen)
			throws DbException;

	/**
	 * Stores a transport and returns true if the transport was not previously
	 * in the database.
	 * <p>
	 * Locking: write.
	 */
	boolean addTransport(T txn, TransportId t, long maxLatency)
			throws DbException;

	/**
	 * Makes a group visible to the given contact.
	 * <p>
	 * Locking: write.
	 */
	void addVisibility(T txn, ContactId c, GroupId g) throws DbException;

	/**
	 * Returns true if the database contains the given contact.
	 * <p>
	 * Locking: read.
	 */
	boolean containsContact(T txn, AuthorId a) throws DbException;

	/**
	 * Returns true if the database contains the given contact.
	 * <p>
	 * Locking: read.
	 */
	boolean containsContact(T txn, ContactId c) throws DbException;

	/**
	 * Returns true if the user subscribes to the given group.
	 * <p>
	 * Locking: read.
	 */
	boolean containsGroup(T txn, GroupId g) throws DbException;

	/**
	 * Returns true if the database contains the given local pseudonym.
	 * <p>
	 * Locking: read.
	 */
	boolean containsLocalAuthor(T txn, AuthorId a) throws DbException;

	/**
	 * Returns true if the database contains the given message.
	 * <p>
	 * Locking: read.
	 */
	boolean containsMessage(T txn, MessageId m) throws DbException;

	/**
	 * Returns true if the database contains the given transport.
	 * <p>
	 * Locking: read.
	 */
	boolean containsTransport(T txn, TransportId t) throws DbException;

	/**
	 * Returns true if the user subscribes to the given group and the group is
	 * visible to the given contact.
	 * <p>
	 * Locking: read.
	 */
	boolean containsVisibleGroup(T txn, ContactId c, GroupId g)
			throws DbException;

	/**
	 * Returns true if the database contains the given message and the message
	 * is visible to the given contact.
	 * <p>
	 * Locking: read.
	 */
	boolean containsVisibleMessage(T txn, ContactId c, MessageId m)
			throws DbException;

	/**
	 * Returns the number of messages offered by the given contact.
	 * <p>
	 * Locking: read.
	 */
	int countOfferedMessages(T txn, ContactId c) throws DbException;

	/**
	 * Returns the status of all groups to which the user subscribes or can
	 * subscribe, excluding inbox groups.
	 * <p>
	 * Locking: read.
	 */
	Collection<GroupStatus> getAvailableGroups(T txn) throws DbException;

	/**
	 * Returns the configuration for the given transport.
	 * <p>
	 * Locking: read.
	 */
	TransportConfig getConfig(T txn, TransportId t) throws DbException;

	/**
	 * Returns the contact with the given ID.
	 * <p>
	 * Locking: read.
	 */
	Contact getContact(T txn, ContactId c) throws DbException;

	/**
	 * Returns the IDs of all contacts.
	 * <p>
	 * Locking: read.
	 */
	Collection<ContactId> getContactIds(T txn) throws DbException;

	/**
	 * Returns all contacts.
	 * <p>
	 * Locking: read.
	 */
	Collection<Contact> getContacts(T txn) throws DbException;

	/**
	 * Returns all contacts associated with the given local pseudonym.
	 * <p>
	 * Locking: read.
	 */
	Collection<ContactId> getContacts(T txn, AuthorId a) throws DbException;

	/**
	 * Returns all endpoints.
	 * <p>
	 * Locking: read.
	 */
	Collection<Endpoint> getEndpoints(T txn) throws DbException;

	/**
	 * Returns the amount of free storage space available to the database, in
	 * bytes. This is based on the minimum of the space available on the device
	 * where the database is stored and the database's configured size.
	 */
	long getFreeSpace() throws DbException;

	/**
	 * Returns the group with the given ID, if the user subscribes to it.
	 * <p>
	 * Locking: read.
	 */
	Group getGroup(T txn, GroupId g) throws DbException;

	/**
	 * Returns all groups to which the user subscribes.
	 * <p>
	 * Locking: read.
	 */
	Collection<Group> getGroups(T txn) throws DbException;

	/**
	 * Returns the ID of the inbox group for the given contact, or null if no
	 * inbox group has been set.
	 * <p>
	 * Locking: read.
	 */
	GroupId getInboxGroupId(T txn, ContactId c) throws DbException;

	/**
	 * Returns the headers of all messages in the inbox group for the given
	 * contact, or null if no inbox group has been set.
	 * <p>
	 * Locking: read.
	 */
	Collection<MessageHeader> getInboxMessageHeaders(T txn, ContactId c)
			throws DbException;

	/**
	 * Returns the local pseudonym with the given ID.
	 * <p>
	 * Locking: read.
	 */
	LocalAuthor getLocalAuthor(T txn, AuthorId a) throws DbException;

	/**
	 * Returns all local pseudonyms.
	 * <p>
	 * Locking: read.
	 */
	Collection<LocalAuthor> getLocalAuthors(T txn) throws DbException;

	/**
	 * Returns the local transport properties for all transports.
	 * <p>
	 * Locking: read.
	 */
	Map<TransportId, TransportProperties> getLocalProperties(T txn)
			throws DbException;

	/**
	 * Returns the local transport properties for the given transport.
	 * <p>
	 * Locking: read.
	 */
	TransportProperties getLocalProperties(T txn, TransportId t)
			throws DbException;

	/**
	 * Returns the body of the message identified by the given ID.
	 * <p>
	 * Locking: read.
	 */
	byte[] getMessageBody(T txn, MessageId m) throws DbException;

	/**
	 * Returns the headers of all messages in the given group.
	 * <p>
	 * Locking: read.
	 */
	Collection<MessageHeader> getMessageHeaders(T txn, GroupId g)
			throws DbException;

	/**
	 * Returns the IDs of some messages received from the given contact that
	 * need to be acknowledged, up to the given number of messages.
	 * <p>
	 * Locking: read.
	 */
	Collection<MessageId> getMessagesToAck(T txn, ContactId c, int maxMessages)
			throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be offered to the
	 * given contact, up to the given number of messages.
	 * <p>
	 * Locking: read.
	 */
	Collection<MessageId> getMessagesToOffer(T txn, ContactId c,
			int maxMessages) throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be sent to the
	 * given contact, up to the given total length.
	 * <p>
	 * Locking: read.
	 */
	Collection<MessageId> getMessagesToSend(T txn, ContactId c, int maxLength)
			throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be requested from
	 * the given contact, up to the given number of messages.
	 * <p>
	 * Locking: read.
	 */
	Collection<MessageId> getMessagesToRequest(T txn, ContactId c,
			int maxMessages) throws DbException;

	/**
	 * Returns the IDs of the oldest messages in the database, with a total
	 * size less than or equal to the given size.
	 * <p>
	 * Locking: read.
	 */
	Collection<MessageId> getOldMessages(T txn, int size) throws DbException;

	/**
	 * Returns the parent of the given message, or null if either the message
	 * has no parent, or the parent is absent from the database, or the parent
	 * belongs to a different group.
	 * <p>
	 * Locking: read.
	 */
	MessageId getParent(T txn, MessageId m) throws DbException;

	/**
	 * Returns the message identified by the given ID, in serialised form.
	 * <p>
	 * Locking: read.
	 */
	byte[] getRawMessage(T txn, MessageId m) throws DbException;

	/**
	 * Returns true if the given message is marked as read.
	 * <p>
	 * Locking: read.
	 */
	boolean getReadFlag(T txn, MessageId m) throws DbException;

	/**
	 * Returns all remote properties for the given transport.
	 * <p>
	 * Locking: read.
	 */
	Map<ContactId, TransportProperties> getRemoteProperties(T txn,
			TransportId t) throws DbException;

	/**
	 * Returns the IDs of some messages that are eligible to be sent to the
	 * given contact and have been requested by the contact, up to the given
	 * total length.
	 * <p>
	 * Locking: read.
	 */
	Collection<MessageId> getRequestedMessagesToSend(T txn, ContactId c,
			int maxLength) throws DbException;

	/**
	 * Returns a retention ack for the given contact, or null if no ack is due.
	 * <p>
	 * Locking: write.
	 */
	RetentionAck getRetentionAck(T txn, ContactId c) throws DbException;

	/**
	 * Returns a retention update for the given contact and updates its expiry
	 * time using the given latency, or returns null if no update is due.
	 * <p>
	 * Locking: write.
	 */
	RetentionUpdate getRetentionUpdate(T txn, ContactId c, long maxLatency)
			throws DbException;

	/**
	 * Returns all temporary secrets.
	 * <p>
	 * Locking: read.
	 */
	Collection<TemporarySecret> getSecrets(T txn) throws DbException;

	/**
	 * Returns all settings.
	 * <p>
	 * Locking: read.
	 */
	Settings getSettings(T txn) throws DbException;

	/**
	 * Returns all contacts who subscribe to the given group.
	 * <p>
	 * Locking: read.
	 */
	Collection<Contact> getSubscribers(T txn, GroupId g) throws DbException;

	/**
	 * Returns a subscription ack for the given contact, or null if no ack is
	 * due.
	 * <p>
	 * Locking: write.
	 */
	SubscriptionAck getSubscriptionAck(T txn, ContactId c) throws DbException;

	/**
	 * Returns a subscription update for the given contact and updates its
	 * expiry time using the given latency, or returns null if no update is due.
	 * <p>
	 * Locking: write.
	 */
	SubscriptionUpdate getSubscriptionUpdate(T txn, ContactId c,
			long maxLatency) throws DbException;

	/**
	 * Returns a collection of transport acks for the given contact, or null if
	 * no acks are due.
	 * <p>
	 * Locking: write.
	 */
	Collection<TransportAck> getTransportAcks(T txn, ContactId c)
			throws DbException;

	/**
	 * Returns the maximum latencies of all local transports.
	 * <p>
	 * Locking: read.
	 */
	Map<TransportId, Long> getTransportLatencies(T txn) throws DbException;

	/**
	 * Returns a collection of transport updates for the given contact and
	 * updates their expiry times using the given latency, or returns null if
	 * no updates are due.
	 * <p>
	 * Locking: write.
	 */
	Collection<TransportUpdate> getTransportUpdates(T txn, ContactId c,
			long maxLatency) throws DbException;

	/**
	 * Returns the number of unread messages in each subscribed group.
	 * <p>
	 * Locking: read.
	 */
	Map<GroupId, Integer> getUnreadMessageCounts(T txn) throws DbException;

	/**
	 * Returns the IDs of all contacts to which the given group is visible.
	 * <p>
	 * Locking: read.
	 */
	Collection<ContactId> getVisibility(T txn, GroupId g) throws DbException;

	/**
	 * Increments the outgoing stream counter for the given endpoint in the
	 * given rotation period and returns the old value, or -1 if the counter
	 * does not exist.
	 * <p>
	 * Locking: write.
	 */
	long incrementStreamCounter(T txn, ContactId c, TransportId t, long period)
			throws DbException;

	/**
	 * Increments the retention time versions for all contacts to indicate that
	 * the database's retention time has changed and updates should be sent.
	 * <p>
	 * Locking: write.
	 */
	void incrementRetentionVersions(T txn) throws DbException;

	/**
	 * Marks the given messages as not needing to be acknowledged to the
	 * given contact.
	 * <p>
	 * Locking: write.
	 */
	void lowerAckFlag(T txn, ContactId c, Collection<MessageId> acked)
			throws DbException;

	/**
	 * Marks the given messages as not having been requested by the given
	 * contact.
	 * <p>
	 * Locking: write.
	 */
	void lowerRequestedFlag(T txn, ContactId c, Collection<MessageId> requested)
			throws DbException;

	/**
	 * Merges the given configuration with the existing configuration for the
	 * given transport.
	 * <p>
	 * Locking: write.
	 */
	void mergeConfig(T txn, TransportId t, TransportConfig config)
			throws DbException;

	/**
	 * Merges the given properties with the existing local properties for the
	 * given transport.
	 * <p>
	 * Locking: write.
	 */
	void mergeLocalProperties(T txn, TransportId t, TransportProperties p)
			throws DbException;

	/**
	 * Merges the given settings with the existing settings.
	 * <p>
	 * Locking: write.
	 */
	void mergeSettings(T txn, Settings s) throws DbException;

	/**
	 * Marks a message as needing to be acknowledged to the given contact.
	 * <p>
	 * Locking: write.
	 */
	void raiseAckFlag(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Marks a message as having been requested by the given contact.
	 * <p>
	 * Locking: write.
	 */
	void raiseRequestedFlag(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Marks a message as having been seen by the given contact.
	 * <p>
	 * Locking: write.
	 */
	void raiseSeenFlag(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Removes a contact from the database.
	 * <p>
	 * Locking: write.
	 */
	void removeContact(T txn, ContactId c) throws DbException;

	/**
	 * Unsubscribes from a group. Any messages belonging to the group are
	 * deleted from the database.
	 * <p>
	 * Locking: write.
	 */
	void removeGroup(T txn, GroupId g) throws DbException;

	/**
	 * Removes a local pseudonym (and all associated contacts) from the
	 * database.
	 * <p>
	 * Locking: write.
	 */
	void removeLocalAuthor(T txn, AuthorId a) throws DbException;

	/**
	 * Removes a message (and all associated state) from the database.
	 * <p>
	 * Locking: write.
	 */
	void removeMessage(T txn, MessageId m) throws DbException;

	/**
	 * Removes an offered message that was offered by the given contact, or
	 * returns false if there is no such message.
	 * <p>
	 * Locking: write.
	 */
	boolean removeOfferedMessage(T txn, ContactId c, MessageId m)
			throws DbException;

	/**
	 * Removes the given offered messages that were offered by the given
	 * contact.
	 * <p>
	 * Locking: write.
	 */
	void removeOfferedMessages(T txn, ContactId c,
			Collection<MessageId> requested) throws DbException;

	/**
	 * Removes a transport (and all associated state) from the database.
	 * <p>
	 * Locking: write.
	 */
	void removeTransport(T txn, TransportId t) throws DbException;

	/**
	 * Makes a group invisible to the given contact.
	 * <p>
	 * Locking: write.
	 */
	void removeVisibility(T txn, ContactId c, GroupId g) throws DbException;

	/**
	 * Resets the transmission count and expiry time of the given message with
	 * respect to the given contact.
	 * <p>
	 * Locking: write.
	 */
	void resetExpiryTime(T txn, ContactId c, MessageId m) throws DbException;

	/**
	 * Sets the reordering window for the given endpoint in the given rotation
	 * period.
	 * <p>
	 * Locking: write.
	 */
	void setReorderingWindow(T txn, ContactId c, TransportId t, long period,
			long centre, byte[] bitmap) throws DbException;

	/**
	 * Updates the groups to which the given contact subscribes and returns
	 * true, unless an update with an equal or higher version number has
	 * already been received from the contact.
	 * <p>
	 * Locking: write.
	 */
	boolean setGroups(T txn, ContactId c, Collection<Group> groups,
			long version) throws DbException;

	/**
	 * Makes a group visible to the given contact, adds it to the contact's
	 * subscriptions, and sets it as the inbox group for the contact.
	 * <p>
	 * Locking: write.
	 */
	public void setInboxGroup(T txn, ContactId c, Group g) throws DbException;

	/**
	 * Marks a message as read or unread.
	 * <p>
	 * Locking: write.
	 */
	void setReadFlag(T txn, MessageId m, boolean read) throws DbException;

	/**
	 * Sets the remote transport properties for the given contact, replacing
	 * any existing properties.
	 * <p>
	 * Locking: write.
	 */
	void setRemoteProperties(T txn, ContactId c,
			Map<TransportId, TransportProperties> p) throws DbException;

	/**
	 * Updates the remote transport properties for the given contact and the
	 * given transport, replacing any existing properties, and returns true,
	 * unless an update with an equal or higher version number has already been
	 * received from the contact.
	 * <p>
	 * Locking: write.
	 */
	boolean setRemoteProperties(T txn, ContactId c, TransportId t,
			TransportProperties p, long version) throws DbException;

	/**
	 * Sets the retention time of the given contact's database and returns
	 * true, unless an update with an equal or higher version number has
	 * already been received from the contact.
	 * <p>
	 * Locking: write.
	 */
	boolean setRetentionTime(T txn, ContactId c, long retention, long version)
			throws DbException;

	/**
	 * Records a retention ack from the given contact for the given version,
	 * unless the contact has already acked an equal or higher version.
	 * <p>
	 * Locking: write.
	 */
	void setRetentionUpdateAcked(T txn, ContactId c, long version)
			throws DbException;

	/**
	 * Records a subscription ack from the given contact for the given version,
	 * unless the contact has already acked an equal or higher version.
	 * <p>
	 * Locking: write.
	 */
	void setSubscriptionUpdateAcked(T txn, ContactId c, long version)
			throws DbException;

	/**
	 * Records a transport ack from the give contact for the given version,
	 * unless the contact has already acked an equal or higher version.
	 * <p>
	 * Locking: write.
	 */
	void setTransportUpdateAcked(T txn, ContactId c, TransportId t,
			long version) throws DbException;

	/**
	 * Makes a group visible or invisible to future contacts by default.
	 * <p>
	 * Locking: write.
	 */
	void setVisibleToAll(T txn, GroupId g, boolean all) throws DbException;

	/**
	 * Updates the transmission count and expiry time of the given message
	 * with respect to the given contact, using the latency of the transport
	 * over which it was sent.
	 * <p>
	 * Locking: write.
	 */
	void updateExpiryTime(T txn, ContactId c, MessageId m, long maxLatency)
			throws DbException;
}
