//
//  RemoteImageServiceTests.swift
//  DoMemoryTests
//

import XCTest
import UIKit
@testable import DoMemory

/// Stands in for the network so these tests never reach one. Also counts
/// requests, which is how the de-duplication test proves its point.
private final class StubURLProtocol: URLProtocol {
    private static let lock = NSLock()
    nonisolated(unsafe) private static var handler: ((URLRequest) -> Result<(HTTPURLResponse, Data), Error>)?
    nonisolated(unsafe) private static var count = 0

    static func install(_ handler: @escaping (URLRequest) -> Result<(HTTPURLResponse, Data), Error>) {
        lock.lock()
        Self.handler = handler
        count = 0
        lock.unlock()
    }

    static var requestCount: Int {
        lock.lock()
        defer { lock.unlock() }
        return count
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.lock.lock()
        Self.count += 1
        let handler = Self.handler
        Self.lock.unlock()

        guard let handler else {
            client?.urlProtocol(self, didFailWithError: URLError(.unsupportedURL))
            return
        }

        switch handler(request) {
        case .success(let (response, data)):
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        case .failure(let error):
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}

@MainActor
final class RemoteImageServiceTests: XCTestCase {
    private let url = URL(string: "https://example.com/season-background.png")!

    private func makeService() -> RemoteImageService {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubURLProtocol.self]
        return RemoteImageService(session: URLSession(configuration: configuration))
    }

    private func pngData(size: CGFloat = 8) -> Data {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: size, height: size))
        let image = renderer.image { context in
            UIColor.orange.setFill()
            context.fill(CGRect(x: 0, y: 0, width: size, height: size))
        }
        return image.pngData()!
    }

    private func response(_ statusCode: Int) -> HTTPURLResponse {
        HTTPURLResponse(url: url, statusCode: statusCode, httpVersion: nil, headerFields: nil)!
    }

    func testSuccessfulLoadReturnsAnImage() async {
        let data = pngData()
        StubURLProtocol.install { [response] _ in .success((response(200), data)) }

        let image = await makeService().image(for: url)
        XCTAssertNotNil(image)
    }

    func testASecondRequestIsServedFromMemoryWithoutHittingTheNetwork() async {
        let data = pngData()
        StubURLProtocol.install { [response] _ in .success((response(200), data)) }
        let service = makeService()

        _ = await service.image(for: url)
        XCTAssertEqual(StubURLProtocol.requestCount, 1)

        // Synchronously available, which is what lets the season card paint in
        // its first frame when the menu's TabView rebuilds it.
        XCTAssertNotNil(service.cachedImage(for: url))
        _ = await service.image(for: url)
        XCTAssertEqual(StubURLProtocol.requestCount, 1, "a cached image must not be fetched again")
    }

    func testConcurrentRequestsForTheSameURLShareOneFetch() async {
        let data = pngData()
        StubURLProtocol.install { [response] _ in .success((response(200), data)) }
        let service = makeService()

        // The season card and the map behind it can ask for the same artwork in
        // the same frame.
        async let first = service.image(for: url)
        async let second = service.image(for: url)
        let images = await [first, second]

        XCTAssertEqual(images.compactMap { $0 }.count, 2)
        XCTAssertEqual(StubURLProtocol.requestCount, 1, "both callers must share one connection")
    }

    func testNotFoundReturnsNil() async {
        StubURLProtocol.install { [response] _ in .success((response(404), Data())) }
        let image = await makeService().image(for: url)
        // A 404 body would otherwise decode to nothing and be cached as a
        // permanent blank.
        XCTAssertNil(image)
    }

    func testTransportFailureReturnsNil() async {
        StubURLProtocol.install { _ in .failure(URLError(.notConnectedToInternet)) }
        let image = await makeService().image(for: url)
        XCTAssertNil(image)
    }

    func testNonImageBodyReturnsNil() async {
        let data = Data("<html>not an image</html>".utf8)
        StubURLProtocol.install { [response] _ in .success((response(200), data)) }
        let image = await makeService().image(for: url)
        XCTAssertNil(image)
    }

    func testAFailedLoadIsNotCached() async {
        StubURLProtocol.install { _ in .failure(URLError(.timedOut)) }
        let service = makeService()

        _ = await service.image(for: url)
        XCTAssertNil(service.cachedImage(for: url))
        // The next season screen the player opens should try again rather than
        // inherit a failure from a moment of bad signal.
        _ = await service.image(for: url)
        XCTAssertEqual(StubURLProtocol.requestCount, 2)
    }
}
