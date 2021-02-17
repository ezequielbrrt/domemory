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
