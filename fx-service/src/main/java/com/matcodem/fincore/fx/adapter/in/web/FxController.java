package com.matcodem.fincore.fx.adapter.in.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.matcodem.fincore.fx.adapter.in.web.dto.ConversionQuoteRequest;
import com.matcodem.fincore.fx.adapter.in.web.dto.ConversionQuoteResponse;
import com.matcodem.fincore.fx.adapter.in.web.dto.ConvertRequest;
import com.matcodem.fincore.fx.adapter.in.web.dto.ExchangeRateResponse;
import com.matcodem.fincore.fx.adapter.in.web.dto.FxConversionResponse;
import com.matcodem.fincore.fx.application.usecase.FxApplicationService;
import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.model.ExchangeRate;
import com.matcodem.fincore.fx.domain.model.FxConversion;
import com.matcodem.fincore.fx.domain.port.in.ConvertCurrencyUseCase;
import com.matcodem.fincore.fx.domain.port.out.ExchangeRateRepository;

import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/fx")
@RequiredArgsConstructor
public class FxController {

	private final FxApplicationService fxService;
	private final ExchangeRateRepository rateRepository;

	/**
	 * Get current exchange rate for a pair
	 */
	@GetMapping("/rates/{pair}")
	@Timed(value = "api.fx.rate")
	@PreAuthorize("hasRole('ROLE_USER')")
	public ResponseEntity<ExchangeRateResponse> getRate(@PathVariable String pair) {
		ExchangeRate rate = fxService.getRateWithFallback(CurrencyPair.fromSymbol(pair));
		return ResponseEntity.ok(toRateResponse(rate));
	}

	/**
	 * Get all currently active rates
	 */
	@GetMapping("/rates")
	@PreAuthorize("hasRole('ROLE_USER')")
	public ResponseEntity<List<ExchangeRateResponse>> getAllRates() {
		return ResponseEntity.ok(
				rateRepository.findAllActive().stream()
						.map(this::toRateResponse).toList()
		);
	}

	/**
	 * Quote a conversion — no side effects, no persistence
	 */
	@PostMapping("/quote")
	@Timed(value = "api.fx.quote")
	@PreAuthorize("hasRole('ROLE_USER')")
	public ResponseEntity<ConversionQuoteResponse> quote(
			@Valid @RequestBody ConversionQuoteRequest request) {

		var result = fxService.quote(
				CurrencyPair.fromSymbol(request.pair()),
				request.amount(),
				ExchangeRate.ConversionDirection.valueOf(request.direction())
		);
		return ResponseEntity.ok(toQuoteResponse(result));
	}

	/**
	 * Execute a currency conversion
	 */
	@PostMapping("/convert")
	@Timed(value = "api.fx.convert")
	@PreAuthorize("hasRole('ROLE_USER')")
	public ResponseEntity<FxConversionResponse> convert(
			@Valid @RequestBody ConvertRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		FxConversion conversion = fxService.convert(new ConvertCurrencyUseCase.ConvertCommand(
				request.paymentId(),
				request.accountId(),
				jwt.getSubject(),
				CurrencyPair.fromSymbol(request.pair()),
				request.sourceAmount(),
				ExchangeRate.ConversionDirection.valueOf(request.direction())
		));

		return ResponseEntity.ok(toConversionResponse(conversion));
	}

	private ExchangeRateResponse toRateResponse(ExchangeRate r) {
		return new ExchangeRateResponse(
				r.getId().toString(), r.getPair().getSymbol(),
				r.getMidRate(), r.getBidRate(), r.getAskRate(),
				r.getSpreadBasisPoints(), r.getSource().name(),
				r.getFetchedAt(), r.getValidUntil(), r.isActive()
		);
	}

	private ConversionQuoteResponse toQuoteResponse(ExchangeRate.ConversionResult r) {
		return new ConversionQuoteResponse(
				r.pair().getSymbol(), r.sourceAmount(), r.convertedAmount(),
				r.appliedRate(), r.fee(), r.spreadBasisPoints(), r.rateTimestamp()
		);
	}

	private FxConversionResponse toConversionResponse(FxConversion c) {
		return new FxConversionResponse(
				c.getId().toString(), c.getPaymentId(), c.getPair().getSymbol(),
				c.getSourceAmount(), c.getConvertedAmount(), c.getAppliedRate(),
				c.getFee(), c.getSpreadBasisPoints(), c.getStatus().name(), c.getCreatedAt()
		);
	}
}