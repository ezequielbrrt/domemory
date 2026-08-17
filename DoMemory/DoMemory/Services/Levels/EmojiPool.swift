//
//  EmojiPool.swift
//  DoMemory
//
//  Shared emoji pool for procedurally generated boards (Daily Challenge,
//  Levels).
//

/// Curated, visually distinct emoji pool. Order is fixed so seeded
/// selection is reproducible across app versions; only append, never reorder.
enum EmojiPool {
    static let all: [String] = [
        "😀", "😎", "🥳", "😍", "🤓", "😴", "🤖", "👻", "💩", "🐶",
        "🐱", "🦊", "🐻", "🐼", "🐸", "🐵", "🦁", "🐯", "🦄", "🐝",
        "🐢", "🐙", "🦋", "🌵", "🌸", "🍄", "🍎", "🍌", "🍉", "🍓",
        "🍕", "🍔", "🌮", "🍦", "🎈", "⚽️", "🚀", "⭐️", "🌈", "🔥",
        "💎", "🎸", "🎲", "🎯", "👑", "🎁", "❤️", "⚡️"
    ]
}
