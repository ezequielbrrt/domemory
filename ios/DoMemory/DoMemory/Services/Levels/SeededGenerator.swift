//
//  SeededGenerator.swift
//  DoMemory
//
//  Shared deterministic RNG used by any feature that needs the same
//  procedural output for the same seed on every device (Daily Challenge,
//  Levels).
//

import Foundation

/// Small deterministic RNG (SplitMix64) seeded from a string.
struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64

    init(seed: String) {
        // FNV-1a hash of the seed string → 64-bit state.
        var hash: UInt64 = 1_469_598_103_934_665_603
        for byte in seed.utf8 {
            hash ^= UInt64(byte)
            hash = hash &* 1_099_511_628_211
        }
        state = hash
    }

    mutating func next() -> UInt64 {
        state = state &+ 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}
