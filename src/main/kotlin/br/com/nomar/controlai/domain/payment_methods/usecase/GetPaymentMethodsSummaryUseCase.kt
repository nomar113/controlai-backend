package br.com.nomar.controlai.domain.payment_methods.usecase

import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethodSummary
import br.com.nomar.controlai.domain.payment_methods.gateway.GetPaymentMethodsSummaryGateway
import org.springframework.stereotype.Component
import java.time.YearMonth

@Component
class GetPaymentMethodsSummaryUseCase(
    private val getPaymentMethodsSummaryGateway: GetPaymentMethodsSummaryGateway,
) {
    fun execute(month: YearMonth): Result<List<PaymentMethodSummary>> {
        return runCatching {
            getPaymentMethodsSummaryGateway.execute(month).getOrThrow()
        }
    }
}
