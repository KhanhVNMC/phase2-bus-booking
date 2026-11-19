package dev.janitor.busbooking.service;

import dev.janitor.busbooking.consts.ServiceFailure;

// the only exception services throw on purpose, the message goes to the client as is
public class ServiceException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	public final ServiceFailure failure;
	
	public ServiceException(ServiceFailure failure, String message) {
		super(message);
		this.failure = failure;
	}
	
	public static ServiceException invalid(String message) {
		return new ServiceException(ServiceFailure.INVALID_INPUT, message);
	}
	
	public static ServiceException notFound(String message) {
		return new ServiceException(ServiceFailure.NOT_FOUND, message);
	}
	
	public static ServiceException conflict(String message) {
		return new ServiceException(ServiceFailure.CONFLICT, message);
	}
}
