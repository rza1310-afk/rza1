package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.EdexSectionTab
import com.example.model.SystemTelemetry
import com.example.ui.components.BootSequenceOverlay
import com.example.ui.components.CpuTelemetryPanel
import com.example.ui.components.CrtScanlineOverlay
import com.example.ui.components.EdexTopHeaderBar
import com.example.ui.components.FileInspectorDialog
import com.example.ui.components.FilesystemPanel
import com.example.ui.components.HudGlitchShaderContainer
import com.example.ui.components.MacroConfirmDialog
import com.example.ui.components.MacroEditorDialog
import com.example.ui.components.MemoryAndStoragePanel
import com.example.ui.components.NetGlobePanel
import com.example.ui.components.NewFileEntryDialog
import com.example.ui.components.ProcessesPanel
import com.example.ui.components.SciFiKeyboard
import com.example.ui.components.SettingsDialog
import com.example.ui.components.SystemClockPanel
import com.example.ui.components.TelemetryDaemonPanel
import com.example.ui.components.TelemetryThemeSwitcherBar
import com.example.ui.components.TerminalPanel
import com.example.ui.theme.LocalEdexPalette
import com.example.ui.theme.TelemetryThemeOverlay
import com.example.ui.viewmodel.EdexViewModel
import java.util.Locale

@Composable
fun EdexDashboardScreen(viewModel: EdexViewModel) {
  val palette = LocalEdexPalette.current
  val context = LocalContext.current
  val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
  val isServiceRunning by viewModel.isTelemetryServiceRunning.collectAsStateWithLifecycle()
  val sampleCount by viewModel.telemetrySampleCount.collectAsStateWithLifecycle()
  val prefs by viewModel.preferences.collectAsStateWithLifecycle()
  val prefsLoaded by viewModel.preferencesLoaded.collectAsStateWithLifecycle()
  val isBooting by viewModel.isBooting.collectAsStateWithLifecycle()
  val activeSection by viewModel.activeSection.collectAsStateWithLifecycle()
  val tabs by viewModel.tabs.collectAsStateWithLifecycle()
  val activeTabIndex by viewModel.activeTabIndex.collectAsStateWithLifecycle()
  val terminalInput by viewModel.terminalInput.collectAsStateWithLifecycle()
  val isRunning by viewModel.isCommandRunning.collectAsStateWithLifecycle()
  val streamSystemLogs by viewModel.streamSystemLogs.collectAsStateWithLifecycle()
  val currentPath by viewModel.currentBrowsePath.collectAsStateWithLifecycle()
  val files by viewModel.files.collectAsStateWithLifecycle()
  val selectedFilePath by viewModel.selectedFilePath.collectAsStateWithLifecycle()
  val inspectedFile by viewModel.inspectedFile.collectAsStateWithLifecycle()
  val showSettings by viewModel.showSettingsDialog.collectAsStateWithLifecycle()
  val showNewFile by viewModel.showNewFileDialog.collectAsStateWithLifecycle()
  val selectedGlobeNodeId by viewModel.selectedGlobeNodeId.collectAsStateWithLifecycle()
  val globeNodeFilter by viewModel.globeNodeFilter.collectAsStateWithLifecycle()
  val editingMacroState by viewModel.editingMacroState.collectAsStateWithLifecycle()
  val pendingMacroConfirm by viewModel.pendingMacroConfirm.collectAsStateWithLifecycle()

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
      if (prefs.backgroundMonitorEnabled) {
        viewModel.startTelemetryService()
      }
    }

  LaunchedEffect(prefsLoaded, prefs.backgroundMonitorEnabled) {
    if (!prefsLoaded) return@LaunchedEffect
    if (prefs.backgroundMonitorEnabled && !isServiceRunning) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted =
          ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
          viewModel.startTelemetryService()
        } else {
          notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
      } else {
        viewModel.startTelemetryService()
      }
    }
  }

  val ramPercent =
    ((telemetry.ramUsedMb.toFloat() / telemetry.ramTotalMb.coerceAtLeast(1L)) * 100f)
      .coerceIn(0f, 100f)
  val systemLoadPercent = maxOf(telemetry.cpuLoadPercent, ramPercent)
  val isOverloadGlitch =
    prefs.glitchShaderEnabled && systemLoadPercent >= prefs.glitchLoadThreshold
  val glitchIntensity =
    if (!isOverloadGlitch) 0f
    else
      (((systemLoadPercent - prefs.glitchLoadThreshold) /
            (100f - prefs.glitchLoadThreshold).coerceAtLeast(5f)) * 0.7f + 0.3f)
        .coerceIn(0.3f, 1f)

  if (activeSection != EdexSectionTab.TERMINAL) {
    BackHandler { viewModel.selectSection(EdexSectionTab.TERMINAL) }
  }

  Scaffold(
    containerColor = palette.background,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { _ ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.radialGradient(
              colors = listOf(palette.surface.copy(alpha = 0.7f), palette.background)
            )
          )
    ) {
      BoxWithConstraints(
        modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()
      ) {
        val isExpandedLayout = maxWidth >= 840.dp

        Column(modifier = Modifier.fillMaxSize()) {
          EdexTopHeaderBar(
            timeFormatted = telemetry.timeFormatted,
            cpuPercent = telemetry.cpuLoadPercent,
            ramUsedMb = telemetry.ramUsedMb,
            ramTotalMb = telemetry.ramTotalMb,
            soundEnabled = prefs.soundEnabled,
            showKeyboard = prefs.showKeyboard,
            onToggleSound = viewModel::toggleSound,
            onToggleKeyboard = viewModel::toggleKeyboard,
            onOpenSettings = viewModel::openSettings,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )

          if (isExpandedLayout) {
            // Full 3-column eDEX-UI cockpit for Tablets / Foldables / Landscape
            Row(
              modifier =
                Modifier.weight(1f)
                  .fillMaxWidth()
                  .navigationBarsPadding()
                  .padding(horizontal = 8.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              // Left Telemetry Column wrapped in Telemetry Theme + AGSL Glitch Shader when overloaded
              TelemetryThemeOverlay(
                telemetryThemeId = prefs.telemetryThemeId,
                globalThemeId = prefs.themeId,
              ) {
                HudGlitchShaderContainer(
                  active = isOverloadGlitch,
                  intensity = glitchIntensity,
                  modifier = Modifier.weight(0.28f).fillMaxHeight(),
                ) {
                  Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    TelemetryThemeSwitcherBar(
                      selectedTheme = prefs.telemetryThemeId,
                      globalThemeId = prefs.themeId,
                      onSelectTheme = viewModel::setTelemetryTheme,
                    )
                    SystemClockPanel(telemetry)
                    TelemetryDaemonPanel(
                      isRunning = isServiceRunning,
                      sampleCount = sampleCount,
                      telemetry = telemetry,
                      loadPercent = systemLoadPercent,
                      glitchThreshold = prefs.glitchLoadThreshold,
                      glitchActive = isOverloadGlitch,
                      onToggleService = viewModel::toggleTelemetryService,
                    )
                    CpuTelemetryPanel(telemetry)
                    MemoryAndStoragePanel(telemetry)
                    ProcessesPanel(telemetry.activeProcesses)
                  }
                }
              }

              // Center Terminal + Optional Sci-Fi Keyboard Column
              Column(
                modifier = Modifier.weight(0.44f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                TerminalPanel(
                  tabs = tabs,
                  activeTabIndex = activeTabIndex,
                  inputText = terminalInput,
                  isRunning = isRunning,
                  streamSystemLogs = streamSystemLogs,
                  onToggleStreamSystemLogs = viewModel::toggleStreamSystemLogs,
                  onSelectTab = viewModel::selectTerminalTab,
                  onAddTab = viewModel::addTerminalTab,
                  onCloseTab = viewModel::closeTerminalTab,
                  onClearTerminal = viewModel::clearActiveTerminal,
                  onInputChange = viewModel::updateTerminalInput,
                  onSubmitCommand = viewModel::submitCurrentInput,
                  onHistoryUp = viewModel::historyUp,
                  onHistoryDown = viewModel::historyDown,
                  modifier = Modifier.weight(1f).fillMaxWidth(),
                  macroDecks = prefs.macroDecks,
                  activeMacroDeckId = prefs.activeMacroDeckId,
                  onSelectMacroDeck = viewModel::selectMacroDeck,
                  onRunMacro = viewModel::triggerMacro,
                  onAddMacro = viewModel::openAddMacroDialog,
                  onEditMacro = viewModel::openEditMacroDialog,
                  onScrollToLatest = viewModel::onTerminalScrollToLatest,
                )
                AnimatedVisibility(visible = prefs.showKeyboard) {
                  SciFiKeyboard(
                    onKeyPress = viewModel::appendKey,
                    onBackspace = viewModel::backspaceInput,
                    onEnter = viewModel::submitCurrentInput,
                    onTabComplete = viewModel::autocompleteInput,
                  )
                }
              }

              // Right Network Globe & Filesystem Column
              Column(
                modifier = Modifier.weight(0.28f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                TelemetryThemeOverlay(
                  telemetryThemeId = prefs.telemetryThemeId,
                  globalThemeId = prefs.themeId,
                ) {
                  HudGlitchShaderContainer(
                    active = isOverloadGlitch,
                    intensity = glitchIntensity,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    NetGlobePanel(
                      telemetry = telemetry,
                      isServiceRunning = isServiceRunning,
                      selectedNodeId = selectedGlobeNodeId,
                      nodeFilter = globeNodeFilter,
                      onSelectNode = viewModel::selectGlobeNode,
                      onSelectFilter = viewModel::selectGlobeNodeFilter,
                      onProbeNode = viewModel::probeGlobeNode,
                      onRunNetDiagnostic = viewModel::runNetworkDiagnostic,
                      onGlobeTap = viewModel::onGlobeTap,
                      onGlobeReset = viewModel::onGlobeReset,
                    )
                  }
                }
                FilesystemPanel(
                  currentPath = currentPath,
                  workspaceRoot = viewModel.getWorkspaceRootPath(),
                  files = files,
                  selectedFilePath = selectedFilePath,
                  onNavigateUp = viewModel::navigateUpDirectory,
                  onNavigatePath = viewModel::navigateDirectory,
                  onRefresh = { viewModel.refreshDirectory(playFeedback = true) },
                  onOpenNewDialog = viewModel::openNewFileDialog,
                  onSelectFile = viewModel::selectFileItem,
                  onOpenFile = viewModel::openFile,
                  onDeleteFile = viewModel::deleteFile,
                  onSyncTerminal = viewModel::syncTerminalToPath,
                  modifier = Modifier.weight(1f).fillMaxWidth(),
                  onFilterChange = viewModel::onFileFilterChanged,
                )
              }
            }
          } else {
            // Compact / Medium Handheld Layout
            Box(
              modifier =
                Modifier.weight(1f)
                  .fillMaxWidth()
                  .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              when (activeSection) {
                EdexSectionTab.TERMINAL -> {
                  Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    TelemetryThemeOverlay(
                      telemetryThemeId = prefs.telemetryThemeId,
                      globalThemeId = prefs.themeId,
                    ) {
                      HudGlitchShaderContainer(
                        active = isOverloadGlitch,
                        intensity = glitchIntensity,
                        modifier = Modifier.fillMaxWidth(),
                      ) {
                        CompactTelemetryTicker(telemetry, isServiceRunning, isOverloadGlitch)
                      }
                    }
                    TerminalPanel(
                      tabs = tabs,
                      activeTabIndex = activeTabIndex,
                      inputText = terminalInput,
                      isRunning = isRunning,
                      streamSystemLogs = streamSystemLogs,
                      onToggleStreamSystemLogs = viewModel::toggleStreamSystemLogs,
                      onSelectTab = viewModel::selectTerminalTab,
                      onAddTab = viewModel::addTerminalTab,
                      onCloseTab = viewModel::closeTerminalTab,
                      onClearTerminal = viewModel::clearActiveTerminal,
                      onInputChange = viewModel::updateTerminalInput,
                      onSubmitCommand = viewModel::submitCurrentInput,
                      onHistoryUp = viewModel::historyUp,
                      onHistoryDown = viewModel::historyDown,
                      modifier = Modifier.weight(1f).fillMaxWidth(),
                      macroDecks = prefs.macroDecks,
                      activeMacroDeckId = prefs.activeMacroDeckId,
                      onSelectMacroDeck = viewModel::selectMacroDeck,
                      onRunMacro = viewModel::triggerMacro,
                      onAddMacro = viewModel::openAddMacroDialog,
                      onEditMacro = viewModel::openEditMacroDialog,
                      onScrollToLatest = viewModel::onTerminalScrollToLatest,
                    )
                    AnimatedVisibility(visible = prefs.showKeyboard) {
                      SciFiKeyboard(
                        onKeyPress = viewModel::appendKey,
                        onBackspace = viewModel::backspaceInput,
                        onEnter = viewModel::submitCurrentInput,
                        onTabComplete = viewModel::autocompleteInput,
                      )
                    }
                  }
                }

                EdexSectionTab.TELEMETRY -> {
                  TelemetryThemeOverlay(
                    telemetryThemeId = prefs.telemetryThemeId,
                    globalThemeId = prefs.themeId,
                  ) {
                    HudGlitchShaderContainer(
                      active = isOverloadGlitch,
                      intensity = glitchIntensity,
                      modifier = Modifier.fillMaxSize(),
                    ) {
                      Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                      ) {
                        TelemetryThemeSwitcherBar(
                          selectedTheme = prefs.telemetryThemeId,
                          globalThemeId = prefs.themeId,
                          onSelectTheme = viewModel::setTelemetryTheme,
                        )
                        TelemetryDaemonPanel(
                          isRunning = isServiceRunning,
                          sampleCount = sampleCount,
                          telemetry = telemetry,
                          loadPercent = systemLoadPercent,
                          glitchThreshold = prefs.glitchLoadThreshold,
                          glitchActive = isOverloadGlitch,
                          onToggleService = viewModel::toggleTelemetryService,
                        )
                        SystemClockPanel(telemetry)
                        CpuTelemetryPanel(telemetry)
                        MemoryAndStoragePanel(telemetry)
                        ProcessesPanel(telemetry.activeProcesses)
                        Spacer(modifier = Modifier.height(6.dp))
                      }
                    }
                  }
                }

                EdexSectionTab.NETWORK -> {
                  TelemetryThemeOverlay(
                    telemetryThemeId = prefs.telemetryThemeId,
                    globalThemeId = prefs.themeId,
                  ) {
                    HudGlitchShaderContainer(
                      active = isOverloadGlitch,
                      intensity = glitchIntensity,
                      modifier = Modifier.fillMaxSize(),
                    ) {
                      Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                      ) {
                        NetGlobePanel(
                          telemetry = telemetry,
                          isServiceRunning = isServiceRunning,
                          selectedNodeId = selectedGlobeNodeId,
                          nodeFilter = globeNodeFilter,
                          onSelectNode = viewModel::selectGlobeNode,
                          onSelectFilter = viewModel::selectGlobeNodeFilter,
                          onProbeNode = viewModel::probeGlobeNode,
                          onRunNetDiagnostic = viewModel::runNetworkDiagnostic,
                          onGlobeTap = viewModel::onGlobeTap,
                          onGlobeReset = viewModel::onGlobeReset,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                      }
                    }
                  }
                }

                EdexSectionTab.FILES -> {
                  FilesystemPanel(
                    currentPath = currentPath,
                    workspaceRoot = viewModel.getWorkspaceRootPath(),
                    files = files,
                    selectedFilePath = selectedFilePath,
                    onNavigateUp = viewModel::navigateUpDirectory,
                    onNavigatePath = viewModel::navigateDirectory,
                    onRefresh = { viewModel.refreshDirectory(playFeedback = true) },
                    onOpenNewDialog = viewModel::openNewFileDialog,
                    onSelectFile = viewModel::selectFileItem,
                    onOpenFile = viewModel::openFile,
                    onDeleteFile = viewModel::deleteFile,
                    onSyncTerminal = viewModel::syncTerminalToPath,
                    modifier = Modifier.fillMaxSize(),
                    onFilterChange = viewModel::onFileFilterChanged,
                  )
                }
              }
            }

            // Bottom HUD Navigation Bar with WindowInsets.navigationBars padding
            CompactHudBottomNav(
              activeSection = activeSection,
              onSelect = viewModel::selectSection,
            )
          }
        }
      }

      if (prefs.scanlinesEnabled) {
        CrtScanlineOverlay()
      }

      AnimatedVisibility(
        visible = isBooting,
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        BootSequenceOverlay(
          deviceModel = telemetry.deviceModel,
          osVersion = telemetry.osVersion,
          onSkip = viewModel::skipBoot,
        )
      }

      inspectedFile?.let { fileState ->
        FileInspectorDialog(
          state = fileState,
          onSave = viewModel::saveInspectedFile,
          onDismiss = viewModel::closeInspectedFile,
          onTextEdit = viewModel::onFileEditorTextChanged,
        )
      }

      if (showNewFile) {
        NewFileEntryDialog(
          onCreate = viewModel::createWorkspaceEntry,
          onDismiss = viewModel::closeNewFileDialog,
        )
      }

      if (showSettings) {
        SettingsDialog(
          preferences = prefs,
          isTelemetryServiceRunning = isServiceRunning,
          onSelectTheme = viewModel::setTheme,
          onSelectTelemetryTheme = viewModel::setTelemetryTheme,
          onApplyHudProfile = viewModel::applyHudProfile,
          onSaveCurrentHudProfile = viewModel::saveCurrentAsHudProfile,
          onDeleteHudProfile = viewModel::deleteHudProfile,
          onToggleSound = viewModel::toggleSound,
          onToggleScanlines = viewModel::toggleScanlines,
          onToggleKeyboard = viewModel::toggleKeyboard,
          onToggleTelemetryService = viewModel::toggleTelemetryService,
          onToggleGlitchShader = viewModel::toggleGlitchShader,
          onChangeGlitchThreshold = viewModel::setGlitchLoadThreshold,
          onReplayBoot = viewModel::replayBoot,
          onDismiss = viewModel::closeSettings,
        )
      }

      editingMacroState?.let { editor ->
        MacroEditorDialog(
          initialMacro = editor.existingMacro,
          deckName = editor.deckName,
          onSave = viewModel::saveMacro,
          onDelete = if (editor.existingMacro != null) viewModel::deleteEditingMacro else null,
          onDismiss = viewModel::dismissMacroEditor,
        )
      }

      pendingMacroConfirm?.let { pending ->
        MacroConfirmDialog(
          macro = pending.macro,
          resolvedCommand = pending.resolvedCommand,
          onConfirm = viewModel::confirmPendingMacro,
          onDismiss = viewModel::dismissPendingMacro,
        )
      }
    }
  }
}

@Composable
private fun CompactTelemetryTicker(
  telemetry: SystemTelemetry,
  isServiceRunning: Boolean,
  isOverload: Boolean,
) {
  val palette = LocalEdexPalette.current
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = palette.surface.copy(alpha = 0.9f),
    border = BorderStroke(1.dp, if (isOverload) palette.danger else palette.gridLine),
    shape = CutCornerShape(6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TickerChip(
        "FGS",
        if (isServiceRunning) "ON" else "OFF",
        if (isServiceRunning) palette.success else palette.warning,
      )
      TickerChip(
        "CPU",
        "${telemetry.cpuLoadPercent.toInt()}%",
        if (isOverload) palette.danger else palette.primaryBright,
      )
      TickerChip("RAM", "${telemetry.ramUsedMb}M", palette.primary)
      TickerChip(
        "NET",
        String.format(Locale.US, "↓%.0fK", telemetry.rxSpeedKbps),
        if (telemetry.networkConnected) palette.success else palette.warning,
      )
      TickerChip(
        "BAT",
        "${telemetry.batteryPercent}%",
        if (telemetry.batteryCharging) palette.success else palette.textPrimary,
      )
    }
  }
}

@Composable
private fun TickerChip(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
  val palette = LocalEdexPalette.current
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      text = "$label ",
      style = MaterialTheme.typography.labelSmall,
      color = palette.textMuted,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.labelSmall,
      color = valueColor,
    )
  }
}

@Composable
private fun CompactHudBottomNav(
  activeSection: EdexSectionTab,
  onSelect: (EdexSectionTab) -> Unit,
) {
  val palette = LocalEdexPalette.current
  Surface(
    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
    color = palette.surface.copy(alpha = 0.96f),
    border = BorderStroke(1.dp, palette.border),
    shape = CutCornerShape(topStart = 8.dp, topEnd = 8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      HudNavItem(
        label = stringResource(R.string.tab_terminal),
        icon = Icons.Filled.Terminal,
        selected = activeSection == EdexSectionTab.TERMINAL,
        onClick = { onSelect(EdexSectionTab.TERMINAL) },
        modifier = Modifier.weight(1f).testTag("nav_terminal"),
      )
      HudNavItem(
        label = stringResource(R.string.tab_telemetry),
        icon = Icons.Filled.Memory,
        selected = activeSection == EdexSectionTab.TELEMETRY,
        onClick = { onSelect(EdexSectionTab.TELEMETRY) },
        modifier = Modifier.weight(1f).testTag("nav_telemetry"),
      )
      HudNavItem(
        label = stringResource(R.string.tab_network),
        icon = Icons.Filled.Public,
        selected = activeSection == EdexSectionTab.NETWORK,
        onClick = { onSelect(EdexSectionTab.NETWORK) },
        modifier = Modifier.weight(1f).testTag("nav_network"),
      )
      HudNavItem(
        label = stringResource(R.string.tab_files),
        icon = Icons.Filled.Folder,
        selected = activeSection == EdexSectionTab.FILES,
        onClick = { onSelect(EdexSectionTab.FILES) },
        modifier = Modifier.weight(1f).testTag("nav_files"),
      )
    }
  }
}

@Composable
private fun HudNavItem(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalEdexPalette.current
  val tint = if (selected) palette.primaryBright else palette.textMuted
  Column(
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .background(
          if (selected) palette.primaryDim.copy(alpha = 0.45f) else androidx.compose.ui.graphics.Color.Transparent,
          CutCornerShape(6.dp),
        )
        .clickable(onClick = onClick)
        .padding(vertical = 6.dp, horizontal = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = tint,
      maxLines = 1,
    )
  }
}
