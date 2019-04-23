package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.PoCContract
import com.template.states.IOUState
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class IOUFlow(val aptNumber: String,
              val addressLine1: String,
              val addressLine2: String,
              val city: String,
              val state: String,
              val zipCode: String,
              val BBL: String,
              val Price: Int,
              val SellingDate: String,
              val reLedger: Party,
              val sellAttester: Party,
              val buyAttester: Party) : FlowLogic<SignedTransaction>() {

    /**
     * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
     * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
     */
    companion object {
        object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new IOU.")
        object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
        object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()


    @Suspendable
    override fun call(): SignedTransaction {
        // We retrieve the notary identity from the network map.
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        progressTracker.currentStep = GENERATING_TRANSACTION

        // We create the transaction components.
        val outputState = IOUState(aptNumber, addressLine1, addressLine2,
                city, state, zipCode, BBL, Price, SellingDate, reLedger,
                sellAttester, buyAttester, ourIdentity)
        val command = Command(PoCContract.Commands.Action(), outputState.participants.map { it.owningKey })

        // We create a transaction builder and add the components.
        val txBuilder = TransactionBuilder(notary = notary)
                .addOutputState(outputState, PoCContract.ID)
                .addCommand(command)

        // We sign the transaction.
        progressTracker.currentStep = VERIFYING_TRANSACTION
        txBuilder.verify(serviceHub)

        progressTracker.currentStep = SIGNING_TRANSACTION
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        progressTracker.currentStep = GATHERING_SIGS

        // Creating a session with the other party.
        val buySession = initiateFlow(buyAttester)
        val sellSession = initiateFlow(sellAttester)
        val reLedgerSession = initiateFlow(reLedger)


        // We finalise the transaction and then send it to the counterparty.
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, setOf(buySession,sellSession, reLedgerSession)))
        progressTracker.currentStep = FINALISING_TRANSACTION
        return subFlow(FinalityFlow(fullySignedTx, setOf(buySession,sellSession, reLedgerSession)))

    }
}

@InitiatedBy(IOUFlow::class)
class Responder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable override fun call(): SignedTransaction  {
        // [SignTransactionFlow] sub-classed as a singleton object.
        val flow = object : SignTransactionFlow(counterpartySession) {
            @Suspendable override fun checkTransaction(stx: SignedTransaction) = requireThat {
                "No inputs should be consumed in transaction" using (stx.tx.inputs.isEmpty())
            }
        }
        val expectedTxId = subFlow(flow).id

        return subFlow(ReceiveFinalityFlow(counterpartySession, expectedTxId))
    }


}
