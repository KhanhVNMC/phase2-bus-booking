package dev.janitor.busbooking.api;

import dev.janitor.busbooking.api.dto.LoginRequest;
import dev.janitor.busbooking.api.dto.LoginResponse;
import dev.janitor.busbooking.api.dto.RegisterRequest;
import dev.janitor.busbooking.api.dto.UpdateProfileRequest;
import dev.janitor.busbooking.api.dto.ChangeRoleRequest;
import dev.janitor.busbooking.service.IAuthService;
import dev.janitor.busbooking.service.IAuthService.LoginResult;
import dev.janitor.busbooking.service.IAuthService.Principal;
import dev.janitor.busbooking.service.IUserService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

// cho /api/auth/* and /api/users/me, voi pha 1 paths va status codes
public class AuthController {
    private final IAuthService authService;
    private final IUserService userService;

    public AuthController(IAuthService authService, IUserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    public void registerRoutes(JavalinDefaultRoutingApi router) {
        router.post("/api/auth/register", this::register);
        router.post("/api/auth/login", this::login);
        router.get("/api/auth/me", this::me);
        router.get("/api/users/me", this::me);
        router.put("/api/users/me", this::updateMe);
        router.put("/api/admin/users/{userId}/role", this::changeRole);
    }

    public void register(Context ctx) {
        RegisterRequest request = APIUtils.body(ctx, RegisterRequest.class);
        var user = authService.register(request.fullName(), request.email(), request.password(), request.phone());
        ctx.status(HttpStatus.CREATED);
        ctx.header("Cache-Control", "no-store");
        ctx.json(ResponseMapper.user(user));
    }

    public void login(Context ctx) {
        LoginRequest request = APIUtils.body(ctx, LoginRequest.class);
        LoginResult result = authService.login(request.email(), request.password());
        ctx.header("Cache-Control", "no-store");
        ctx.json(new LoginResponse(result.token(), "Bearer", result.expiresInSeconds(), ResponseMapper.user(result.user())));
    }

    public void me(Context ctx) {
        Principal principal = APIUtils.principal(ctx);
        ctx.json(ResponseMapper.user(userService.getProfile(principal.userId())));
    }

    public void updateMe(Context ctx) {
        Principal principal = APIUtils.principal(ctx);
        UpdateProfileRequest request = APIUtils.body(ctx, UpdateProfileRequest.class);
        ctx.json(ResponseMapper.user(userService.updateProfile(principal.userId(), request.fullName(), request.phone())));
    }

    /* cai token chi de dua qua buoc dau, buoc sau van check db binh thuong */
    public void changeRole(Context ctx) {
        Principal principal = APIUtils.admin(ctx);
        long targetUserId = APIUtils.pathId(ctx, "userId");
        ChangeRoleRequest request = APIUtils.body(ctx, ChangeRoleRequest.class);
        ctx.json(ResponseMapper.user(userService.changeRole(principal.userId(), targetUserId, request.role())));
    }
}
