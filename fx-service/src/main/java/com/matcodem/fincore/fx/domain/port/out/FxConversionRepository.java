package com.matcodem.fincore.fx.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.matcodem.fincore.fx.domain.model.FxConversion;
import com.matcodem.fincore.fx.domain.model.FxConversionId;

public interface FxConversionRepository {
	FxConversion save(FxConversion conversion);

	Optional<FxConversion> findById(FxConversionId id);

	List<FxConversion> findByAccountId(String accountId);

	Optional<FxConversion> findByPaymentId(String paymentId);
}
