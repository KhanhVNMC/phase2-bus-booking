package dev.janitor.busbooking.service;

import java.util.List;

import dev.janitor.busbooking.model.Location;

public interface ILocationService {
	Location get(long locationId);
	
	// blank searchText = all
	List<Location> search(String searchText);
	
	Location create(String name, String province, String address);
	
	Location update(long locationId, String name, String province, String address);
}
