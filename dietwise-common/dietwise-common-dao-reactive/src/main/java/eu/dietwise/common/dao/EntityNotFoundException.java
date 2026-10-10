package eu.dietwise.common.dao;

import java.io.Serializable;

/**
 * An entity was not found. The caller may optionally specify the class and key of the entity that was not found.
 */
public class EntityNotFoundException extends DaoException {
	private final Class<?> entityClass;
	private final Serializable entityId;

	public EntityNotFoundException(Class<?> entityClass, Serializable entityId, String message) {
		super(message);
		this.entityClass = entityClass;
		this.entityId = entityId;
	}

	public EntityNotFoundException(Class<?> entityClass, Serializable entityId) {
		super("Entity not found" + (entityClass != null ? " " + entityClass.getName() : "") + (entityId != null ? " " + entityId : ""));
		this.entityClass = entityClass;
		this.entityId = entityId;
	}

	public EntityNotFoundException(String message) {
		super(message);
		this.entityClass = null;
		this.entityId = null;
	}

	public EntityNotFoundException(Class<?> entityClass, Serializable entityId, String message, Throwable cause) {
		super(message, cause);
		this.entityClass = entityClass;
		this.entityId = entityId;
	}

	public EntityNotFoundException(Class<?> entityClass, Serializable entityId, Throwable cause) {
		super("Entity not found" + (entityClass != null ? " " + entityClass.getName() : "") + (entityId != null ? " " + entityId : ""), cause);
		this.entityClass = entityClass;
		this.entityId = entityId;
	}

	public EntityNotFoundException(String message, Throwable cause) {
		super(message, cause);
		this.entityClass = null;
		this.entityId = null;
	}

	public Class<?> getEntityClass() {
		return entityClass;
	}

	public Serializable getEntityId() {
		return entityId;
	}
}
