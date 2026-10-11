//
//  DoMemoryTests.swift
//  DoMemoryTests
//
//  Created by Ezequiel Barreto on 16/02/21.
//

import XCTest
@testable import DoMemory

class DoMemoryTests: XCTestCase {

    override func setUpWithError() throws {
        // Put setup code here. This method is called before the invocation of each test method in the class.
    }

    override func tearDownWithError() throws {
        // Put teardown code here. This method is called after the invocation of each test method in the class.
    }

    @MainActor
    func testExample() throws {
        // This is an example of a functional test case.
        // Use XCTAssert and related functions to verify your tests produce the correct results.
        let memorizeVieModel = MemorizeViewModel(memorama: Memorama(id: "1",
                                                                    name: "test",
                                                                    category: "one",
                                                                    difficulty: "easy",
                                                                    description: "test",
                                                                    publishedDate: "hoy",
                                                                    items: [],
                                                                    itemType: "",
                                                                    isDoubleItem: true))
        XCTAssertTrue(memorizeVieModel.cards.count == 0)
        
    }

}

// MARK: - Prototype snapshots

import SwiftUI

/// Renders the gameplay-prototype components to PNGs for a visual check,
/// only when `SNAPSHOT_DIR` is set in the test runner's environment
/// (`TEST_RUNNER_SNAPSHOT_DIR=… xcodebuild test`). Silent otherwise.
final class GameplayPrototypeSnapshotTests: XCTestCase {
    @MainActor
    func testRenderPrototypeComponents() throws {
        guard let dir = ProcessInfo.processInfo.environment["SNAPSHOT_DIR"] else {
            throw XCTSkip("SNAPSHOT_DIR not set")
        }
        try FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)

        func write<V: View>(_ name: String, _ view: V, width: CGFloat = 393) throws {
            let renderer = ImageRenderer(content: view.frame(width: width).background(Color.appBackground))
            renderer.scale = 3
            let image = try XCTUnwrap(renderer.uiImage, name)
            let data = try XCTUnwrap(image.pngData(), name)
            try data.write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(name).png"))
        }

        try write("streak-meters", VStack(spacing: 14) {
            StreakMeter(streak: 0, banner: nil, showsMilestones: true)
            StreakMeter(streak: 2, banner: nil, showsMilestones: true)
            StreakMeter(streak: 3, banner: .charged(.peek), showsMilestones: true)
            StreakMeter(streak: 5, banner: .charged(.freeze), showsMilestones: true)
            StreakMeter(streak: 7, banner: .charged(.revealPair), showsMilestones: true)
            StreakMeter(streak: 9, banner: nil, showsMilestones: true)
            StreakMeter(streak: 3, banner: .streak(3), showsMilestones: false)
        }.padding(16))

        try write("power-up-bar", VStack(spacing: 14) {
            PowerUpBar(balance: 12, onUse: { _ in })
            PowerUpBar(balance: 0, isCharged: { $0 == .peek }, onUse: { _ in })
            PowerUpBar(balance: 4, isCharged: { $0 == .peek || $0 == .freeze }, onUse: { _ in })
        }.padding(.vertical, 16))

        var game = MemoryGame<String>(numbersOfPairsOfCards: 6) { ["🎃", "👻", "🦇", "🕷️", "🍬", "🌙"][$0] }
        game.freezeCards(count: 1)
        game.placeBombs(count: 1)
        game.lockCards(count: 1, forMatches: 2)
        game.choose(card: game.cards.first { !$0.hasModifier }!)
        let plain = game.cards.filter { !$0.hasModifier && !$0.isFaceUp }
        let revealedID = plain[0].id
        let pins = [plain[1].id: 0, plain[2].id: 1, plain[3].id: 2]
        try write("board-blockers", LazyVGrid(columns: Array(repeating: GridItem(.fixed(80)), count: 4), spacing: 10) {
            ForEach(game.cards) { card in
                CardView(card: card, shouldShowPie: false, isRevealed: card.id == revealedID, pinSlot: pins[card.id])
                    .frame(width: 80, height: 110)
            }
        }.padding(16))

        var faceUp = MemoryGame<String>(numbersOfPairsOfCards: 1) { _ in "🎃" }.cards[0]
        faceUp.isFaceUp = true
        // The renderer can't draw a Lottie view and captures the end of an
        // animation, so the burst is left out and Reduce Motion holds the
        // numbers still: this checks their typography, not their motion.
        try write("card-juice", HStack(spacing: 24) {
            CardView(card: faceUp, shouldShowPie: false).frame(width: 80, height: 110)
                .overlay { FloatingNumber(text: "×3", color: CardView.burstColor(tier: 2), rise: 40, holdsStill: true) }
            CardView(card: faceUp, shouldShowPie: false).frame(width: 80, height: 110)
                .overlay { FloatingNumber(text: "−10 s", color: Color.secundaryColor, rise: 40, holdsStill: true) }
            CardView(card: faceUp, shouldShowPie: false).frame(width: 80, height: 110)
                .overlay { FloatingNumber(text: "+5 s", color: CardView.burstColor(tier: 3), rise: 40, holdsStill: true) }
        }.padding(30), width: 360)

        try write("power-up-bar-moves", PowerUpBar(balance: 6, isCharged: { $0 == .peek }, powerUps: LevelPowerUp.available(for: .moves(10)), onUse: { _ in }).padding(.vertical, 16))
    }
}
