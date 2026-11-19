package dev.janitor.busbooking.repository;

import java.util.List;
import java.util.Optional;

import dev.janitor.busbooking.model.Location;

public interface ILocationRepository {
	Optional<Location> get(long locationId);
	
	// searchText matches name/province/address, accent and case insensitive; null = all
	List<Location> search(String searchText);
	
	Location create(Location location);
	
	Location update(Location location);
	
	// same name + province + address (case insensitive) on another location. excludedLocationId 0 = none
	boolean existsByIdentity(String name, String province, String address, long excludedLocationId);
}
