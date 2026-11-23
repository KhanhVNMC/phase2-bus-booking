package dev.janitor.busbooking.api;

import dev.janitor.busbooking.service.IAuthService.Principal;
import dev.janitor.busbooking.service.ServiceException;
import dev.janitor.busbooking.consts.ServiceFailure;
import io.javalin.http.Context;

public class APIUtils {
    public static final String PRINCIPAL = "principal";

    public static long parseId(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw ServiceException.invalid(fieldName + " is required");
        }
        long parsed;
        try {
            parsed = Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw ServiceException.invalid(fieldName + " is not a valid id");
        }
        if (parsed <= 0) {
            throw ServiceException.invalid(fieldName + " is not a valid id");
        }
        return parsed;
    }

    public static long pathId(Context ctx, String name) {
        return parseId(ctx.pathParam(name), name);
    }

    // Set by the middleware for every request that carried a valid token
    public static Principal principal(Context ctx) {
        Principal principal = ctx.attribute(PRINCIPAL);
        if (principal == null) {
            throw new ServiceException(ServiceFailure.UNAUTHENTICATED, "Authentication required");
        }
        return principal;
    }

    public static Principal admin(Context ctx) {
        Principal principal = principal(ctx);
        if (!principal.isAdmin()) {
            throw new ServiceException(ServiceFailure.FORBIDDEN, "Admins only");
        }
        return principal;
    }

    // A body that is not json, or has a field of the wrong type, is an client error
    public static <T> T body(Context ctx, Class<T> bodyClass) {
        T parsed;
        try {
            parsed = ctx.bodyAsClass(bodyClass);
        } catch (Exception e) {
            throw ServiceException.invalid("Malformed request body");
        }
        if (parsed == null) {
            throw ServiceException.invalid("Request body is required");
        }
        return parsed;
    }
}
