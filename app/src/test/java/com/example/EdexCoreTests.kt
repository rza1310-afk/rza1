package com.example

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.EdexPreferencesRepository
import com.example.data.FilesystemRepository
import com.example.data.HudSoundEffect
import com.example.data.HudSoundManager
import com.example.data.LowFreqPcmSynthesizer
import com.example.data.SystemTelemetryRepository
import com.example.data.TelemetryHub
import com.example.data.TerminalEngine
import com.example.model.EdexDefaults
import com.example.model.EdexSectionTab
import com.example.model.EdexUserPreferences
import com.example.model.GlobeNodeFilter
import com.example.model.MacroAccentRole
import com.example.model.NetworkConnectionNode
import com.example.model.ShellMacro
import com.example.model.SystemTelemetry
import com.example.model.TerminalLineType
import com.example.ui.components.HudProfilesSection
import com.example.ui.components.MacroConfirmDialog
import com.example.ui.components.MacroDeckBar
import com.example.ui.components.NetGlobePanel
import com.example.ui.components.Vec3
import com.example.ui.components.filterGlobeConnections
import com.example.ui.components.findNearestVisibleNode
import com.example.ui.theme.EdexPalettes
import com.example.ui.theme.EdexTheme
import com.example.ui.theme.EdexThemeId
import com.example.ui.theme.TelemetryThemeId
import com.example.ui.viewmodel.EdexViewModel
import com.example.ui.viewmodel.extractHostFromEndpoint
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EdexCoreTests {

  @get:Rule val composeRule = createComposeRule()

  @Test
  fun `filesystem repository enforces workspace boundary`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = FilesystemRepository(context)
    repo.ensureWorkspaceSeeded()

    val root = repo.workspaceRoot
    assertTrue(repo.isWithinWorkspace(root.absolutePath, allowRoot = true))
    assertFalse(repo.isWithinWorkspace(root.absolutePath, allowRoot = false))

    val traversalPath = File(root, "../outside_secret.txt").absolutePath
    assertFalse(repo.isWithinWorkspace(traversalPath, allowRoot = true))
    assertTrue(repo.deleteItem(root.absolutePath).isFailure)
    assertTrue(repo.writeFile(traversalPath, "nope").isFailure)

    val created = repo.createFile(root.absolutePath, "probe.txt", "hello hud")
    assertTrue(created.isSuccess)
    val file = created.getOrThrow()
    assertTrue(repo.isWithinWorkspace(file.absolutePath, allowRoot = false))
    assertEquals("hello hud", repo.readFileContent(file.absolutePath).getOrThrow())
    assertTrue(repo.deleteItem(file.absolutePath).isSuccess)
    assertFalse(file.exists())
  }

  @Test
  fun `terminal engine handles builtins and shell commands`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val fs = FilesystemRepository(context)
    fs.ensureWorkspaceSeeded()
    val engine = TerminalEngine(fs)
    val cwd = fs.workspaceRoot.absolutePath
    val telemetry = SystemTelemetry(deviceModel = "TEST HUD", cpuCores = 4, cpuLoadPercent = 35f)

    val themeRes = engine.execute("theme blade", cwd, telemetry)
    assertEquals(EdexThemeId.BLADE, themeRes.themeChange)

    val glitchRes = engine.execute("glitch 60", cwd, telemetry)
    assertEquals(60f, glitchRes.glitchThresholdChange)

    val echoShell = engine.execute("printf 'edex-shell-ok'", cwd, telemetry)
    assertTrue(echoShell.outputLines.any { it.first == "edex-shell-ok" && it.second == TerminalLineType.OUTPUT })
  }

  @Test
  fun `telemetry repository and hub are thread safe under concurrent sampling`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = SystemTelemetryRepository(context)
    val initialCount = TelemetryHub.sampleCount.value

    val jobs =
      (1..8).map { worker ->
        async(Dispatchers.Default) {
          repeat(5) { iter ->
            val snap = repo.collectSnapshot()
            TelemetryHub.publish(snap)
            TelemetryHub.log("TEST-$worker", "iter=$iter cpu=${snap.cpuLoadPercent.toInt()}")
          }
        }
      }
    jobs.awaitAll()

    assertTrue(TelemetryHub.sampleCount.value >= initialCount + 40L)
    assertTrue(TelemetryHub.systemLogs.value.isNotEmpty())
  }

  @Test
  fun `sound synthesizer generates subtle low-frequency smooth waveforms`() {
    HudSoundEffect.entries.forEach { effect ->
      val pcm = LowFreqPcmSynthesizer.synthesize(effect, variant = 2)
      assertTrue("PCM for $effect must be non-empty", pcm.isNotEmpty())

      val maxAbs = pcm.maxOf { abs(it.toInt()) }
      assertTrue("PCM for $effect should be audible (> 0)", maxAbs > 200)
      assertTrue(
        "PCM for $effect should stay subtle (<= 15% full scale), got $maxAbs",
        maxAbs <= (Short.MAX_VALUE * 0.15).toInt(),
      )

      // Smooth cosine edge taper prevents speaker pops
      assertTrue("Start sample should taper near zero", abs(pcm.first().toInt()) < 50)
      assertTrue("End sample should taper near zero", abs(pcm.last().toInt()) < 50)

      // Zero-crossing frequency check confirms low-frequency spectrum (< 350 Hz)
      var zeroCrossings = 0
      for (i in 1 until pcm.size) {
        if ((pcm[i - 1] < 0 && pcm[i] >= 0) || (pcm[i - 1] >= 0 && pcm[i] < 0)) {
          zeroCrossings++
        }
      }
      val durationSec = pcm.size.toDouble() / LowFreqPcmSynthesizer.SAMPLE_RATE
      val estFreqHz = zeroCrossings / (2.0 * durationSec)
      assertTrue(
        "Estimated frequency for $effect should stay low-frequency (< 350 Hz), was $estFreqHz Hz",
        estFreqHz < 350.0,
      )
    }
  }

  @Test
  fun `hud sound manager handles rapid triggers and lifecycle safely`() {
    val manager = HudSoundManager()
    manager.enabled = false
    manager.playTerminalType()
    manager.playDataProcess()

    manager.enabled = true
    repeat(10) {
      manager.playTerminalType()
      manager.playDataBurst()
    }
    manager.playUiSwitch()
    manager.playCommandExec()
    manager.release()
  }

  @Test
  fun `telemetry color themes resolve distinct palettes and hudtheme commands`() = runBlocking {
    val cyber = EdexPalettes.forTelemetryTheme(TelemetryThemeId.CYBERPUNK_NEON, EdexThemeId.TRON)
    val mono = EdexPalettes.forTelemetryTheme(TelemetryThemeId.MONOCHROME_TERMINAL, EdexThemeId.TRON)
    val military = EdexPalettes.forTelemetryTheme(TelemetryThemeId.MILITARY_HUD, EdexThemeId.TRON)
    val synced = EdexPalettes.forTelemetryTheme(TelemetryThemeId.SYNC_HUD, EdexThemeId.BLADE)

    assertEquals(EdexPalettes.CyberpunkNeon, cyber)
    assertEquals(EdexPalettes.MilitaryHud, military)
    assertEquals(EdexPalettes.Blade, synced)
    // Monochrome Terminal has equal RGB channels across primary/secondary/text
    assertEquals(mono.primaryBright.red, mono.primaryBright.green, 0.001f)
    assertEquals(mono.primaryBright.green, mono.primaryBright.blue, 0.001f)
    assertEquals(mono.secondary.red, mono.secondary.blue, 0.001f)

    val context = ApplicationProvider.getApplicationContext<Context>()
    val fs = FilesystemRepository(context)
    fs.ensureWorkspaceSeeded()
    val engine = TerminalEngine(fs)
    val telemetry = SystemTelemetry()

    assertEquals(
      TelemetryThemeId.CYBERPUNK_NEON,
      engine.execute("hudtheme cyberpunk", fs.workspaceRoot.absolutePath, telemetry).telemetryThemeChange,
    )
    assertEquals(
      TelemetryThemeId.MONOCHROME_TERMINAL,
      engine.execute("hudtheme monochrome", fs.workspaceRoot.absolutePath, telemetry).telemetryThemeChange,
    )
    assertEquals(
      TelemetryThemeId.MILITARY_HUD,
      engine.execute("hudtheme military", fs.workspaceRoot.absolutePath, telemetry).telemetryThemeChange,
    )
    assertEquals(
      TelemetryThemeId.CYBERPUNK_NEON,
      engine.execute("hudtheme cyberpunk-neon", fs.workspaceRoot.absolutePath, telemetry).telemetryThemeChange,
    )
    assertEquals(
      TelemetryThemeId.MONOCHROME_TERMINAL,
      engine.execute("hudtheme monochrome-terminal", fs.workspaceRoot.absolutePath, telemetry).telemetryThemeChange,
    )
    assertEquals(
      TelemetryThemeId.MILITARY_HUD,
      engine.execute("hudtheme military-hud", fs.workspaceRoot.absolutePath, telemetry).telemetryThemeChange,
    )
    assertEquals(
      TelemetryThemeId.SYNC_HUD,
      engine.execute("hudtheme sync-hud", fs.workspaceRoot.absolutePath, telemetry).telemetryThemeChange,
    )
  }

  @Test
  fun `viewmodel preserves active tab when closing earlier tab and resolves file paths on sync`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = EdexViewModel(app)
    vm.skipBoot()

    vm.addTerminalTab()
    vm.addTerminalTab()
    assertEquals(3, vm.tabs.value.size)
    assertEquals(2, vm.activeTabIndex.value)

    // Select middle tab (tab-2 at index 1), then close tab-1 (index 0): tab-2 must remain active at index 0
    vm.selectTerminalTab(1)
    val selectedId = vm.tabs.value[1].id
    vm.closeTerminalTab(0)
    assertEquals(2, vm.tabs.value.size)
    assertEquals(0, vm.activeTabIndex.value)
    assertEquals(selectedId, vm.tabs.value[vm.activeTabIndex.value].id)

    // Closing the active last tab clamps back safely
    vm.selectTerminalTab(1)
    vm.closeTerminalTab(1)
    assertEquals(1, vm.tabs.value.size)
    assertEquals(0, vm.activeTabIndex.value)

    // Syncing terminal to a file path resolves to its parent directory
    val readme = File(vm.getWorkspaceRootPath(), "README.edex")
    vm.selectSection(EdexSectionTab.FILES)
    vm.syncTerminalToPath(readme.absolutePath)
    assertEquals(EdexSectionTab.TERMINAL, vm.activeSection.value)
    assertEquals(File(vm.getWorkspaceRootPath()).absolutePath, vm.tabs.value[0].cwd)
  }

  @Test
  fun `telemetry repository parses proc net sockets and maps active connection nodes`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = SystemTelemetryRepository(context)

    // 2E50FA8E little-endian = 142.250.80.46, 01BB = 443, st 01 = ESTABLISHED
    val tcpLine =
      "   0: 0F02000A:B41E 2E50FA8E:01BB 01 00000000:00000000 00:00000000 00000000 10123 0 12345"
    val parsed = repo.parseProcNetLine(tcpLine, protocol = "TCP", isIpv6 = false)
    assertNotNull(parsed)
    assertEquals("142.250.80.46", parsed?.ip)
    assertEquals(443, parsed?.port)
    assertEquals("ESTAB", parsed?.state)

    // Loopback and LISTEN sockets should be ignored
    val loopbackLine =
      "   1: 0100007F:1F90 0100007F:9999 01 00000000:00000000 00:00000000 00000000 10123 0 12346"
    assertNull(repo.parseProcNetLine(loopbackLine, protocol = "TCP", isIpv6 = false))

    val listenLine =
      "   2: 00000000:0050 00000000:0000 0A 00000000:00000000 00:00000000 00000000 10123 0 12347"
    assertNull(repo.parseProcNetLine(listenLine, protocol = "TCP", isIpv6 = false))

    // IPv4-mapped IPv6 address in /proc/net/tcp6
    val tcp6MappedLine =
      "   3: 0000000000000000FFFF00000F02000A:B41E 0000000000000000FFFF00002E50FA8E:01BB 01 00000000:00000000 00:00000000 00000000 10123 0 12348"
    val parsedV6 = repo.parseProcNetLine(tcp6MappedLine, protocol = "TCP", isIpv6 = true)
    assertNotNull(parsedV6)
    assertEquals("142.250.80.46", parsedV6?.ip)
    assertEquals(443, parsedV6?.port)

    val nodes =
      repo.collectActiveConnectionNodes(
        connected = true,
        interfaceName = "wlan0",
        localIp = "192.168.1.44",
        gatewayIp = "192.168.1.1",
        dnsServers = listOf("1.1.1.1", "8.8.8.8"),
        rxKbps = 45f,
        txKbps = 12f,
      )
    assertTrue(nodes.any { it.isOrigin && it.endpoint == "192.168.1.44" })
    assertTrue(nodes.any { it.protocol == "GW" && it.endpoint == "192.168.1.1" })
    assertTrue(nodes.any { it.protocol == "DNS" && it.endpoint == "1.1.1.1" })
    assertTrue(nodes.all { it.latDeg in -90.0..90.0 && it.lonDeg in -180.0..180.0 })

    val offlineNodes =
      repo.collectActiveConnectionNodes(
        connected = false,
        interfaceName = "lo",
        localIp = "127.0.0.1",
        gatewayIp = "--",
        dnsServers = emptyList(),
        rxKbps = 0f,
        txKbps = 0f,
      )
    assertEquals(1, offlineNodes.size)
    assertTrue(offlineNodes.first().isOrigin)
  }

  @Test
  fun `net globe panel renders active connection nodes and telemetry service packet badge`() {
    val activeTelemetry =
      SystemTelemetry(
        networkConnected = true,
        networkTransport = "WIFI // WLAN",
        interfaceName = "wlan0",
        ipAddress = "10.0.2.15",
        gatewayIp = "10.0.2.2",
        dnsServers = listOf("8.8.8.8"),
        packetsPerSec = 42L,
        packetTrafficActive = true,
        activeConnections =
          listOf(
            NetworkConnectionNode(
              id = "origin_wlan0",
              label = "WLAN0",
              endpoint = "10.0.2.15",
              protocol = "LOCAL",
              state = "UP",
              latDeg = 34.0,
              lonDeg = -120.0,
              isOrigin = true,
            ),
            NetworkConnectionNode(
              id = "tcp_1",
              label = "TCP:443",
              endpoint = "142.250.80.46:443",
              protocol = "TCP",
              state = "ESTAB",
              latDeg = 37.4,
              lonDeg = -122.1,
            ),
          ),
      )

    var probedNode: NetworkConnectionNode? = null
    var diagRan = false

    composeRule.setContent {
      EdexTheme(themeId = EdexThemeId.TRON) {
        var selectedNodeId by remember { mutableStateOf<String?>(null) }
        var nodeFilter by remember { mutableStateOf(GlobeNodeFilter.ALL) }
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          NetGlobePanel(
            telemetry = activeTelemetry,
            isServiceRunning = true,
            selectedNodeId = selectedNodeId,
            nodeFilter = nodeFilter,
            onSelectNode = { selectedNodeId = it },
            onSelectFilter = { nodeFilter = it },
            onProbeNode = { probedNode = it },
            onRunNetDiagnostic = { diagRan = true },
          )
        }
      }
    }

    composeRule.onNodeWithTag("network_globe_canvas").assertIsDisplayed()
    composeRule.onNodeWithTag("globe_packet_status").assertIsDisplayed()
    composeRule.onNodeWithText("PKT 42/s").assertIsDisplayed()
    composeRule.onNodeWithTag("globe_active_connections").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("ACTIVE NODES (2)").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("globe_node_chip_tcp_1").performScrollTo().performClick()
    composeRule.onNodeWithTag("globe_selected_node_detail").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("globe_probe_ping_button").performScrollTo().performClick()
    assertEquals("tcp_1", probedNode?.id)
    composeRule.onNodeWithTag("globe_ifconfig_button").performScrollTo().performClick()
    assertTrue(diagRan)
    composeRule.onNodeWithTag("globe_filter_gw_dns").performScrollTo().performClick()
    composeRule.onNodeWithText("ACTIVE NODES (1)").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `net globe panel indicates paused telemetry service when service is stopped`() {
    val telemetry =
      SystemTelemetry(
        networkConnected = true,
        networkTransport = "WIFI // WLAN",
        interfaceName = "wlan0",
        ipAddress = "10.0.2.15",
        packetsPerSec = 99L,
        packetTrafficActive = true,
      )

    composeRule.setContent {
      EdexTheme(themeId = EdexThemeId.TRON) {
        NetGlobePanel(telemetry = telemetry, isServiceRunning = false)
      }
    }

    composeRule.onNodeWithTag("globe_packet_status").assertIsDisplayed()
    composeRule.onNodeWithText("SVC PAUSED").assertIsDisplayed()
  }

  @Test
  fun `globe hit testing and node filter select visible front-facing nodes accurately`() {
    val frontNode =
      NetworkConnectionNode(
        id = "front",
        label = "FRONT",
        endpoint = "142.250.80.46:443",
        protocol = "TCP",
        state = "ESTAB",
        latDeg = 0.0,
        lonDeg = 0.0,
      )
    val backNode =
      NetworkConnectionNode(
        id = "back",
        label = "BACK",
        endpoint = "8.8.8.8",
        protocol = "DNS",
        state = "RESOLV",
        latDeg = 0.0,
        lonDeg = 180.0,
      )
    val originNode =
      NetworkConnectionNode(
        id = "origin",
        label = "WLAN0",
        endpoint = "10.0.2.15",
        protocol = "LOCAL",
        state = "UP",
        latDeg = 0.0,
        lonDeg = 0.0,
        isOrigin = true,
      )

    // With yaw=0, pitch=0, roll=0: lat=0, lon=0 projects to Vec3(x=1, y=0, z=0) -> screen (cx + r, cy)
    val nodeVectors =
      listOf(
        frontNode to Vec3(1f, 0f, 0f),
        backNode to Vec3(0f, 0f, -1f),
      )

    val hit =
      findNearestVisibleNode(
        tapOffset = Offset(302f, 204f),
        nodeVectors = nodeVectors,
        yawRad = 0f,
        pitchRad = 0f,
        rollRad = 0f,
        cx = 200f,
        cy = 200f,
        radius = 100f,
        maxDistancePx = 30f,
      )
    assertEquals("front", hit?.id)

    // Tapping center misses front (100px away) and ignores back (z = -1 <= -0.10)
    val miss =
      findNearestVisibleNode(
        tapOffset = Offset(200f, 200f),
        nodeVectors = nodeVectors,
        yawRad = 0f,
        pitchRad = 0f,
        rollRad = 0f,
        cx = 200f,
        cy = 200f,
        radius = 100f,
        maxDistancePx = 30f,
      )
    assertNull(miss)

    val all = listOf(originNode, frontNode, backNode)
    assertEquals(3, filterGlobeConnections(all, GlobeNodeFilter.ALL).size)
    assertEquals(listOf("origin", "front"), filterGlobeConnections(all, GlobeNodeFilter.SOCKETS).map { it.id })
    assertEquals(listOf("origin", "back"), filterGlobeConnections(all, GlobeNodeFilter.GW_DNS).map { it.id })
  }

  @Test
  fun `preferences repository round-trips macro decks and hud profiles and falls back on malformed json`() {
    val defaultDecks = EdexDefaults.defaultMacroDecks()
    val customDeck =
      defaultDecks.map { deck ->
        if (deck.id == "recon") {
          deck.copy(
            macros =
              deck.macros +
                ShellMacro(
                  id = "m_custom",
                  label = "trace",
                  commandTemplate = "ping {selected_ip}",
                  requiresConfirm = true,
                  accentRole = MacroAccentRole.WARNING,
                )
          )
        } else {
          deck
        }
      }
    val encodedDecks = EdexPreferencesRepository.encodeMacroDecks(customDeck)
    val decodedDecks = EdexPreferencesRepository.decodeMacroDecks(encodedDecks)
    assertEquals(customDeck, decodedDecks)
    assertEquals(defaultDecks, EdexPreferencesRepository.decodeMacroDecks("{not-valid-json"))
    assertEquals(defaultDecks, EdexPreferencesRepository.decodeMacroDecks(""))

    val defaultProfiles = EdexDefaults.defaultHudProfiles()
    val customProfiles =
      defaultProfiles +
        defaultProfiles.first().copy(
          id = "prof_custom_1",
          name = "NIGHT OPS",
          themeId = EdexThemeId.MATRIX,
          telemetryThemeId = TelemetryThemeId.CYBERPUNK_NEON,
          glitchLoadThreshold = 55f,
          isBuiltIn = false,
        )
    val encodedProfiles = EdexPreferencesRepository.encodeHudProfiles(customProfiles)
    val decodedProfiles = EdexPreferencesRepository.decodeHudProfiles(encodedProfiles)
    assertEquals(customProfiles, decodedProfiles)
    assertEquals(defaultProfiles, EdexPreferencesRepository.decodeHudProfiles("corrupt"))
  }

  @Test
  fun `viewmodel resolves macro tokens handles confirmation and manages custom macros and profiles`() = runBlocking {
    assertEquals("142.250.80.46", extractHostFromEndpoint("142.250.80.46:443"))
    assertEquals("2001:db8::1", extractHostFromEndpoint("[2001:db8::1]:8443"))
    assertEquals("8.8.8.8", extractHostFromEndpoint("8.8.8.8"))

    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = EdexViewModel(app)
    vm.skipBoot()

    TelemetryHub.publish(
      SystemTelemetry(
        networkConnected = true,
        interfaceName = "wlan0",
        gatewayIp = "192.168.50.1",
        activeConnections =
          listOf(
            NetworkConnectionNode(
              id = "tcp_42",
              label = "TCP:443",
              endpoint = "93.184.216.34:443",
              protocol = "TCP",
              state = "ESTAB",
              latDeg = 42.3,
              lonDeg = -71.0,
            )
          ),
      )
    )

    // Without selected node, {selected_ip} falls back to gatewayIp
    vm.selectGlobeNode(null)
    val gwResolved = vm.resolveMacroCommand("ping {selected_ip} // {iface}")
    assertEquals("ping 192.168.50.1 // wlan0", gwResolved)

    // With selected node, {selected_ip} resolves to node host without port
    vm.selectGlobeNode("tcp_42")
    val nodeResolved = vm.resolveMacroCommand("ping {selected_ip} in {cwd}")
    assertTrue(nodeResolved.startsWith("ping 93.184.216.34 in "))

    // Confirm-required macro opens confirmation dialog instead of executing immediately
    val confirmMacro =
      ShellMacro(
        id = "m_confirm_test",
        label = "probe confirm",
        commandTemplate = "ping {selected_ip}",
        requiresConfirm = true,
        accentRole = MacroAccentRole.WARNING,
      )
    vm.triggerMacro(confirmMacro)
    assertNotNull(vm.pendingMacroConfirm.value)
    assertEquals("ping 93.184.216.34", vm.pendingMacroConfirm.value?.resolvedCommand)
    vm.dismissPendingMacro()
    assertNull(vm.pendingMacroConfirm.value)

    // Probing node navigates to TERMINAL
    vm.selectSection(EdexSectionTab.NETWORK)
    vm.probeGlobeNode(
      NetworkConnectionNode(
        id = "dns_1",
        label = "DNS",
        endpoint = "1.1.1.1:53",
        protocol = "DNS",
        state = "RESOLV",
        latDeg = -33.8,
        lonDeg = 151.2,
      )
    )
    assertEquals(EdexSectionTab.TERMINAL, vm.activeSection.value)

    // Adding/editing macro opens editor cleanly
    vm.openAddMacroDialog()
    assertNotNull(vm.editingMacroState.value)
    assertNull(vm.editingMacroState.value?.existingMacro)
    vm.dismissMacroEditor()
    assertNull(vm.editingMacroState.value)

    // Filter clears selected node when hidden
    vm.selectGlobeNode("tcp_42")
    vm.selectGlobeNodeFilter(GlobeNodeFilter.GW_DNS)
    assertNull(vm.selectedGlobeNodeId.value)
  }

  @Test
  fun `macro deck bar, confirmation dialog, and hud profiles section render and dispatch interactions`() {
    var selectedDeck = "recon"
    var executedMacro: ShellMacro? = null
    var addClicked = false
    var savedProfileName = ""

    composeRule.setContent {
      EdexTheme(themeId = EdexThemeId.TRON) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          MacroDeckBar(
            decks = EdexDefaults.defaultMacroDecks(),
            activeDeckId = selectedDeck,
            onSelectDeck = { selectedDeck = it },
            onRunMacro = { executedMacro = it },
            onAddMacro = { addClicked = true },
            onEditMacro = {},
          )
          HudProfilesSection(
            profiles = EdexDefaults.defaultHudProfiles(),
            activeProfileId = "prof_stealth_tron",
            onApplyProfile = {},
            onSaveCurrentProfile = { savedProfileName = it },
            onDeleteProfile = {},
          )
        }
      }
    }

    composeRule.onNodeWithTag("macro_deck_kernel").performClick()
    assertEquals("kernel", selectedDeck)
    composeRule.onNodeWithTag("macro_add_button").performClick()
    assertTrue(addClicked)
    composeRule.onNodeWithTag("macro_chip_m_ifconfig").performClick()
    assertEquals("m_ifconfig", executedMacro?.id)

    composeRule.onNodeWithTag("hud_profile_name_input").performScrollTo().performTextInput("Delta Rig")
    composeRule.onNodeWithTag("save_hud_profile_button").performScrollTo().performClick()
    assertEquals("Delta Rig", savedProfileName)
  }

  @Test
  fun `macro confirm dialog executes and dismisses cleanly`() {
    var executed = false
    var dismissed = false
    val macro =
      ShellMacro(
        id = "m_reboot",
        label = "reboot hud",
        commandTemplate = "reboot",
        requiresConfirm = true,
        accentRole = MacroAccentRole.WARNING,
      )

    composeRule.setContent {
      EdexTheme(themeId = EdexThemeId.TRON) {
        MacroConfirmDialog(
          macro = macro,
          resolvedCommand = "reboot",
          onConfirm = { executed = true },
          onDismiss = { dismissed = true },
        )
      }
    }

    composeRule.onNodeWithText("$ reboot").assertIsDisplayed()
    composeRule.onNodeWithTag("macro_confirm_execute").performClick()
    assertTrue(executed)
    assertFalse(dismissed)
  }
}
