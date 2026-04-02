package br.com.nomar.controlai.domain.payment_methods

import br.com.nomar.controlai.domain.payment_methods.entity.*
import br.com.nomar.controlai.domain.payment_methods.gateway.*
import br.com.nomar.controlai.domain.payment_methods.usecase.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaymentMethodsUseCasesTest {

    // --- Holder Use Cases ---

    @Test
    fun `SaveHolderUseCase should return saved holder on success`() {
        val holder = sampleHolder()
        val gateway = SaveHolderGateway { Result.success(Holder(id = 1, name = it.name)) }
        val useCase = SaveHolderUseCase(gateway)

        val result = useCase.execute(holder)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.id)
        assertEquals("Ramon", result.getOrNull()?.name)
    }

    @Test
    fun `SaveHolderUseCase should return failure when gateway fails`() {
        val gateway = SaveHolderGateway { Result.failure(IllegalStateException("duplicate name")) }
        val useCase = SaveHolderUseCase(gateway)

        val result = useCase.execute(sampleHolder())

        assertTrue(result.isFailure)
        assertEquals("duplicate name", result.exceptionOrNull()?.message)
    }

    @Test
    fun `ListHoldersUseCase should return list of holders`() {
        val holders = listOf(Holder(id = 1, name = "Ramon"), Holder(id = 2, name = "Aline"))
        val gateway = ListHoldersGateway { Result.success(holders) }
        val useCase = ListHoldersUseCase(gateway)

        val result = useCase.execute()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    @Test
    fun `ListHoldersUseCase should return failure when gateway fails`() {
        val gateway = ListHoldersGateway { Result.failure(RuntimeException("db error")) }
        val useCase = ListHoldersUseCase(gateway)

        val result = useCase.execute()

        assertTrue(result.isFailure)
    }

    // --- PaymentMethod Use Cases ---

    @Test
    fun `SavePaymentMethodUseCase should return saved payment method`() {
        val pm = samplePaymentMethod()
        val gateway = SavePaymentMethodGateway { Result.success(PaymentMethod(id = 1, name = it.name, type = it.type, holderId = it.holderId)) }
        val useCase = SavePaymentMethodUseCase(gateway)

        val result = useCase.execute(pm)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.id)
        assertEquals("Smiles Infinite", result.getOrNull()?.name)
    }

    @Test
    fun `SavePaymentMethodUseCase should return failure when gateway fails`() {
        val gateway = SavePaymentMethodGateway { Result.failure(IllegalStateException("error")) }
        val useCase = SavePaymentMethodUseCase(gateway)

        val result = useCase.execute(samplePaymentMethod())

        assertTrue(result.isFailure)
    }

    @Test
    fun `ListPaymentMethodsUseCase should return list filtered by holderId`() {
        val pms = listOf(samplePaymentMethod())
        val gateway = ListPaymentMethodsGateway { holderId ->
            if (holderId == 1L) Result.success(pms) else Result.success(emptyList())
        }
        val useCase = ListPaymentMethodsUseCase(gateway)

        val result = useCase.execute(holderId = 1L)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)

        val resultEmpty = useCase.execute(holderId = 99L)
        assertTrue(resultEmpty.isSuccess)
        assertEquals(0, resultEmpty.getOrNull()?.size)
    }

    @Test
    fun `FindPaymentMethodUseCase should return payment method by id`() {
        val pm = PaymentMethod(id = 1, name = "Smiles", type = PaymentMethodType.CREDIT_CARD, holderId = 1)
        val gateway = FindPaymentMethodGateway { id ->
            if (id == 1L) Result.success(pm) else Result.failure(NoSuchElementException("not found"))
        }
        val useCase = FindPaymentMethodUseCase(gateway)

        val result = useCase.execute(1L)
        assertTrue(result.isSuccess)
        assertEquals("Smiles", result.getOrNull()?.name)

        val resultNotFound = useCase.execute(99L)
        assertTrue(resultNotFound.isFailure)
    }

    @Test
    fun `UpdatePaymentMethodUseCase should return updated payment method`() {
        val pm = PaymentMethod(id = 1, name = "Updated Name", type = PaymentMethodType.CREDIT_CARD, holderId = 1)
        val gateway = UpdatePaymentMethodGateway { Result.success(it) }
        val useCase = UpdatePaymentMethodUseCase(gateway)

        val result = useCase.execute(pm)

        assertTrue(result.isSuccess)
        assertEquals("Updated Name", result.getOrNull()?.name)
    }

    @Test
    fun `DeactivatePaymentMethodUseCase should return success`() {
        val gateway = DeactivatePaymentMethodGateway { Result.success(Unit) }
        val useCase = DeactivatePaymentMethodUseCase(gateway)

        val result = useCase.execute(1L)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `DeactivatePaymentMethodUseCase should return failure when not found`() {
        val gateway = DeactivatePaymentMethodGateway { Result.failure(NoSuchElementException("not found")) }
        val useCase = DeactivatePaymentMethodUseCase(gateway)

        val result = useCase.execute(99L)

        assertTrue(result.isFailure)
        assertEquals("not found", result.exceptionOrNull()?.message)
    }

    // --- SubCard Use Cases ---

    @Test
    fun `SaveSubCardUseCase should return saved sub card`() {
        val sc = sampleSubCard()
        val gateway = SaveSubCardGateway { Result.success(SubCard(id = 1, paymentMethodId = it.paymentMethodId, lastFourDigits = it.lastFourDigits, type = it.type)) }
        val useCase = SaveSubCardUseCase(gateway)

        val result = useCase.execute(sc)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.id)
        assertEquals("6668", result.getOrNull()?.lastFourDigits)
    }

    @Test
    fun `SaveSubCardUseCase should return failure when gateway fails`() {
        val gateway = SaveSubCardGateway { Result.failure(IllegalStateException("error")) }
        val useCase = SaveSubCardUseCase(gateway)

        val result = useCase.execute(sampleSubCard())

        assertTrue(result.isFailure)
    }

    @Test
    fun `UpdateSubCardUseCase should return updated sub card`() {
        val sc = SubCard(id = 1, paymentMethodId = 1, lastFourDigits = "9999", type = SubCardType.VIRTUAL)
        val gateway = UpdateSubCardGateway { Result.success(it) }
        val useCase = UpdateSubCardUseCase(gateway)

        val result = useCase.execute(sc)

        assertTrue(result.isSuccess)
        assertEquals("9999", result.getOrNull()?.lastFourDigits)
    }

    @Test
    fun `DeactivateSubCardUseCase should return success`() {
        val gateway = DeactivateSubCardGateway { Result.success(Unit) }
        val useCase = DeactivateSubCardUseCase(gateway)

        val result = useCase.execute(1L)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `DeactivateSubCardUseCase should return failure when not found`() {
        val gateway = DeactivateSubCardGateway { Result.failure(NoSuchElementException("not found")) }
        val useCase = DeactivateSubCardUseCase(gateway)

        val result = useCase.execute(99L)

        assertTrue(result.isFailure)
    }

    // --- Enum Tests ---

    @Test
    fun `PaymentMethodType should have all expected values`() {
        val values = PaymentMethodType.entries
        assertEquals(3, values.size)
        assertTrue(values.contains(PaymentMethodType.CREDIT_CARD))
        assertTrue(values.contains(PaymentMethodType.PIX))
        assertTrue(values.contains(PaymentMethodType.CASH))
    }

    @Test
    fun `SubCardType should have all expected values`() {
        val values = SubCardType.entries
        assertEquals(5, values.size)
        assertTrue(values.contains(SubCardType.PHYSICAL_HOLDER))
        assertTrue(values.contains(SubCardType.PHYSICAL_DEPENDENT))
        assertTrue(values.contains(SubCardType.VIRTUAL))
        assertTrue(values.contains(SubCardType.VIRTUAL_TEMPORARY))
        assertTrue(values.contains(SubCardType.DIGITAL_WALLET))
    }

    @Test
    fun `WalletPlatform should have all expected values`() {
        val values = WalletPlatform.entries
        assertEquals(4, values.size)
        assertTrue(values.contains(WalletPlatform.APPLE_PAY))
        assertTrue(values.contains(WalletPlatform.GOOGLE_PAY))
        assertTrue(values.contains(WalletPlatform.SAMSUNG_PAY))
        assertTrue(values.contains(WalletPlatform.OTHER))
    }

    // --- Entity Construction Tests ---

    @Test
    fun `Holder should be constructable with minimal fields`() {
        val holder = Holder(name = "Ramon")
        assertEquals("Ramon", holder.name)
        assertEquals(null, holder.id)
    }

    @Test
    fun `PaymentMethod should be constructable with all fields`() {
        val pm = PaymentMethod(
            id = 1,
            name = "Smiles Infinite",
            type = PaymentMethodType.CREDIT_CARD,
            holderId = 1,
            brand = "Visa",
            closingDay = 15,
            subCards = listOf(sampleSubCard()),
        )
        assertEquals("Smiles Infinite", pm.name)
        assertEquals(PaymentMethodType.CREDIT_CARD, pm.type)
        assertEquals("Visa", pm.brand)
        assertEquals(15, pm.closingDay)
        assertEquals(1, pm.subCards.size)
    }

    @Test
    fun `SubCard should support digital wallet with platform`() {
        val sc = SubCard(
            paymentMethodId = 1,
            lastFourDigits = "1234",
            type = SubCardType.DIGITAL_WALLET,
            walletPlatform = WalletPlatform.APPLE_PAY,
        )
        assertEquals(SubCardType.DIGITAL_WALLET, sc.type)
        assertEquals(WalletPlatform.APPLE_PAY, sc.walletPlatform)
    }

    @Test
    fun `SubCard should support dependent with name`() {
        val sc = SubCard(
            paymentMethodId = 1,
            lastFourDigits = "9687",
            type = SubCardType.PHYSICAL_DEPENDENT,
            dependentName = "Aline",
        )
        assertEquals(SubCardType.PHYSICAL_DEPENDENT, sc.type)
        assertEquals("Aline", sc.dependentName)
    }

    // --- Helpers ---

    private fun sampleHolder() = Holder(name = "Ramon")

    private fun samplePaymentMethod() = PaymentMethod(
        name = "Smiles Infinite",
        type = PaymentMethodType.CREDIT_CARD,
        holderId = 1,
        brand = "Visa",
        closingDay = 15,
    )

    private fun sampleSubCard() = SubCard(
        paymentMethodId = 1,
        lastFourDigits = "6668",
        type = SubCardType.PHYSICAL_HOLDER,
    )
}
