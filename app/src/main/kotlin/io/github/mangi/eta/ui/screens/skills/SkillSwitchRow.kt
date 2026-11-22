package io.github.mangi.eta.ui.screens.skills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Item
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor
import com.itos.coui.shape.CouiShape
import com.itos.coui.switch.CouiSwitchDefaults
import com.itos.coui.switch.Switch as CouiSwitch
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import com.itos.coui.theme.CouiTheme
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.EtaTopLayer
import io.github.mangi.eta.ui.components.ItemDescriptionDialog
import io.github.mangi.eta.ui.components.coui.CouiIcon
import io.github.mangi.eta.ui.components.currentGlassOverlayBackdrop
import io.github.mangi.eta.ui.model.SkillItemUi
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.GlassTransformPopup
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.icon.glass.Info
import top.yukonga.miuix.kmp.icon.glass.More
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SkillSwitchRow(
    skill: SkillItemUi,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
    busy: Boolean = false,
) {
    var showDescription by remember(skill.id) { mutableStateOf(false) }
    val description = skill.description.ifBlank { stringResource(R.string.skills_no_description) }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            val dark = CouiTheme.isDark
            val colors = CouiPageDefaults.themeColors(dark)
            val destructiveColor = CouiDialogDefaults.themeColors(dark).destructive
            var showMenu by remember { mutableStateOf(false) }
            val anchor = rememberCouiMenuAnchor()
            val viewDescText = stringResource(R.string.ui_view_description)
            val deleteText = stringResource(R.string.ui_delete_3755f5)
            val menuItems = remember(viewDescText, deleteText, onDelete, enabled, destructiveColor) {
                buildList {
                    add(CouiMenuItem(value = 0, text = viewDescText))
                    if (onDelete != null) {
                        add(
                            CouiMenuItem(
                                value = 1,
                                text = deleteText,
                                enabled = enabled,
                                iconTint = destructiveColor,
                                dividerBefore = true,
                            ),
                        )
                    }
                }
            }
            Item(
                enabled = enabled,
                onClick = { onToggle(!skill.enabled) },
                title = {
                    CouiText(
                        text = skill.name,
                        kind = CouiTextKind.ItemTitle,
                        color = if (enabled) colors.title else colors.disabled,
                    )
                },
                summary = {
                    CouiText(
                        text = description,
                        kind = CouiTextKind.ItemSummary,
                        color = if (enabled) colors.summary else colors.disabled,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailing = {
                    Box {
                        val moreSource = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                // 圆形按压反馈：默认 indication 按 36dp 方形裁剪出矩形框，
                                // 这里显式 clip 成圆并走 COUI 按钮反馈，与顶栏圆钮保持一致。
                                .clip(CouiShape(18.dp, 2f))
                                .couiButtonFeedback(dark, moreSource, true)
                                .couiMenuAnchor(anchor)
                                .clickable(
                                    interactionSource = moreSource,
                                    indication = null,
                                    role = Role.Button,
                                ) {
                                    anchor.open { showMenu = true }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            CouiIcon(
                                imageVector = CouiIcons.Outlined.More,
                                contentDescription = stringResource(R.string.skills_more_named, skill.name),
                                modifier = Modifier.size(20.dp),
                                tint = colors.summary,
                            )
                        }
                        Menu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            items = menuItems,
                            onItemClick = { item ->
                                showMenu = false
                                when (item.value) {
                                    0 -> showDescription = true
                                    1 -> if (enabled) onDelete?.invoke()
                                }
                            },
                            anchor = anchor,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    CouiSwitch(
                        checked = skill.enabled,
                        onCheckedChange = onToggle,
                        dark = dark,
                        colors = CouiSwitchDefaults.colors(colors.accent, dark),
                        enabled = enabled,
                        // 写入中只在这一行的开关上转一圈，避免整页因为 busy 而变色闪烁。
                        loading = busy,
                    )
                },
            )
        }
        AppearanceUiStyle.MIUIX -> {
            var showMenu by remember { mutableStateOf(false) }
            val moreAnchor = rememberGlassPopupAnchor()
            val overlayBackdrop = currentGlassOverlayBackdrop()
            BasicComponent(
                enabled = enabled,
                onClick = { onToggle(!skill.enabled) },
                endActions = {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .glassPopupAnchor(
                                anchor = moreAnchor,
                                cornerRadius = 16.dp,
                            )
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { showMenu = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Glass.More,
                            contentDescription = stringResource(R.string.skills_more_named, skill.name),
                            modifier = Modifier
                                .size(20.dp)
                                .glassPopupAnchorContent(moreAnchor),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = skill.enabled,
                        onCheckedChange = onToggle,
                        enabled = enabled,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                },
            ) {
                Text(
                    text = skill.name,
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                )
                Text(
                    text = description,
                    style = MiuixTheme.textStyles.body2,
                    color = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            EtaTopLayer {
                GlassTransformPopup(
                    show = showMenu,
                    onDismissRequest = { showMenu = false },
                    anchor = moreAnchor,
                    backdrop = overlayBackdrop,
                    anchorContent = {
                        Icon(
                            imageVector = MiuixIcons.Glass.More,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    },
                ) {
                    GlassPopupItem(
                        text = stringResource(R.string.ui_view_description),
                        icon = MiuixIcons.Glass.Info,
                        onClick = {
                            showMenu = false
                            showDescription = true
                        },
                    )
                    if (onDelete != null) {
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                horizontal = GlassPopupDefaults.DividerPaddingHorizontal,
                                vertical = GlassPopupDefaults.DividerPaddingVertical,
                            ),
                            thickness = GlassPopupDefaults.DividerThickness,
                        )
                        GlassPopupItem(
                            text = stringResource(R.string.ui_delete_3755f5),
                            icon = MiuixIcons.Glass.Delete,
                            enabled = enabled,
                            contentColor = MiuixTheme.colorScheme.error,
                            onClick = {
                                showMenu = false
                                if (enabled) onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
    ItemDescriptionDialog(
        show = showDescription,
        title = skill.name,
        description = description,
        onDismiss = { showDescription = false },
    )
}
