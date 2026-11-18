package dev.janitor.busbooking.repository;

import java.util.List;
import java.util.Optional;

import dev.janitor.busbooking.consts.ActiveStatus;
import dev.janitor.busbooking.model.Operator;

public interface IOperatorRepository {
	Optional<Operator> get(long operatorId);
	
	// null filters are ignored
	List<Operator> search(String searchText, ActiveStatus status);
	
	Operator create(Operator operator);
	
	Operator update(Operator operator);
	
	// any ACTIVE or MAINTENANCE vehicle (still ours, even if off the road): blocks going INACTIVE
	boolean ownsInServiceVehicles(long operatorId);
}
