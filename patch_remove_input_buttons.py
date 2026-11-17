#!/usr/bin/env python3
"""移除语音浮窗三条提示 / 输入栏语音+模型按钮，并给加号按钮换主题色（常亮 primary）。"""
import sys
from pathlib import Path

ROOT = Path("/workspace/eta-work")
EDITS = []  # (relpath, old, new, expected_count)


def E(rel, old, new, n=1):
    EDITS.append((rel, old, new, n))


V = "app/src/main/kotlin/io/github/mangi/eta/agent/voice/EtaVoicePanel.kt"
E(V,
  "    onInputChange: (String) -> Unit,\n    onSuggestionClick: (String) -> Unit,\n    onSubmit: () -> Unit,\n",
  "    onInputChange: (String) -> Unit,\n    onSubmit: () -> Unit,\n")
E(V,
  "    onInputChange: (String) -> Unit,\n    onSuggestionClick: (String) -> Unit,\n    keyboardVisible: Boolean,\n",
  "    onInputChange: (String) -> Unit,\n    keyboardVisible: Boolean,\n")
E(V,
  "                onInputChange = onInputChange,\n"
  "                onSuggestionClick = { suggestion ->\n"
  "                    keyboard?.hide()\n"
  "                    onSuggestionClick(suggestion)\n"
  "                },\n"
  "                keyboardVisible = imeBottom > navigationBottom,\n",
  "                onInputChange = onInputChange,\n"
  "                keyboardVisible = imeBottom > navigationBottom,\n")
E(V,
  "        EtaAssistantSuggestions(\n"
  "            onSuggestionClick = onSuggestionClick,\n"
  "            colors = colors,\n"
  "            visible = !hasMessages,\n"
  "        )\n",
  "")

O = "app/src/main/kotlin/io/github/mangi/eta/agent/voice/EtaAssistantOverlayService.kt"
E(O,
  "                        onInputChange = { inputText = it },\n                        onSuggestionClick = ::submitPrompt,\n",
  "                        onInputChange = { inputText = it },\n")

I = "app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentChatInputBar.kt"
E(I,
  "import io.github.mangi.eta.ui.voice.SpeechDictationButton\n"
  "import io.github.mangi.eta.ui.voice.SpeechInputFeedback\n"
  "import io.github.mangi.eta.ui.voice.rememberSpeechInput\n",
  "")
E(I, "import androidx.compose.ui.text.TextRange\n", "")
E(I, "    onModelSelected: (String) -> Unit,\n", "")
E(I,
  "    val dictation = rememberSpeechInput { recognized ->\n"
  "        textFieldState.edit {\n"
  "            val range = selection\n"
  "            replace(range.min, range.max, recognized)\n"
  "            selection = TextRange(range.min + recognized.length)\n"
  "        }\n"
  "    }\n"
  "    LaunchedEffect(isStreaming, isEditingMessage) { dictation.cancel() }\n",
  "")
E(I, "        SpeechInputFeedback(dictation)\n", "")
E(I,
  "                        SpeechDictationButton(dictation, enabled = !isStreaming)\n"
  "\n"
  "                        Spacer(modifier = Modifier.width(2.dp))\n"
  "\n"
  "                        AgentModelPickerButton(\n"
  "                            state = modelPickerState,\n"
  "                            isStreaming = isStreaming,\n"
  "                            popupAnchorTopPx = inputContainerTopPx,\n"
  "                            popupMaxHeight = thinkingPopupMaxHeight,\n"
  "                            onModelSelected = onModelSelected,\n"
  "                        )\n"
  "\n",
  "")
E(I,
  "                                    if (canSend) {\n"
  "                                        dictation.cancel()\n"
  "                                        val submittedText = textFieldState.text.toString()\n",
  "                                    if (canSend) {\n"
  "                                        val submittedText = textFieldState.text.toString()\n")

B = "app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentChatBody.kt"
E(B, "    onModelSelected: (String) -> Unit,\n", "", 3)
E(B,
  "            canCompactContext = canCompactContext,\n"
  "            onModelSelected = onModelSelected,\n"
  "            onStop = onStop,\n",
  "            canCompactContext = canCompactContext,\n"
  "            onStop = onStop,\n")
E(B,
  "                canCompactContext = canCompactContext,\n"
  "                onModelSelected = onModelSelected,\n"
  "                onStop = onStop,\n",
  "                canCompactContext = canCompactContext,\n"
  "                onStop = onStop,\n", 2)

H = "app/src/main/kotlin/io/github/mangi/eta/ui/screens/home/AgentHomeScreen.kt"
E(H, "            onModelSelected = { onAction(AgentHomeAction.ModelSelected(it)) },\n", "")

F = "app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentChatFileAttachments.kt"
E(F,
  "                contentDescription = stringResource(R.string.ui_add_attachment_dba9e8),\n"
  "                modifier = Modifier.size(ChatInputActionIconSize),\n"
  "                tint = MiuixTheme.colorScheme.onSurface,\n",
  "                contentDescription = stringResource(R.string.ui_add_attachment_dba9e8),\n"
  "                modifier = Modifier.size(ChatInputActionIconSize),\n"
  "                tint = MiuixTheme.colorScheme.primary,\n")

G = "app/build.gradle.kts"
E(G, "        versionCode = 2026100701\n", "        versionCode = 2026100702\n")

# 先整体校验，全部命中才落盘。
plans = {}
for rel, old, new, n in EDITS:
    p = ROOT / rel
    plans.setdefault(rel, []).append((old, new, n))
fail = False
for rel, items in plans.items():
    text = (ROOT / rel).read_text(encoding="utf-8")
    for old, new, n in items:
        got = text.count(old)
        if got != n:
            print(f"[FAIL] {rel}: expect {n}, got {got} for {old.strip()[:60]!r}")
            fail = True
        text = text.replace(old, new, n)
if fail:
    sys.exit(1)

for rel, items in plans.items():
    p = ROOT / rel
    text = p.read_text(encoding="utf-8")
    for old, new, n in items:
        text = text.replace(old, new, n)
    p.write_text(text, encoding="utf-8")
    print(f"[OK] {rel}")

(ROOT / "app/src/main/kotlin/io/github/mangi/eta/agent/voice/EtaAssistantSuggestions.kt").unlink()
print("[OK] deleted EtaAssistantSuggestions.kt")
