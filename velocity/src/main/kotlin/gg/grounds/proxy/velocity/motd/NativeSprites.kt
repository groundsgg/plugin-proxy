package gg.grounds.proxy.velocity.motd

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ObjectComponent
import net.kyori.adventure.text.`object`.SpriteObjectContents

/** Omit unsupported atlas objects without discarding neighboring children or inherited style. */
internal fun projectNativeSprites(node: Component, supported: Boolean): Component {
    if (supported) return node
    val children = node.children().map { projectNativeSprites(it, false) }
    return if (node is ObjectComponent && node.contents() is SpriteObjectContents)
        Component.empty().style(node.style()).children(children)
    else node.children(children)
}
