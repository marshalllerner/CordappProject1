package com.template.states

import com.sun.corba.se.pept.broker.Broker
import com.template.contracts.PoCContract
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.Party
import java.util.*

// *********
// * State *
// *********
@BelongsToContract(PoCContract::class)
class IOUState(val aptNumber: String,
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
               val buyAttester: Party,
               val broker: Party) : ContractState {
    override val participants get() = listOf(reLedger, sellAttester, buyAttester, broker)
    fun checkAddressFields() : Boolean  {
        return aptNumber.length > 0 || addressLine1.length > 0 || addressLine2.length > 0 ||
                city.length > 0 || state.length > 0 || zipCode.length > 0 ||
                BBL.length > 0
    }
}