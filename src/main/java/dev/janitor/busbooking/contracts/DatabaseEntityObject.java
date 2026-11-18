package dev.janitor.busbooking.contracts;

/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 *
 * Marks an application-owned object with an independent JPA entity lifecycle.
 * Implementors must also be mapped with {@code @Entity}; this marker does not
 * replace JPA annotations or make an object persistable by itself.
 *
 * Embedded values, query builders, column declarations, projections, and DTOs
 * do not belong to this contract. The marker exists for classification and
 * future entity-specific constraints only; it defines no runtime behavior.
 */
public interface DatabaseEntityObject {}
