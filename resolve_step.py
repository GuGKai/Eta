import io, sys

def resolve(path, pairs):
    src = io.open(path, encoding="utf-8").read()
    for old, new in pairs:
        assert old in src, (path, old[:40])
        src = src.replace(old, new, 1)
    assert "<<<<<<<" not in src, path
    io.open(path, "w", encoding="utf-8").write(src)
    print("resolved", path)

resolve(
    "app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentChatBody.kt",
    [(
"""<<<<<<< HEAD
                top = 16.dp,
                bottom = bottomInset + 16.dp,
=======
                // 列表视口铺满整屏，顶栏高度在这里做内边距，正文才能滑到顶栏毛玻璃下方。
                top = topInset + 14.dp,
                bottom = bottomInset + 14.dp,
>>>>>>> 1150ce1 (feat(chat): 会话页顶栏统一为设置页的毛玻璃样式)
""",
"""                // 列表视口铺满整屏，顶栏高度在这里做内边距，正文才能滑到顶栏毛玻璃下方。
                top = topInset + 14.dp,
                bottom = bottomInset + 14.dp,
""")],
)
