package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.model.NetworkConnectionNode

/**
 * Compatibility wrapper delegating to [NetworkGlobe].
 */
@Composable
fun SceneViewNetworkGlobe(
  networkConnected: Boolean,
  modifier: Modifier = Modifier,
  connections: List<NetworkConnectionNode> = emptyList(),
  packetTrafficActive: Boolean = false,
  packetIntensity: Float = 0f,
  rxSpeedKbps: Float = 0f,
  txSpeedKbps: Float = 0f,
  selectedNodeId: String? = null,
  onGlobeTap: () -> Unit = {},
  onGlobeReset: () -> Unit = {},
) {
  NetworkGlobe(
    networkConnected = networkConnected,
    modifier = modifier,
    connections = connections,
    packetTrafficActive = packetTrafficActive,
    packetIntensity = packetIntensity,
    rxSpeedKbps = rxSpeedKbps,
    txSpeedKbps = txSpeedKbps,
    selectedNodeId = selectedNodeId,
    onGlobeTap = onGlobeTap,
    onGlobeReset = onGlobeReset,
  )
}
