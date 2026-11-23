package dev.janitor.busbooking.api;

import dev.janitor.busbooking.service.IAuthService;
import dev.janitor.busbooking.service.IAuthService.Principal;
import io.javalin.http.Context;
import io.javalin.http.Handler;

public final class AuthenticationMiddleware {
    private static final String BEARER = "Bearer ";

    private final IAuthService authService;

    public AuthenticationMiddleware(IAuthService authService) {
        this.authService = authService;
    }

    public Handler handler() {
        return this::authenticate;
    }

    private void authenticate(Context ctx) {
        String header = ctx.header("Authorization");
        if (header == null || header.isBlank()) {
            return;
        }
        String token = header;
        if (header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            token = header.substring(BEARER.length());
        }
        Principal principal = authService.authenticate(token);
        ctx.attribute(APIUtils.PRINCIPAL, principal);
    }
}
