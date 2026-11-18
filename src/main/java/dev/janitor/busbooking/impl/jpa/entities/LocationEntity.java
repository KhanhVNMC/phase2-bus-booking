package dev.janitor.busbooking.impl.jpa.entities;

import dev.janitor.busbooking.contracts.DatabaseEntityObject;
import dev.janitor.busbooking.model.Location;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "locations")
@Getter @Setter
@NoArgsConstructor
public class LocationEntity implements DatabaseEntityObject {
	@Id
	@Column(name = "location_id")
	private long id;
	
	@Column(name = "name", nullable = false)
	private String name;
	
	@Column(name = "province", nullable = false)
	private String province;
	
	@Column(name = "address", nullable = false)
	private String address;
	
	public static LocationEntity fromModel(Location model) {
		var entity = new LocationEntity();
		entity.id = model.id();
		entity.name = model.name();
		entity.province = model.province();
		entity.address = model.address();
		return entity;
	}
	
	public Location toModel() {
		return new Location(id, name, province, address);
	}
}
