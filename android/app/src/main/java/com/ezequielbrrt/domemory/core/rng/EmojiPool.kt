package com.ezequielbrrt.domemory.core.rng

/**
 * The 48 curated, visually distinct emoji used by every generated board (spec 6.2).
 *
 * **Order is part of the contract** — seeded selection depends on it. Only ever
 * append; never reorder or remove.
 */
object EmojiPool {
    val all: List<String> = listOf(
        "😀", "😎", "🥳", "😍", "🤓", "😴", "🤖", "👻", "💩", "🐶",
        "🐱", "🦊", "🐻", "🐼", "🐸", "🐵", "🦁", "🐯", "🦄", "🐝",
        "🐢", "🐙", "🦋", "🌵", "🌸", "🍄", "🍎", "🍌", "🍉", "🍓",
        "🍕", "🍔", "🌮", "🍦", "🎈", "⚽️", "🚀", "⭐️", "🌈", "🔥",
        "💎", "🎸", "🎲", "🎯", "👑", "🎁", "❤️", "⚡️",
    )

    val size: Int get() = all.size
}
