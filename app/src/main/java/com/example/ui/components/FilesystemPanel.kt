package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.FileItem
import com.example.ui.theme.LocalEdexPalette
import com.example.ui.viewmodel.InspectedFileState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class FileFilterMode(val label: String) {
  ALL("ALL"),
  DIRS("DIRS"),
  FILES("FILES"),
}

@Composable
fun FilesystemPanel(
  currentPath: String,
  workspaceRoot: String,
  files: List<FileItem>,
  selectedFilePath: String?,
  onNavigateUp: () -> Unit,
  onNavigatePath: (String) -> Unit,
  onRefresh: () -> Unit,
  onOpenNewDialog: () -> Unit,
  onSelectFile: (FileItem) -> Unit,
  onOpenFile: (FileItem) -> Unit,
  onDeleteFile: (FileItem) -> Unit,
  onSyncTerminal: (String) -> Unit,
  modifier: Modifier = Modifier,
  onFilterChange: () -> Unit = {},
) {
  val palette = LocalEdexPalette.current
  val inWorkspace =
    currentPath == workspaceRoot || currentPath.startsWith("$workspaceRoot/")
  var filterMode by remember { mutableStateOf(FileFilterMode.ALL) }

  val filteredFiles =
    remember(files, filterMode) {
      when (filterMode) {
        FileFilterMode.ALL -> files
        FileFilterMode.DIRS -> files.filter { it.isDirectory }
        FileFilterMode.FILES -> files.filter { !it.isDirectory }
      }
    }

  val dirCount = remember(files) { files.count { it.isDirectory } }
  val fileCount = remember(files) { files.count { !it.isDirectory } }

  EdexHudPanel(
    title = "FILESYSTEM // EXPLORER",
    subtitle = "$dirCount DIR • $fileCount FILE",
    modifier = modifier,
    headerActions = {
      IconButton(
        onClick = onNavigateUp,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("files_up_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(R.string.cd_parent_dir),
          tint = palette.primary,
          modifier = Modifier.size(18.dp),
        )
      }
      IconButton(
        onClick = onRefresh,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("files_refresh_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.Refresh,
          contentDescription = stringResource(R.string.cd_refresh_files),
          tint = palette.primary,
          modifier = Modifier.size(18.dp),
        )
      }
      if (inWorkspace) {
        IconButton(
          onClick = onOpenNewDialog,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("files_new_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.CreateNewFolder,
            contentDescription = stringResource(R.string.cd_new_file),
            tint = palette.primaryBright,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    },
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Quick Mount Chips + CD Here
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val mounts =
          listOf(
            "WORKSPACE" to workspaceRoot,
            "/proc" to "/proc",
            "/system" to "/system",
            "/" to "/",
          )
        mounts.forEach { (label, path) ->
          val active = currentPath == path
          val bgColor by
            animateColorAsState(
              targetValue = if (active) palette.primaryDim else palette.surfaceElevated,
              animationSpec = tween(220),
              label = "mount_bg_$label",
            )
          val borderColor by
            animateColorAsState(
              targetValue = if (active) palette.primaryBright else palette.gridLine,
              animationSpec = tween(220),
              label = "mount_border_$label",
            )
          Surface(
            modifier =
              Modifier.testTag("mount_chip_${label.lowercase()}")
                .clickable { onNavigatePath(path) },
            color = bgColor,
            border = BorderStroke(1.dp, borderColor),
            shape = CutCornerShape(4.dp),
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelSmall,
              color = if (active) palette.primaryBright else palette.textPrimary,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            )
          }
        }
        Surface(
          modifier = Modifier.testTag("sync_terminal_cwd").clickable { onSyncTerminal(currentPath) },
          color = palette.primaryBright.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, palette.primaryBright),
          shape = CutCornerShape(4.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.Terminal,
              contentDescription = null,
              tint = palette.primaryBright,
              modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "CD HERE",
              style = MaterialTheme.typography.labelSmall,
              color = palette.primaryBright,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      // Interactive Breadcrumb Bar + Filter Pills
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        BreadcrumbBar(
          currentPath = currentPath,
          onNavigatePath = onNavigatePath,
          modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          FileFilterMode.entries.forEach { mode ->
            val selected = filterMode == mode
            val pillBg by
              animateColorAsState(
                targetValue =
                  if (selected) palette.primaryBright.copy(alpha = 0.22f) else palette.background,
                animationSpec = tween(180),
                label = "filter_bg_${mode.name}",
              )
            Surface(
              modifier =
                Modifier.testTag("file_filter_${mode.name.lowercase()}")
                  .clickable {
                    if (filterMode != mode) {
                      filterMode = mode
                      onFilterChange()
                    }
                  },
              color = pillBg,
              border = BorderStroke(1.dp, if (selected) palette.primaryBright else palette.gridLine),
              shape = CutCornerShape(4.dp),
            ) {
              Text(
                text = mode.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) palette.primaryBright else palette.textMuted,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      if (filteredFiles.isEmpty()) {
        Box(
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth()
              .background(palette.background.copy(alpha = 0.6f), CutCornerShape(4.dp))
              .border(1.dp, palette.gridLine, CutCornerShape(4.dp)),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text =
              if (files.isEmpty()) "DIRECTORY EMPTY OR RESTRICTED"
              else "NO ${filterMode.label} IN CURRENT DIRECTORY",
            style = MaterialTheme.typography.labelMedium,
            color = palette.textMuted,
          )
        }
      } else {
        LazyColumn(
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth()
              .background(palette.background.copy(alpha = 0.6f), CutCornerShape(4.dp))
              .border(1.dp, palette.gridLine, CutCornerShape(4.dp))
              .padding(6.dp),
          verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          items(filteredFiles, key = { it.path }) { item ->
            val isSelected = selectedFilePath == item.path
            FileExplorerRow(
              item = item,
              isSelected = isSelected,
              inWorkspace = inWorkspace,
              onSelect = { onSelectFile(item) },
              onOpen = { onOpenFile(item) },
              onSyncTerminal = { onSyncTerminal(item.path) },
              onDelete = { onDeleteFile(item) },
              modifier = Modifier.animateItem(),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BreadcrumbBar(
  currentPath: String,
  onNavigatePath: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalEdexPalette.current
  val segments =
    remember(currentPath) {
      val parts = currentPath.split('/').filter { it.isNotEmpty() }
      buildList {
        add("/" to "/")
        var acc = ""
        parts.forEach { part ->
          acc += "/$part"
          add(part to acc)
        }
      }
    }

  Row(
    modifier = modifier.horizontalScroll(rememberScrollState()),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    segments.forEachIndexed { idx, (label, path) ->
      val isLast = idx == segments.lastIndex
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (isLast) palette.primaryBright else palette.textMuted,
        modifier =
          Modifier.clickable { onNavigatePath(path) }.padding(horizontal = 3.dp, vertical = 2.dp),
      )
      if (!isLast && label != "/") {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = palette.textMuted.copy(alpha = 0.6f),
          modifier = Modifier.size(12.dp),
        )
      }
    }
  }
}

@Composable
private fun FileExplorerRow(
  item: FileItem,
  isSelected: Boolean,
  inWorkspace: Boolean,
  onSelect: () -> Unit,
  onOpen: () -> Unit,
  onSyncTerminal: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalEdexPalette.current

  // Animated sci-fi selection states
  val containerBg by
    animateColorAsState(
      targetValue =
        if (isSelected) palette.primaryDim.copy(alpha = 0.68f)
        else palette.surfaceElevated.copy(alpha = 0.55f),
      animationSpec = tween(durationMillis = 220),
      label = "row_bg_${item.name}",
    )
  val borderColor by
    animateColorAsState(
      targetValue = if (isSelected) palette.primaryBright else palette.gridLine,
      animationSpec = tween(durationMillis = 220),
      label = "row_border_${item.name}",
    )
  val railColor by
    animateColorAsState(
      targetValue =
        when {
          isSelected -> palette.primaryBright
          item.isDirectory -> palette.primary.copy(alpha = 0.65f)
          else -> palette.textMuted.copy(alpha = 0.35f)
        },
      animationSpec = tween(durationMillis = 220),
      label = "rail_color_${item.name}",
    )
  val railWidth by
    animateDpAsState(
      targetValue = if (isSelected) 6.dp else 2.dp,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
      label = "rail_width_${item.name}",
    )
  val startIndent by
    animateDpAsState(
      targetValue = if (isSelected) 4.dp else 0.dp,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
      label = "row_indent_${item.name}",
    )
  val iconScale by
    animateFloatAsState(
      targetValue = if (isSelected) 1.15f else 1.0f,
      animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
      label = "icon_scale_${item.name}",
    )
  val reticleAlpha by
    animateFloatAsState(
      targetValue = if (isSelected) 1f else 0.35f,
      animationSpec = tween(durationMillis = 200),
      label = "reticle_alpha_${item.name}",
    )

  val typeTag =
    remember(item.name, item.isDirectory) {
      if (item.isDirectory) "DIR"
      else item.name.substringAfterLast('.', "").uppercase(Locale.US).take(4).ifBlank { "FILE" }
    }

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(start = startIndent)
        .clickable(onClick = onSelect)
        .testTag("file_row_${item.name}"),
    color = containerBg,
    border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
    shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Left animated HUD targeting rail
        Box(
          modifier =
            Modifier.width(railWidth)
              .height(28.dp)
              .background(railColor, CutCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
          imageVector =
            when {
              item.isDirectory && isSelected -> Icons.Filled.FolderOpen
              item.isDirectory -> Icons.Filled.Folder
              else -> Icons.Filled.Description
            },
          contentDescription = null,
          tint =
            if (isSelected || item.isDirectory) palette.primaryBright else palette.primary,
          modifier = Modifier.size(20.dp).scale(iconScale),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = item.name,
              style = MaterialTheme.typography.bodySmall,
              color =
                when {
                  isSelected -> palette.primaryBright
                  item.isDirectory -> palette.primary
                  else -> palette.textPrimary
                },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
              modifier =
                Modifier.background(palette.background.copy(alpha = 0.7f), CutCornerShape(3.dp))
                  .border(1.dp, palette.border.copy(alpha = reticleAlpha), CutCornerShape(3.dp))
                  .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
              Text(
                text = typeTag,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) palette.primaryBright else palette.textMuted,
              )
            }
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text =
              buildString {
                append(item.permissions)
                append(" • ")
                append(
                  if (item.isDirectory) "${item.sizeBytes} items" else formatFileSize(item.sizeBytes)
                )
                if (item.lastModifiedMs > 0L) {
                  append(" • ")
                  append(formatTimestamp(item.lastModifiedMs))
                }
              },
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = if (isSelected) palette.primaryBright else palette.textMuted,
          modifier = Modifier.size(18.dp).alpha(reticleAlpha),
        )
      }

      // Animated inline action deck when selected
      AnimatedVisibility(
        visible = isSelected,
        enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(180)),
        exit = shrinkVertically(animationSpec = tween(160)) + fadeOut(animationSpec = tween(140)),
      ) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(palette.background.copy(alpha = 0.55f))
              .padding(horizontal = 10.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RowActionChip(
            label = if (item.isDirectory) "ENTER DIR" else "INSPECT",
            icon =
              if (item.isDirectory) Icons.Filled.FolderOpen else Icons.Filled.Visibility,
            accentColor = palette.primaryBright,
            onClick = onOpen,
            testTag = "open_selected_${item.name}",
          )
          if (item.isDirectory) {
            RowActionChip(
              label = "CD TERM",
              icon = Icons.Filled.Terminal,
              accentColor = palette.primary,
              onClick = onSyncTerminal,
              testTag = "cd_selected_${item.name}",
            )
          }
          Spacer(modifier = Modifier.weight(1f))
          if (inWorkspace) {
            RowActionChip(
              label = "DELETE",
              icon = Icons.Filled.Delete,
              accentColor = palette.danger,
              onClick = onDelete,
              testTag = "delete_file_${item.name}",
            )
          }
        }
      }
    }
  }
}

@Composable
private fun RowActionChip(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  accentColor: androidx.compose.ui.graphics.Color,
  onClick: () -> Unit,
  testTag: String,
) {
  Surface(
    modifier =
      Modifier.minimumInteractiveComponentSize()
        .testTag(testTag)
        .clickable(onClick = onClick),
    color = accentColor.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, accentColor),
    shape = CutCornerShape(4.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = accentColor,
        modifier = Modifier.size(14.dp),
      )
      Spacer(modifier = Modifier.width(5.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = accentColor,
      )
    }
  }
}

@Composable
fun FileInspectorDialog(
  state: InspectedFileState,
  onSave: (String) -> Unit,
  onDismiss: () -> Unit,
  onTextEdit: (previous: String, updated: String) -> Unit = { _, _ -> },
) {
  val palette = LocalEdexPalette.current
  var editedText by remember(state.item.path, state.content) { mutableStateOf(state.content) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 24.dp),
      color = palette.surface,
      border = BorderStroke(1.dp, palette.primaryBright),
      shape = CutCornerShape(10.dp),
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = state.item.name,
              style = MaterialTheme.typography.titleMedium,
              color = palette.primaryBright,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = if (state.isEditable) "READ/WRITE WORKSPACE FILE" else "READ-ONLY SYSTEM FILE",
              style = MaterialTheme.typography.labelSmall,
              color = palette.textMuted,
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("close_inspector_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = stringResource(R.string.cd_close_dialog),
              tint = palette.primary,
            )
          }
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (state.isEditable) {
          OutlinedTextField(
            value = editedText,
            onValueChange = { next ->
              onTextEdit(editedText, next)
              editedText = next
            },
            textStyle = MaterialTheme.typography.bodySmall.copy(color = palette.textPrimary),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.primaryBright,
                unfocusedBorderColor = palette.border,
                focusedContainerColor = palette.background,
                unfocusedContainerColor = palette.background,
              ),
            modifier = Modifier.fillMaxWidth().height(260.dp).testTag("file_editor_field"),
          )
        } else {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(260.dp)
                .background(palette.background, CutCornerShape(4.dp))
                .border(1.dp, palette.gridLine, CutCornerShape(4.dp))
                .verticalScroll(rememberScrollState())
                .padding(10.dp)
          ) {
            Text(
              text = state.content,
              style = MaterialTheme.typography.bodySmall,
              color = palette.textPrimary,
            )
          }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          OutlinedButton(
            onClick = onDismiss,
            border = BorderStroke(1.dp, palette.border),
            shape = CutCornerShape(6.dp),
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Text("CLOSE", color = palette.textPrimary)
          }
          if (state.isEditable) {
            Spacer(modifier = Modifier.width(8.dp))
            Button(
              onClick = { onSave(editedText) },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = palette.primaryBright,
                  contentColor = palette.background,
                ),
              shape = CutCornerShape(6.dp),
              modifier = Modifier.minimumInteractiveComponentSize().testTag("save_file_button"),
            ) {
              Text("SAVE CHANGES")
            }
          }
        }
      }
    }
  }
}

@Composable
fun NewFileEntryDialog(
  onCreate: (name: String, isDirectory: Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalEdexPalette.current
  var name by remember { mutableStateOf("") }
  var isDirectory by remember { mutableStateOf(false) }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      color = palette.surface,
      border = BorderStroke(1.dp, palette.primaryBright),
      shape = CutCornerShape(10.dp),
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = "CREATE WORKSPACE ENTRY",
          style = MaterialTheme.typography.titleMedium,
          color = palette.primaryBright,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { isDirectory = false },
            border = BorderStroke(1.dp, if (!isDirectory) palette.primaryBright else palette.border),
            shape = CutCornerShape(6.dp),
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
          ) {
            Text("FILE", color = if (!isDirectory) palette.primaryBright else palette.textMuted)
          }
          OutlinedButton(
            onClick = { isDirectory = true },
            border = BorderStroke(1.dp, if (isDirectory) palette.primaryBright else palette.border),
            shape = CutCornerShape(6.dp),
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
          ) {
            Text("FOLDER", color = if (isDirectory) palette.primaryBright else palette.textMuted)
          }
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          singleLine = true,
          label = { Text(if (isDirectory) "Folder name" else "File name (e.g. notes.txt)") },
          textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.textPrimary),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = palette.primaryBright,
              unfocusedBorderColor = palette.border,
              focusedLabelColor = palette.primary,
              unfocusedLabelColor = palette.textMuted,
            ),
          modifier = Modifier.fillMaxWidth().testTag("new_entry_name_input"),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          OutlinedButton(
            onClick = onDismiss,
            border = BorderStroke(1.dp, palette.border),
            shape = CutCornerShape(6.dp),
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Text("CANCEL", color = palette.textMuted)
          }
          Spacer(modifier = Modifier.width(8.dp))
          val sanitizedName = name.trim().replace("/", "").replace("\\", "")
          val canCreate = sanitizedName.isNotEmpty() && sanitizedName != "." && sanitizedName != ".."
          Button(
            onClick = { onCreate(name, isDirectory) },
            enabled = canCreate,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = palette.primaryBright,
                contentColor = palette.background,
              ),
            shape = CutCornerShape(6.dp),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("create_entry_confirm"),
          ) {
            Text("CREATE")
          }
        }
      }
    }
  }
}

private fun formatFileSize(bytes: Long): String {
  return when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
  }
}

private val fileDateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.US)

private fun formatTimestamp(millis: Long): String =
  synchronized(fileDateFormat) { fileDateFormat.format(Date(millis)) }
