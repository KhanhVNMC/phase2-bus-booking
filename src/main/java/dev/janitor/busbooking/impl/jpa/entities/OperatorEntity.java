package dev.janitor.busbooking.impl.jpa.entities;

import dev.janitor.busbooking.consts.ActiveStatus;
import dev.janitor.busbooking.contracts.DatabaseEntityObject;
import dev.janitor.busbooking.model.Operator;
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
@Table(name = "operators")
@Getter @Setter
@NoArgsConstructor
public class OperatorEntity implements DatabaseEntityObject {
	@Id
	@Column(name = "operator_id")
	private long id;
	
	@Column(name = "name", nullable = false)
	private String name;
	
	@Column(name = "address", nullable = false)
	private String address;
	
	@Column(name = "phone", nullable = false)
	private String phone;
	
	@Column(name = "email", nullable = false)
	private String email;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private ActiveStatus status;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "created_at", nullable = false)
	private long createdAt;
	
	public static OperatorEntity fromModel(Operator model) {
		var entity = new OperatorEntity();
		entity.id = model.id();
		entity.name = model.name();
		entity.address = model.address();
		entity.phone = model.phone();
		entity.email = model.email();
		entity.status = model.status();
		entity.createdAt = model.createdAt();
		return entity;
	}
	
	public Operator toModel() {
		return new Operator(id, name, address, phone, email, status, createdAt);
	}
}
