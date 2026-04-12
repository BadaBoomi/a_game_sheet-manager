package de.badaboomi.gamesheetmanager.bluetooth

import java.util.UUID

/**
 * Constants used for Bluetooth template transfer.
 */
object BluetoothConstants {
    /**
     * A unique UUID for the Spielbögen-Manager Bluetooth service.
     * Both sender and receiver must use the same UUID.
     */
    val SERVICE_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")

    const val SERVICE_NAME = "GameSheetManager"

    /** Magic header to identify valid protocol packets. */
    const val PROTOCOL_HEADER = "GSM_TEMPLATE_V1"

    /** Buffer size for reading/writing data. */
    const val BUFFER_SIZE = 8192
}
