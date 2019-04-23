package com.template.contracts

import com.template.states.IOUState
import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.transactions.LedgerTransaction

// ************
// * Contract *
// ************
class PoCContract : Contract {
    companion object {

        const val ID = "com.template.contracts.PoCContract"
    }

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands.Action>()
        requireThat {
            // Generic constraints around the IOU transaction.
            "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
            "Only one output state should be created." using (tx.outputs.size ==1)
            val out = tx.outputsOfType<IOUState>().single()
            "The broker and the buy/sell attester cannot be the same entity" using (out.broker != out.sellAttester
                    && out.broker != out.buyAttester)
            "Property address must not be empty and contain all fields" using (out.checkAddressFields())
            "Property closing price must not be empty." using (out.Price > 0)
            "Property closing price date must noe be empty." using (out.SellingDate != "")
            "The PoC broker recorder or sell/buy side attester must sign." using
                    (command.signers.contains(out.broker.owningKey)
                            || command.signers.contains(out.sellAttester.owningKey)
                            || command.signers.contains(out.buyAttester.owningKey))
            "Streetwire must sign." using  (command.signers.contains(out.reLedger.owningKey))



        }
        // Verification logic goes here.
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class Action : Commands
    }
}