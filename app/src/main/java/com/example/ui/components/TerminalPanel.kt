package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.EdexDefaults
import com.example.model.MacroDeck
import com.example.model.ShellMacro
import com.example.model.TerminalLine
import com.example.model.TerminalLineType
import com.example.model.TerminalTab
import com.example.ui.theme.LocalEdexPalette
import com.example.ui.viewmodel.EdexViewModel
import kotlinx.coroutines.launch

@Composable
fun TerminalPanel(
  tabs: List<TerminalTab>,
  activeTabIndex: Int,
  inputText: String,
  isRunning: Boolean,
  streamSystemLogs: Boolean,
  onToggleStreamSystemLogs: () -> Unit,
  onSelectTab: (Int) -> Unit,
  onAddTab: () -> Unit,
  onCloseTab: (Int) -> Unit,
  onClearTerminal: () -> Unit,
  onInputChange: (String) -> Unit,
  onSubmitCommand: () -> Unit,
  onHistoryUp: () -> Unit,
  onHistoryDown: () -> Unit,
  modifier: Modifier = Modifier,
  macroDecks: List<MacroDeck> = EdexDefaults.defaultMacroDecks(),
  activeMacroDeckId: String = "recon",
  onSelectMacroDeck: (String) -> Unit = {},
  onRunMacro: (ShellMacro) -> Unit = {},
  onAddMacro: () -> Unit = {},
  onEditMacro: (ShellMacro) -> Unit = {},
  onScrollToLatest: () -> Unit = {},
) {
  val palette = LocalEdexPalette.current
  val activeTab = tabs.getOrNull(activeTabIndex)
  val lines = activeTab?.lines.orEmpty()

  val blinkTransition = rememberInfiniteTransition(label = "cursor_blink")
  val cursorAlpha by
    blinkTransition.animateFloat(
      initialValue = 1f,
      targetValue = 0.15f,
      animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
      label = "cursor_alpha",
    )

  EdexHudPanel(
    title = "TERMINAL // SH",
    subtitle =
      if (isRunning) "EXEC..."
      else "BUF ${lines.size}/${EdexViewModel.MAX_BUFFER_LINES}",
    modifier = modifier,
    headerActions = {
      Surface(
        modifier =
          Modifier.testTag("toggle_live_logs_button").clickable(onClick = onToggleStreamSystemLogs),
        color =
          if (streamSystemLogs) palette.success.copy(alpha = 0.16f)
          else palette.surfaceElevated,
        border =
          BorderStroke(1.dp, if (streamSystemLogs) palette.success else palette.border),
        shape = CutCornerShape(4.dp),
      ) {
        Text(
          text = if (streamSystemLogs) "SYS LOGS: ON" else "SYS LOGS: OFF",
          style = MaterialTheme.typography.labelSmall,
          color = if (streamSystemLogs) palette.success else palette.textMuted,
          modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
        )
      }
      IconButton(
        onClick = onClearTerminal,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_terminal_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.ClearAll,
          contentDescription = stringResource(R.string.cd_clear_terminal),
          tint = palette.textMuted,
          modifier = Modifier.size(18.dp),
        )
      }
      IconButton(
        onClick = onAddTab,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("new_terminal_tab_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.Add,
          contentDescription = stringResource(R.string.cd_new_tab),
          tint = palette.primaryBright,
          modifier = Modifier.size(18.dp),
        )
      }
    },
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Session Tab Strip
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        tabs.forEachIndexed { idx, tab ->
          val selected = idx == activeTabIndex
          Surface(
            modifier = Modifier.testTag("terminal_tab_$idx").clickable { onSelectTab(idx) },
            color =
              if (selected) palette.primaryDim.copy(alpha = 0.7f)
              else palette.background.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, if (selected) palette.primaryBright else palette.border),
            shape = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = tab.title,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) palette.primary else palette.textMuted,
                maxLines = 1,
              )
              if (tabs.size > 1) {
                Spacer(modifier = Modifier.width(2.dp))
                Box(
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .testTag("close_terminal_tab_$idx")
                      .clickable { onCloseTab(idx) },
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_close_tab),
                    tint = palette.textMuted,
                    modifier = Modifier.size(14.dp),
                  )
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      // Programmable Macro Deck Bar
      MacroDeckBar(
        decks = macroDecks,
        activeDeckId = activeMacroDeckId,
        onSelectDeck = onSelectMacroDeck,
        onRunMacro = onRunMacro,
        onAddMacro = onAddMacro,
        onEditMacro = onEditMacro,
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Scrolling Text Buffer for System Logs & Command Output
      TerminalScrollingBuffer(
        lines = lines,
        tabKey = activeTab?.id ?: activeTabIndex,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        onScrollToLatest = onScrollToLatest,
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Command Prompt Input Row
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(palette.background, CutCornerShape(6.dp))
            .border(1.dp, palette.primaryBright.copy(alpha = 0.7f), CutCornerShape(6.dp))
            .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "$",
          style = MaterialTheme.typography.bodyLarge,
          color = palette.primaryBright,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
          if (inputText.isEmpty()) {
            Text(
              text = stringResource(R.string.terminal_input_hint),
              style = MaterialTheme.typography.bodySmall,
              color = palette.textMuted.copy(alpha = 0.65f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          BasicTextField(
            value = inputText,
            onValueChange = onInputChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.textPrimary),
            cursorBrush = SolidColor(palette.primaryBright.copy(alpha = cursorAlpha)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSubmitCommand() }),
            modifier = Modifier.fillMaxWidth().testTag("terminal_command_input"),
          )
        }
        IconButton(
          onClick = onHistoryUp,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("terminal_history_up"),
        ) {
          Icon(
            imageVector = Icons.Filled.ArrowUpward,
            contentDescription = "Previous Command",
            tint = palette.textMuted,
            modifier = Modifier.size(16.dp),
          )
        }
        IconButton(
          onClick = onHistoryDown,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("terminal_history_down"),
        ) {
          Icon(
            imageVector = Icons.Filled.ArrowDownward,
            contentDescription = "Next Command",
            tint = palette.textMuted,
            modifier = Modifier.size(16.dp),
          )
        }
        IconButton(
          onClick = onSubmitCommand,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("terminal_send_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = stringResource(R.string.cd_run_command),
            tint = palette.primaryBright,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}

@Composable
fun TerminalScrollingBuffer(
  lines: List<TerminalLine>,
  tabKey: Any,
  modifier: Modifier = Modifier,
  onScrollToLatest: () -> Unit = {},
) {
  val palette = LocalEdexPalette.current
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()

  val isScrolledAway by remember { derivedStateOf { listState.canScrollForward } }

  val lastLineId = lines.lastOrNull()?.id

  LaunchedEffect(tabKey) {
    if (lines.isNotEmpty()) {
      listState.scrollToItem(lines.lastIndex)
    }
  }

  LaunchedEffect(lastLineId) {
    if (lines.isNotEmpty() && !listState.isScrollInProgress) {
      val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      if (lines.size - lastVisible <= 6 || lastVisible == 0) {
        listState.animateScrollToItem(lines.lastIndex)
      }
    }
  }

  Box(
    modifier =
      modifier
        .background(palette.background.copy(alpha = 0.85f), CutCornerShape(4.dp))
        .border(1.dp, palette.gridLine, CutCornerShape(4.dp))
        .padding(horizontal = 8.dp, vertical = 6.dp)
        .testTag("terminal_text_buffer")
  ) {
    if (lines.isEmpty()) {
      Text(
        text = "TERMINAL BUFFER CLEARED. READY FOR SYSTEM LOGS & COMMAND OUTPUT.",
        style = MaterialTheme.typography.bodySmall,
        color = palette.textMuted,
      )
    } else {
      SelectionContainer {
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          items(lines, key = { it.id }) { line ->
            val color =
              when (line.type) {
                TerminalLineType.COMMAND -> palette.primaryBright
                TerminalLineType.ACCENT -> palette.primary
                TerminalLineType.SUCCESS -> palette.success
                TerminalLineType.ERROR -> palette.danger
                TerminalLineType.SYSTEM_LOG -> palette.secondary
                TerminalLineType.OUTPUT -> palette.textPrimary
              }
            Text(
              text = line.text,
              style = MaterialTheme.typography.bodySmall,
              color = color,
            )
          }
        }
      }
    }

    AnimatedVisibility(
      visible = isScrolledAway && lines.isNotEmpty(),
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
    ) {
      Surface(
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .testTag("terminal_scroll_bottom_button")
            .clickable {
              onScrollToLatest()
              scope.launch {
                if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
              }
            },
        color = palette.surfaceElevated.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, palette.primaryBright),
        shape = CutCornerShape(4.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Filled.ArrowDownward,
            contentDescription = "Scroll to latest output",
            tint = palette.primaryBright,
            modifier = Modifier.size(13.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "LATEST",
            style = MaterialTheme.typography.labelSmall,
            color = palette.primaryBright,
          )
        }
      }
    }
  }
}
