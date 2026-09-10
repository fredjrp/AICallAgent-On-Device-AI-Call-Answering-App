package com.aicall.agent.telecom

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import com.aicall.agent.util.Logger

/**
 * Telecom ConnectionService counterpart.
 * Allows AICallAgent to manage telephony connections when operating as the default phone account.
 */
class CallConnectionService : ConnectionService() {

    private val tag = "CallConnectionService"

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Logger.i(tag, "onCreateIncomingConnection: ${request?.address}")
        return super.onCreateIncomingConnection(connectionManagerPhoneAccount, request)
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Logger.i(tag, "onCreateOutgoingConnection: ${request?.address}")
        return super.onCreateOutgoingConnection(connectionManagerPhoneAccount, request)
    }
}
