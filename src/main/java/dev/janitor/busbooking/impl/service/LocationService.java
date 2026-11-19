package dev.janitor.busbooking.impl.service;

import java.util.List;

import dev.janitor.busbooking.model.Location;
import dev.janitor.busbooking.repository.ILocationRepository;
import dev.janitor.busbooking.service.ILocationService;
import dev.janitor.busbooking.service.ServiceException;
import dev.janitor.busbooking.utils.Snowflake;
import dev.janitor.busbooking.utils.Sputnik;

// locations are freely editable at any time, only the name + province + address must stay unique
public class LocationService implements ILocationService {
	private final ILocationRepository locationRepo;
	
	public LocationService(ILocationRepository locationRepo) {
		this.locationRepo = locationRepo;
	}
	
	@Override
	public Location get(long locationId) {
		var location = locationRepo.get(locationId);
		if (location.isEmpty()) {
			throw ServiceException.notFound("Location not found");
		}
		return location.get();
	}
	
	@Override
	public List<Location> search(String searchText) {
		return locationRepo.search(Sputnik.trimToNull(searchText));
	}
	
	@Override
	public Location create(String name, String province, String address) {
		Location location = new Location(
			Snowflake.get().nextId(),
			Sputnik.requireText(name, "name", 100),
			Sputnik.requireText(province, "province", 100),
			Sputnik.requireText(address, "address", 255)
		);
		requireUnique(location);
		return locationRepo.create(location);
	}
	
	@Override
	public Location update(long locationId, String name, String province, String address) {
		get(locationId);
		Location location = new Location(
			locationId,
			Sputnik.requireText(name, "name", 100),
			Sputnik.requireText(province, "province", 100),
			Sputnik.requireText(address, "address", 255)
		);
		requireUnique(location);
		return locationRepo.update(location);
	}
	
	private void requireUnique(Location location) {
		if (locationRepo.existsByIdentity(location.name(), location.province(), location.address(), location.id())) {
			throw ServiceException.conflict("A location with the same name, province and address already exists");
		}
	}
}
