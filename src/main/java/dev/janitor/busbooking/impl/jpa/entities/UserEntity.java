package dev.janitor.busbooking.impl.jpa.entities;

import dev.janitor.busbooking.consts.ActiveStatus;
import dev.janitor.busbooking.consts.UserRole;
import dev.janitor.busbooking.contracts.DatabaseEntityObject;
import dev.janitor.busbooking.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor
public class UserEntity implements DatabaseEntityObject {
	@Id
	@Column(name = "user_id")
	private long id;
	
	@Column(name = "full_name", nullable = false)
	private String fullName;
	
	@Column(name = "email", nullable = false, unique = true)
	private String email;
	
	@Column(name = "password_hash", nullable = false)
	private String passwordHash;
	
	@Column(name = "phone", nullable = false)
	private String phone;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false)
	private UserRole role;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private ActiveStatus status;
	
	@Column(name = "email_verified", nullable = false)
	private boolean emailVerified;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "created_at", nullable = false)
	private long createdAt;
	
	@Column(name = "updated_at", nullable = false)
	private long updatedAt;
	
	public static UserEntity fromModel(User model) {
		var entity = new UserEntity();
		entity.id = model.id();
		entity.fullName = model.fullName();
		entity.email = model.email();
		entity.passwordHash = model.passwordHash();
		entity.phone = model.phone();
		entity.role = model.role();
		entity.status = model.status();
		entity.emailVerified = model.emailVerified();
		entity.createdAt = model.createdAt();
		entity.updatedAt = model.updatedAt();
		return entity;
	}
	
	public User toModel() {
		return new User(
			id, fullName, email, passwordHash, phone,
			role, status, emailVerified, createdAt, updatedAt
		);
	}
}
