package com.matcodem.fincore.account.adapter.in.web;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.matcodem.fincore.account.adapter.in.web.dto.AccountResponse;
import com.matcodem.fincore.account.adapter.in.web.dto.AuditLogResponse;
import com.matcodem.fincore.account.adapter.in.web.dto.OpenAccountRequest;
import com.matcodem.fincore.account.adapter.in.web.mapper.AccountWebMapper;
import com.matcodem.fincore.account.domain.model.AccountId;
import com.matcodem.fincore.account.domain.port.in.FreezeAccountUseCase;
import com.matcodem.fincore.account.domain.port.in.GetAccountUseCase;
import com.matcodem.fincore.account.domain.port.in.OpenAccountUseCase;
import com.matcodem.fincore.account.domain.port.out.AuditLogRepository;

import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

	private final OpenAccountUseCase openAccountUseCase;
	private final GetAccountUseCase getAccountUseCase;
	private final FreezeAccountUseCase freezeAccountUseCase;
	private final AuditLogRepository auditLogRepository;
	private final AccountWebMapper mapper;

	@PostMapping
	@Timed(value = "account.open", description = "Time to open an account")
	@PreAuthorize("hasRole('ROLE_USER') or hasRole('ROLE_ADMIN')")
	public ResponseEntity<AccountResponse> openAccount(
			@Valid @RequestBody OpenAccountRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		String ownerId = jwt.getSubject();
		var command = new OpenAccountUseCase.OpenAccountCommand(ownerId, request.currency());
		var account = openAccountUseCase.openAccount(command);
		var response = mapper.toResponse(account);

		return ResponseEntity
				.created(URI.create("/api/v1/accounts/" + account.getId()))
				.body(response);
	}

	@GetMapping("/{accountId}")
	@PreAuthorize("hasRole('ROLE_USER') or hasRole('ROLE_ADMIN')")
	public ResponseEntity<AccountResponse> getAccount(
			@PathVariable String accountId,
			@AuthenticationPrincipal Jwt jwt) {

		var account = getAccountUseCase.getAccount(AccountId.of(accountId));

		// Users can only see their own accounts
		if (!account.getOwnerId().equals(jwt.getSubject()) && !isAdmin(jwt)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		return ResponseEntity.ok(mapper.toResponse(account));
	}

	@GetMapping("/my")
	@PreAuthorize("hasRole('ROLE_USER')")
	public ResponseEntity<List<AccountResponse>> getMyAccounts(
			@AuthenticationPrincipal Jwt jwt) {

		String ownerId = jwt.getSubject();
		var accounts = getAccountUseCase.getAccountsByOwner(ownerId);
		return ResponseEntity.ok(accounts.stream().map(mapper::toResponse).toList());
	}

	@PostMapping("/{accountId}/freeze")
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	public ResponseEntity<Void> freezeAccount(
			@PathVariable String accountId,
			@RequestParam String reason) {

		freezeAccountUseCase.freeze(
				new FreezeAccountUseCase.FreezeCommand(AccountId.of(accountId), reason)
		);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{accountId}/unfreeze")
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	public ResponseEntity<Void> unfreezeAccount(@PathVariable String accountId) {
		freezeAccountUseCase.unfreeze(AccountId.of(accountId));
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{accountId}/audit")
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	public ResponseEntity<List<AuditLogResponse>> getAuditLog(
			@PathVariable String accountId) {

		var entries = auditLogRepository.findByAccountId(AccountId.of(accountId));
		var responses = entries.stream()
				.map(e -> new AuditLogResponse(
						e.accountId().toString(),
						e.eventType(),
						e.performedBy(),
						e.details(),
						e.occurredAt()
				))
				.toList();
		return ResponseEntity.ok(responses);
	}

	private boolean isAdmin(Jwt jwt) {
		var roles = jwt.getClaimAsStringList("roles");
		return roles != null && roles.contains("ROLE_ADMIN");
	}
}