package io.github.mangi.eta.ui.components;

import com.itos.coui.search.CouiSearchBarState;

/**
 * COUI 的 {@code CouiSearchBarState.updateTarget} 是 internal（字节码名 {@code updateTarget$coui}），
 * Kotlin 侧既拿不到 internal 可见性，也没法在源码里写出带 {@code $} 的名字。
 * 这里用 Java 中转一次：Java 看到的就是普通 public 方法，R8 也能正确重映射。
 */
public final class CouiSearchBarStateBridge {

    private CouiSearchBarStateBridge() {
    }

    /** 让 COUI SearchBar 收起（expanded=false）；内部会顺带释放焦点并收起键盘。 */
    public static void collapse(CouiSearchBarState state) {
        if (state == null) {
            return;
        }
        state.updateTarget$coui(false);
    }
}
