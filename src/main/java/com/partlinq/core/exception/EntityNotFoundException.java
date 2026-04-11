package com.partlinq.core.exception;

import java.util.UUID;

/**
 * Exception thrown when an entity is not found in the database.
 * Provides entity name and ID for debugging purposes.
 */
public class EntityNotFoundException extends RuntimeException {

	private final String entityName;
	private final UUID entityId;

	/**
	 * Construct with entity name and ID.
	 *
	 * @param entityName Name of the entity (e.g., "Order", "Technician")
	 * @param entityId   UUID of the entity that was not found
	 */
	public EntityNotFoundException(String entityName, UUID entityId) {
		super(String.format("%s with ID %s not found", entityName, entityId));
		this.entityName = entityName;
		this.entityId = entityId;
	}

	/**
	 * Construct with entity name and custom message.
	 *
	 * @param entityName Name of the entity
	 * @param message    Custom error message
	 */
	public EntityNotFoundException(String entityName, String message) {
		super(message);
		this.entityName = entityName;
		this.entityId = null;
	}

	public String getEntityName() {
		return entityName;
	}

	public UUID getEntityId() {
		return entityId;
	}
}
