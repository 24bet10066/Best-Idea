package com.partlinq.core.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * ShopAccessGuard — enforces "a request can only touch the shop it claims to act for".
 *
 * The single biggest risk in a multi-tenant credit system is one shop accidentally
 * (or maliciously) reading another shop's customers and balances. Khatabook had this
 * exact bug class in 2021. This guard exists to stop it.
 *
 * Today (no auth):
 *   The client is expected to send X-Shop-Id with the shop UUID it's acting on behalf of.
 *   The guard verifies any shopId in the URL matches the header.
 *
 * Tomorrow (JWT):
 *   Replace the header read with a JWT claim. Same call sites stay intact.
 *
 * Modes (controlled by env var TENANT_ENFORCEMENT):
 *   - DISABLED   — guard is a no-op. Use only for one-off admin scripts.
 *   - WARN_ONLY  — log violations but don't block. Default during pilot.
 *   - STRICT     — return 403 on any mismatch. Set this before public launch.
 *
 * Usage in a controller:
 *   <pre>
 *     shopAccessGuard.assertCanAccess(shopId);
 *   </pre>
 */
@Component
@Slf4j
public class ShopAccessGuard {

	public enum Mode { DISABLED, WARN_ONLY, STRICT }

	private final Mode mode;

	public ShopAccessGuard(@Value("${app.tenant.enforcement:WARN_ONLY}") String modeStr) {
		Mode parsed;
		try {
			parsed = Mode.valueOf(modeStr.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			log.warn("Unknown TENANT_ENFORCEMENT value '{}', defaulting to WARN_ONLY", modeStr);
			parsed = Mode.WARN_ONLY;
		}
		this.mode = parsed;
		log.info("ShopAccessGuard initialized in {} mode", this.mode);
	}

	/**
	 * Verify the current request is authorized to act on the given shopId.
	 *
	 * @throws ResponseStatusException(403) in STRICT mode if the claimed shop
	 *         (X-Shop-Id header) doesn't match the requested shopId.
	 */
	public void assertCanAccess(UUID shopId) {
		if (mode == Mode.DISABLED || shopId == null) return;

		String claimedShopId = readClaimedShopId();

		if (claimedShopId == null || claimedShopId.isBlank()) {
			handle("missing X-Shop-Id header for request scoped to shop=" + shopId);
			return;
		}

		UUID claimed;
		try {
			claimed = UUID.fromString(claimedShopId);
		} catch (IllegalArgumentException e) {
			handle("malformed X-Shop-Id header value '" + claimedShopId + "'");
			return;
		}

		if (!claimed.equals(shopId)) {
			handle("cross-shop access attempt: claimed=" + claimed + ", requested=" + shopId);
		}
	}

	private String readClaimedShopId() {
		ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		if (attrs == null) return null;
		HttpServletRequest req = attrs.getRequest();
		return req.getHeader("X-Shop-Id");
	}

	private void handle(String reason) {
		switch (mode) {
			case STRICT:
				log.warn("Tenant enforcement BLOCKED: {}", reason);
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant access denied: " + reason);
			case WARN_ONLY:
				log.warn("Tenant enforcement WARNING (would block in STRICT): {}", reason);
				break;
			case DISABLED:
				// no-op
				break;
		}
	}

	public Mode getMode() { return mode; }
}
