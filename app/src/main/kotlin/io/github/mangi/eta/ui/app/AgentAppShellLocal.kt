package io.github.mangi.eta.ui.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.components.CharacterAvatar
import io.github.mangi.eta.ui.components.RoleplayChatUi
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// fork 本地新增：顶栏标题渐隐与角色身份条。

/** 顶栏会话标题的宽度上限：按当前字号实测出的 11 个中文字符宽度。 */
private const val TOP_BAR_TITLE_CJK_WIDTH = 11

/** 会话标题透明度渐显时长：够看出「淡出来」，又不至于让人等标题。 */
private const val TOP_BAR_TITLE_FADE_MS = 320

/**
 * 会话标题的透明度渐显。
 *
 * 标题由无到有、或换成新名字时从 0 淡到 1；空标题直接把透明度压到 0，
 * 这样「发完消息、命名还没落地」的那一瞬间不会闪出旧的占位文案。
 */
@Composable
internal fun rememberConversationTitleFade(title: String?): Float {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(title) {
        if (title.isNullOrBlank()) {
            alpha.snapTo(0f)
        } else {
            alpha.snapTo(0f)
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = TOP_BAR_TITLE_FADE_MS, easing = LinearOutSlowInEasing),
            )
        }
    }
    return alpha.value
}

@Composable
internal fun CharacterTopBarIdentity(character: RoleplayChatUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CharacterAvatar(
            name = character.characterName,
            avatarPath = character.avatarPath,
            size = 32.dp,
            shape = CircleShape,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = character.characterName,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
    }
}
