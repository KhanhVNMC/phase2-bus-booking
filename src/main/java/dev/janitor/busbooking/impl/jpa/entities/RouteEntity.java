package dev.janitor.busbooking.impl.jpa.entities;

import dev.janitor.busbooking.consts.ActiveStatus;
import dev.janitor.busbooking.contracts.DatabaseEntityObject;
import dev.janitor.busbooking.model.Route;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "routes", indexes = {
	@Index(name = "idx_routes_endpoints", columnList = "origin_id, destination_id")
})
@Getter @Setter
@NoArgsConstructor
public class RouteEntity implements DatabaseEntityObject {
	@Id
	@Column(name = "route_id")
	private long id;
	
	@Column(name = "origin_id", nullable = false)
	private long originId;
	
	@Column(name = "destination_id", nullable = false)
	private long destinationId;
	
	@Column(name = "estimated_duration_minutes", nullable = false)
	private int estimatedDurationMinutes;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private ActiveStatus status;
	
	public static RouteEntity fromModel(Route model) {
		var entity = new RouteEntity();
		entity.id = model.id();
		entity.originId = model.originId();
		entity.destinationId = model.destinationId();
		entity.estimatedDurationMinutes = model.estimatedDurationMinutes();
		entity.status = model.status();
		return entity;
	}
	
	public Route toModel() {
		return new Route(id, originId, destinationId, estimatedDurationMinutes, status);
	}
}
