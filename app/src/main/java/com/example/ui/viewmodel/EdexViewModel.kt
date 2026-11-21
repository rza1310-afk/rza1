package com.example.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.EdexPreferencesRepository
import com.example.data.FilesystemRepository
import com.example.data.HudSoundManager
import com.example.data.SystemTelemetryRepository
import com.example.data.TelemetryHub
import com.example.data.TerminalEngine
import com.example.data.createWelcomeTerminalLines
import com.example.model.EdexSectionTab
import com.example.model.EdexUserPreferences
import com.example.model.FileItem
import com.example.model.SystemTelemetry
import com.example.model.TerminalLine
import com.example.model.TerminalLineType
import com.example.model.TerminalTab
import com.example.service.TelemetryMonitorService
import com.example.ui.theme.EdexThemeId
import com.example.ui.theme.TelemetryThemeId
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InspectedFileState(
  val item: FileItem,
  val content: String,
  val isEditable: Boolean,
)

class EdexViewModel(application: Application) : AndroidViewModel(application) {
  private val prefsRepo = EdexPreferencesRepository(application)
  private val telemetryRepo = SystemTelemetryRepository(application)
  private val filesystemRepo = FilesystemRepository(application)
  private val terminalEngine = TerminalEngine(filesystemRepo)
  private val soundManager = HudSoundManager()

  private val lineIdGenerator = AtomicLong(100L)
  private var tabCounter = 1
  private var activeCommands = 0
  private var bootJob: Job? = null

  private val _preferencesLoaded = MutableStateFlow(false)
  val preferencesLoaded: StateFlow<Boolean> = _preferencesLoaded.asStateFlow()

  val preferences: StateFlow<EdexUserPreferences> =
    prefsRepo.preferences
      .onEach { _preferencesLoaded.value = true }
      .stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        EdexUserPreferences(),
      )

  val telemetry: StateFlow<SystemTelemetry> = TelemetryHub.telemetry

  val isTelemetryServiceRunning: StateFlow<Boolean> = TelemetryHub.isServiceRunning

  val telemetrySampleCount: StateFlow<Long> = TelemetryHub.sampleCount

  private val _isBooting = MutableStateFlow(true)
  val isBooting: StateFlow<Boolean> = _isBooting.asStateFlow()

  private val _activeSection = MutableStateFlow(EdexSectionTab.TERMINAL)
  val activeSection: StateFlow<EdexSectionTab> = _activeSection.asStateFlow()

  private val _tabs = MutableStateFlow<List<TerminalTab>>(emptyList())
  val tabs: StateFlow<List<TerminalTab>> = _tabs.asStateFlow()

  private val _activeTabIndex = MutableStateFlow(0)
  val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

  private val _terminalInput = MutableStateFlow("")
  val terminalInput: StateFlow<String> = _terminalInput.asStateFlow()

  private val _isCommandRunning = MutableStateFlow(false)
  val isCommandRunning: StateFlow<Boolean> = _isCommandRunning.asStateFlow()

  private val _currentBrowsePath = MutableStateFlow("")
  val currentBrowsePath: StateFlow<String> = _currentBrowsePath.asStateFlow()

  private val _files = MutableStateFlow<List<FileItem>>(emptyList())
  val files: StateFlow<List<FileItem>> = _files.asStateFlow()

  private val _selectedFilePath = MutableStateFlow<String?>(null)
  val selectedFilePath: StateFlow<String?> = _selectedFilePath.asStateFlow()

  private val _inspectedFile = MutableStateFlow<InspectedFileState?>(null)
  val inspectedFile: StateFlow<InspectedFileState?> = _inspectedFile.asStateFlow()

  private val _showSettingsDialog = MutableStateFlow(false)
  val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

  private val _showNewFileDialog = MutableStateFlow(false)
  val showNewFileDialog: StateFlow<Boolean> = _showNewFileDialog.asStateFlow()

  private val _streamSystemLogs = MutableStateFlow(true)
  val streamSystemLogs: StateFlow<Boolean> = _streamSystemLogs.asStateFlow()

  private var historyCursor: Int = -1

  init {
    filesystemRepo.ensureWorkspaceSeeded()
    val rootPath = filesystemRepo.workspaceRoot.absolutePath
    _currentBrowsePath.value = rootPath

    val deviceLabel = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL}"
    _tabs.value =
      listOf(
        TerminalTab(
          id = "tab-1",
          title = "MAIN // SH",
          cwd = rootPath,
          lines = createWelcomeTerminalLines(deviceLabel),
        )
      )

    if (TelemetryHub.sampleCount.value == 0L) {
      TelemetryHub.publish(telemetryRepo.collectSnapshot())
    }

    viewModelScope.launch {
      preferences.collect { prefs -> soundManager.enabled = prefs.soundEnabled }
    }

    viewModelScope.launch {
      TelemetryHub.liveLogEvents.collect { logEntry ->
        if (_streamSystemLogs.value) {
          val line =
            TerminalLine(
              id = lineIdGenerator.incrementAndGet(),
              text = "[${logEntry.timestamp}] [${logEntry.tag}] ${logEntry.message}",
              type = if (logEntry.isError) TerminalLineType.ERROR else TerminalLineType.SYSTEM_LOG,
              timestamp = logEntry.timestamp,
            )
          _tabs.update { currentTabs ->
            if (currentTabs.isEmpty()) return@update currentTabs
            val activeIdx = _activeTabIndex.value.coerceIn(0, currentTabs.lastIndex)
            currentTabs.mapIndexed { idx, tab ->
              if (idx != activeIdx) tab
              else tab.copy(lines = (tab.lines + line).takeLast(MAX_BUFFER_LINES))
            }
          }
        }
      }
    }

    refreshDirectory(rootPath)
    startBootSequence()
  }

  private fun startBootSequence() {
    bootJob?.cancel()
    _isBooting.value = true
    soundManager.playBootHum()
    bootJob =
      viewModelScope.launch {
        delay(1500L)
        _isBooting.value = false
      }
  }

  fun skipBoot() {
    bootJob?.cancel()
    soundManager.playUiSwitch()
    _isBooting.value = false
  }

  fun replayBoot() {
    _showSettingsDialog.value = false
    startBootSequence()
  }

  fun selectSection(section: EdexSectionTab) {
    soundManager.playUiSwitch()
    _activeSection.value = section
  }

  fun updateTerminalInput(value: String) {
    val previous = _terminalInput.value
    if (value != previous) {
      historyCursor = -1
      if (value.length < previous.length) {
        soundManager.playTerminalBackspace()
      } else {
        soundManager.playTerminalType()
      }
    }
    _terminalInput.value = value
  }

  fun appendKey(text: String) {
    historyCursor = -1
    soundManager.playTerminalType()
    _terminalInput.update { it + text }
  }

  fun backspaceInput() {
    historyCursor = -1
    soundManager.playTerminalBackspace()
    _terminalInput.update { current ->
      if (current.isNotEmpty()) current.dropLast(1) else current
    }
  }

  fun historyUp() {
    val activeTab = _tabs.value.getOrNull(_activeTabIndex.value) ?: return
    val hist = activeTab.commandHistory
    if (hist.isEmpty()) return
    soundManager.playTerminalType()
    historyCursor =
      if (historyCursor < 0) hist.lastIndex else (historyCursor - 1).coerceAtLeast(0)
    _terminalInput.value = hist[historyCursor]
  }

  fun historyDown() {
    val activeTab = _tabs.value.getOrNull(_activeTabIndex.value) ?: return
    val hist = activeTab.commandHistory
    if (hist.isEmpty() || historyCursor < 0) return
    soundManager.playTerminalType()
    if (historyCursor >= hist.lastIndex) {
      historyCursor = -1
      _terminalInput.value = ""
    } else {
      historyCursor++
      _terminalInput.value = hist[historyCursor]
    }
  }

  fun autocompleteInput() {
    val current = _terminalInput.value
    val builtIns =
      listOf(
        "help",
        "neofetch",
        "sysinfo",
        "theme ",
        "hudtheme ",
        "sound ",
        "scanlines ",
        "ls",
        "cd ",
        "cat ",
        "pwd",
        "ifconfig",
        "ping ",
        "free",
        "df",
        "ps",
        "logs",
        "logcat",
        "daemon ",
        "glitch ",
        "clear",
        "mkdir ",
        "touch ",
      )
    val match = builtIns.firstOrNull { it.startsWith(current, ignoreCase = true) }
    if (match != null && current.isNotBlank()) {
      soundManager.playTerminalType()
      _terminalInput.value = match
    }
  }

  fun submitCurrentInput() {
    val cmd = _terminalInput.value
    _terminalInput.value = ""
    historyCursor = -1
    runCommand(cmd)
  }

  fun runCommand(rawCommand: String) {
    val trimmed = rawCommand.trim()
    val tabIndex = _activeTabIndex.value
    val activeTab = _tabs.value.getOrNull(tabIndex) ?: return
    val targetTabId = activeTab.id

    if (trimmed.isEmpty()) {
      soundManager.playTerminalType()
      return
    }

    soundManager.playCommandExec()
    val promptLine =
      TerminalLine(
        id = lineIdGenerator.incrementAndGet(),
        text = "${shortPath(activeTab.cwd)} $ $trimmed",
        type = TerminalLineType.COMMAND,
      )

    _tabs.update { currentTabs ->
      currentTabs.map { tab ->
        if (tab.id != targetTabId) tab
        else
          tab.copy(
            lines = (tab.lines + promptLine).takeLast(MAX_BUFFER_LINES),
            commandHistory = (tab.commandHistory + trimmed).takeLast(50),
          )
      }
    }

    viewModelScope.launch {
      activeCommands++
      _isCommandRunning.value = true
      val result =
        try {
          terminalEngine.execute(trimmed, activeTab.cwd, telemetry.value)
        } finally {
          activeCommands = (activeCommands - 1).coerceAtLeast(0)
          _isCommandRunning.value = activeCommands > 0
        }

      if (result.themeChange != null) setTheme(result.themeChange)
      if (result.telemetryThemeChange != null) setTelemetryTheme(result.telemetryThemeChange)
      if (result.soundChange != null) prefsRepo.setSoundEnabled(result.soundChange)
      if (result.scanlinesChange != null) prefsRepo.setScanlinesEnabled(result.scanlinesChange)
      if (result.daemonChange != null) setTelemetryServiceEnabled(result.daemonChange)
      if (result.glitchChange != null) prefsRepo.setGlitchShaderEnabled(result.glitchChange)
      if (result.glitchThresholdChange != null) prefsRepo.setGlitchLoadThreshold(result.glitchThresholdChange)
      if (result.replayBoot) replayBoot()

      if (result.outputLines.any { it.second == TerminalLineType.ERROR }) {
        soundManager.playError()
      } else if (result.outputLines.isNotEmpty()) {
        soundManager.playDataBurst()
      }

      val newLines =
        result.outputLines.map { (text, type) ->
          TerminalLine(id = lineIdGenerator.incrementAndGet(), text = text, type = type)
        }

      _tabs.update { currentTabs ->
        currentTabs.map { tab ->
          if (tab.id != targetTabId) tab
          else {
            val updatedLines =
              if (result.clearScreen) emptyList()
              else (tab.lines + newLines).takeLast(MAX_BUFFER_LINES)
            tab.copy(cwd = result.newCwd, lines = updatedLines)
          }
        }
      }

      val currentActiveTabId = _tabs.value.getOrNull(_activeTabIndex.value)?.id
      if (result.newCwd != activeTab.cwd && currentActiveTabId == targetTabId) {
        refreshDirectory(result.newCwd)
      } else {
        refreshDirectory(_currentBrowsePath.value)
      }
    }
  }

  fun addTerminalTab() {
    if (_tabs.value.size >= 5) return
    soundManager.playUiSwitch()
    tabCounter++
    val cwd = filesystemRepo.workspaceRoot.absolutePath
    val newTab =
      TerminalTab(
        id = "tab-$tabCounter",
        title = "TERM $tabCounter",
        cwd = cwd,
        lines =
          listOf(
            TerminalLine(
              id = lineIdGenerator.incrementAndGet(),
              text = "Session #$tabCounter initialized at $cwd",
              type = TerminalLineType.ACCENT,
            )
          ),
      )
    _tabs.update { it + newTab }
    _activeTabIndex.value = _tabs.value.lastIndex
  }

  fun selectTerminalTab(index: Int) {
    if (index in _tabs.value.indices) {
      soundManager.playUiSwitch()
      _activeTabIndex.value = index
      historyCursor = -1
    }
  }

  fun closeTerminalTab(index: Int) {
    val currentTabs = _tabs.value
    if (currentTabs.size <= 1 || index !in currentTabs.indices) return
    soundManager.playUiSwitch()
    val currentActive = _activeTabIndex.value.coerceIn(0, currentTabs.lastIndex)
    val updatedTabs = currentTabs.filterIndexed { i, _ -> i != index }
    val nextActive =
      when {
        index < currentActive -> currentActive - 1
        index == currentActive -> currentActive.coerceAtMost(updatedTabs.lastIndex)
        else -> currentActive
      }.coerceIn(0, updatedTabs.lastIndex)
    if (index == currentActive) {
      historyCursor = -1
    }
    _tabs.value = updatedTabs
    _activeTabIndex.value = nextActive
  }

  fun clearActiveTerminal() {
    soundManager.playUiSwitch()
    _tabs.update { list ->
      if (list.isEmpty()) return@update list
      val idx = _activeTabIndex.value.coerceIn(0, list.lastIndex)
      list.mapIndexed { i, tab -> if (i == idx) tab.copy(lines = emptyList()) else tab }
    }
  }

  fun onTerminalScrollToLatest() {
    soundManager.playUiSwitch()
  }

  fun onGlobeTap() {
    soundManager.playUiSwitch()
  }

  fun onGlobeReset() {
    soundManager.playDataProcess()
  }

  fun onFileFilterChanged() {
    soundManager.playUiSwitch()
  }

  fun onFileEditorTextChanged(previous: String, updated: String) {
    if (updated.length < previous.length) {
      soundManager.playTerminalBackspace()
    } else if (updated != previous) {
      soundManager.playTerminalType()
    }
  }

  fun refreshDirectory(path: String = _currentBrowsePath.value, playFeedback: Boolean = false) {
    if (playFeedback) soundManager.playDataProcess()
    viewModelScope.launch {
      _currentBrowsePath.value = path
      val loaded = filesystemRepo.listDirectory(path)
      _files.value = loaded
      if (_selectedFilePath.value != null && loaded.none { it.path == _selectedFilePath.value }) {
        _selectedFilePath.value = null
      }
    }
  }

  fun navigateDirectory(path: String) {
    soundManager.playDataProcess()
    _selectedFilePath.value = null
    refreshDirectory(path)
  }

  fun navigateUpDirectory() {
    val parent = File(_currentBrowsePath.value).parentFile ?: return
    if (parent.canRead() || parent.absolutePath == "/") {
      soundManager.playDataProcess()
      _selectedFilePath.value = null
      refreshDirectory(parent.absolutePath)
    }
  }

  fun selectFileItem(item: FileItem) {
    if (_selectedFilePath.value == item.path) {
      openFile(item)
    } else {
      soundManager.playUiSwitch()
      _selectedFilePath.value = item.path
    }
  }

  fun openFile(item: FileItem) {
    if (item.isDirectory) {
      navigateDirectory(item.path)
      return
    }
    soundManager.playDataProcess()
    viewModelScope.launch {
      val content =
        filesystemRepo
          .readFileContent(item.path)
          .getOrElse { err -> "Unable to read file: ${err.message}" }
      val editable = filesystemRepo.isWithinWorkspace(item.path, allowRoot = false)
      _inspectedFile.value = InspectedFileState(item, content, editable)
    }
  }

  fun saveInspectedFile(newContent: String) {
    val inspected = _inspectedFile.value ?: return
    viewModelScope.launch {
      val res = filesystemRepo.writeFile(inspected.item.path, newContent)
      if (res.isSuccess) {
        soundManager.playDataProcess()
        _inspectedFile.value = null
        refreshDirectory(_currentBrowsePath.value)
      } else {
        soundManager.playError()
      }
    }
  }

  fun closeInspectedFile() {
    soundManager.playUiSwitch()
    _inspectedFile.value = null
  }

  fun openNewFileDialog() {
    soundManager.playUiSwitch()
    _showNewFileDialog.value = true
  }

  fun closeNewFileDialog() {
    soundManager.playUiSwitch()
    _showNewFileDialog.value = false
  }

  fun createWorkspaceEntry(name: String, isDirectory: Boolean) {
    viewModelScope.launch {
      val targetDir = _currentBrowsePath.value
      val res =
        if (isDirectory) filesystemRepo.createDirectory(targetDir, name)
        else filesystemRepo.createFile(targetDir, name, "")
      if (res.isSuccess) {
        soundManager.playDataProcess()
        _showNewFileDialog.value = false
        refreshDirectory(targetDir)
      } else {
        soundManager.playError()
      }
    }
  }

  fun deleteFile(item: FileItem) {
    viewModelScope.launch {
      val res = filesystemRepo.deleteItem(item.path)
      if (res.isSuccess) {
        soundManager.playDataBurst()
        val openPath = _inspectedFile.value?.item?.path
        if (openPath != null && (openPath == item.path || openPath.startsWith(item.path + File.separator))) {
          _inspectedFile.value = null
        }
        refreshDirectory(_currentBrowsePath.value)
      } else {
        soundManager.playError()
      }
    }
  }

  fun syncTerminalToPath(path: String) {
    val targetFile = File(path)
    val resolvedDir =
      when {
        targetFile.isDirectory -> targetFile.absolutePath
        targetFile.parentFile?.isDirectory == true -> targetFile.parentFile!!.absolutePath
        else -> filesystemRepo.workspaceRoot.absolutePath
      }
    soundManager.playDataProcess()
    _tabs.update { list ->
      if (list.isEmpty()) return@update list
      val idx = _activeTabIndex.value.coerceIn(0, list.lastIndex)
      list.mapIndexed { i, tab ->
        if (i != idx) tab
        else
          tab.copy(
            cwd = resolvedDir,
            lines =
              (tab.lines +
                  TerminalLine(
                    id = lineIdGenerator.incrementAndGet(),
                    text = "CWD synced -> $resolvedDir",
                    type = TerminalLineType.ACCENT,
                  ))
                .takeLast(MAX_BUFFER_LINES),
          )
      }
    }
    _activeSection.value = EdexSectionTab.TERMINAL
  }

  fun getWorkspaceRootPath(): String = filesystemRepo.workspaceRoot.absolutePath

  fun openSettings() {
    soundManager.playUiSwitch()
    _showSettingsDialog.value = true
  }

  fun closeSettings() {
    soundManager.playUiSwitch()
    _showSettingsDialog.value = false
  }

  fun setTheme(themeId: EdexThemeId) {
    soundManager.playDataBurst()
    viewModelScope.launch { prefsRepo.setTheme(themeId) }
  }

  fun setTelemetryTheme(themeId: TelemetryThemeId) {
    soundManager.playDataBurst()
    viewModelScope.launch { prefsRepo.setTelemetryTheme(themeId) }
  }

  fun toggleSound() {
    viewModelScope.launch {
      val next = !preferences.value.soundEnabled
      prefsRepo.setSoundEnabled(next)
      soundManager.enabled = next
      if (next) soundManager.playDataProcess()
    }
  }

  fun toggleScanlines() {
    soundManager.playUiSwitch()
    viewModelScope.launch { prefsRepo.setScanlinesEnabled(!preferences.value.scanlinesEnabled) }
  }

  fun toggleKeyboard() {
    soundManager.playUiSwitch()
    viewModelScope.launch { prefsRepo.setShowKeyboard(!preferences.value.showKeyboard) }
  }

  fun startTelemetryService() {
    TelemetryMonitorService.start(getApplication())
  }

  fun stopTelemetryService() {
    TelemetryMonitorService.stop(getApplication())
  }

  fun setTelemetryServiceEnabled(enabled: Boolean) {
    soundManager.playDataProcess()
    viewModelScope.launch { prefsRepo.setBackgroundMonitorEnabled(enabled) }
    if (enabled) {
      startTelemetryService()
    } else {
      stopTelemetryService()
    }
  }

  fun toggleTelemetryService() {
    setTelemetryServiceEnabled(!TelemetryHub.isServiceRunning.value)
  }

  fun toggleStreamSystemLogs() {
    soundManager.playUiSwitch()
    _streamSystemLogs.update { !it }
  }

  fun toggleGlitchShader() {
    soundManager.playDataBurst()
    viewModelScope.launch { prefsRepo.setGlitchShaderEnabled(!preferences.value.glitchShaderEnabled) }
  }

  fun setGlitchLoadThreshold(threshold: Float) {
    soundManager.playDataBurst()
    viewModelScope.launch { prefsRepo.setGlitchLoadThreshold(threshold) }
  }

  private fun shortPath(path: String): String {
    val root = filesystemRepo.workspaceRoot.absolutePath
    return if (path.startsWith(root)) "~" + path.removePrefix(root) else path
  }

  override fun onCleared() {
    soundManager.release()
    super.onCleared()
  }

  companion object {
    const val MAX_BUFFER_LINES = 500
  }
}
