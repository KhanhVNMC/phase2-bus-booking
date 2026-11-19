package dev.janitor.busbooking.service;

import java.util.List;

import dev.janitor.busbooking.consts.ActiveStatus;
import dev.janitor.busbooking.model.Operator;

public interface IOperatorService {
	Operator get(long operatorId);
	
	// null filters are ignored
	List<Operator> search(String searchText, ActiveStatus status);
	
	// starts ACTIVE
	Operator create(String name, String address, String phone, String email);
	
	// going INACTIVE is refused while it still owns ACTIVE / MAINTENANCE vehicles
	Operator update(long operatorId, String name, String address, String phone, String email, ActiveStatus status);
}
