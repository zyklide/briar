package net.sf.briar.api.protocol;

import java.util.Arrays;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a group to which
 * users may subscribe.
 */
public class GroupId extends UniqueId {

	public GroupId(byte[] id) {
		super(id);
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof GroupId)
			return Arrays.equals(id, ((GroupId) o).id);
		return false;
	}
}