package dev.janitor.busbooking.contracts;

/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 *
 * Marks an application-owned object that may be serialized into Redis, either
 * directly or as part of another Redis value. Implementors must remain safely
 * round-trip compatible with the configured Jackson mapper.
 *
 * This is not a marker for HTTP request or response DTOs, and it does not make
 * a type serializable automatically. Constructors, accessors, field types, and
 * Jackson annotations must still preserve the object's Redis representation.
 */
public interface RemoteCompatibleObject {}
