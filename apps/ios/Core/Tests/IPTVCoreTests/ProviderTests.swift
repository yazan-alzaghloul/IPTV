import XCTest

@testable import IPTVCore

private final class FixtureURLProtocol: URLProtocol {
  static let lock = NSLock()
  static var handler: ((URLRequest) throws -> (Int, String))?
  override class func canInit(with request: URLRequest) -> Bool { true }
  override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
  override func startLoading() {
    do {
      Self.lock.lock()
      let handler = Self.handler
      Self.lock.unlock()
      let (code, body) = try XCTUnwrap(handler)(request)
      let response = HTTPURLResponse(
        url: request.url!, statusCode: code, httpVersion: "HTTP/1.1",
        headerFields: ["Content-Type": "application/json"])!
      client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
      client?.urlProtocol(self, didLoad: Data(body.utf8))
      client?.urlProtocolDidFinishLoading(self)
    } catch { client?.urlProtocol(self, didFailWithError: error) }
  }
  override func stopLoading() {}
}
final class ProviderTests: XCTestCase {
  let credentials = Credentials(
    baseURL: URL(string: "https://fixture.invalid/base/")!, username: "user +&", password: "p?/&#")
  override func tearDown() {
    FixtureURLProtocol.lock.lock()
    FixtureURLProtocol.handler = nil
    FixtureURLProtocol.lock.unlock()
  }
  func repository(_ handler: @escaping (URLRequest) throws -> (Int, String)) -> XtreamRepository {
    FixtureURLProtocol.lock.lock()
    FixtureURLProtocol.handler = handler
    FixtureURLProtocol.lock.unlock()
    let config = URLSessionConfiguration.ephemeral
    config.protocolClasses = [FixtureURLProtocol.self]
    return XtreamRepository(session: URLSession(configuration: config))
  }
  func testValidationEncodesQueryAndPreservesBasePath() async throws {
    let repo = repository { request in
      let parts = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)!
      XCTAssertEqual(parts.path, "/base/player_api.php")
      XCTAssertTrue(
        parts.percentEncodedQuery?.contains("%2B") == true,
        "PHP form query decoding must preserve literal plus signs.")
      XCTAssertEqual(parts.queryItems?.first { $0.name == "username" }?.value, "user +&")
      XCTAssertEqual(parts.queryItems?.first { $0.name == "password" }?.value, "p?/&#")
      return (200, #"{"user_info":{"auth":1,"status":"Active","exp_date":null}}"#)
    }
    try await repo.validate(credentials)
  }
  func testExpiredDisabledAndDeniedAccounts() async {
    for body in [
      #"{"user_info":{"auth":0}}"#, #"{"user_info":{"auth":1,"status":"Disabled"}}"#,
      #"{"user_info":{"auth":1,"status":"Active","exp_date":"1"}}"#,
    ] {
      let repo = repository { _ in (200, body) }
      do {
        try await repo.validate(credentials)
        XCTFail("Invalid account accepted")
      } catch { XCTAssertTrue(error is IPTVError) }
    }
  }
  func testHTTPAndMalformedFailuresAreSanitized() async {
    for (status, body) in [
      (403, "secret"), (404, "secret"), (500, "secret"), (200, "<html>secret</html>"),
    ] {
      let repo = repository { _ in (status, body) }
      do {
        try await repo.validate(credentials)
        XCTFail("Failure accepted")
      } catch {
        XCTAssertFalse(error.localizedDescription.contains("secret"))
        XCTAssertFalse(error.localizedDescription.contains(credentials.password))
      }
    }
  }
  func testCatalogToleratesNumericNullFieldsAndDeduplicates() async throws {
    let repo = repository { _ in
      (
        200,
        #"[{"stream_id":7,"name":"Movie","category_id":2,"rating":"8.2","releaseDate":"2026-04-01","stream_icon":null},{"stream_id":"7","name":"Duplicate"},{"name":"Missing ID"}]"#
      )
    }
    let items = try await repo.catalog(credentials, kind: .movie)
    XCTAssertEqual(items.count, 1)
    XCTAssertEqual(items[0].id, "7")
    XCTAssertEqual(items[0].year, "2026")
    XCTAssertEqual(items[0].image, "")
  }
  func testEpisodeParsingKeepsParentAndSeasonOrder() async throws {
    let repo = repository { _ in
      (
        200,
        #"{"info":{"plot":"Plot","youtube_trailer":"dQw4w9WgXcQ"},"episodes":{"2":[{"id":22,"episode_num":1,"title":"Later","info":{"duration":"00:30:00"}}],"1":[{"id":11,"episode_num":1,"title":"First","container_extension":"mp4"}]}}"#
      )
    }
    let details = try await repo.details(
      credentials, item: CatalogItem(id: "s", name: "Show", kind: .series))
    XCTAssertEqual(details.episodes.map(\.season), [1, 2])
    XCTAssertEqual(details.episodes[0].item.parentSeriesID, "s")
    XCTAssertEqual(details.episodes[1].duration, 1800)
    XCTAssertNotNil(details.trailer)
  }
  func testGuideDecodesBase64AndRejectsInvalidIntervals() async throws {
    let repo = repository { _ in
      (
        200,
        #"{"epg_listings":[{"title":"TmV3cw==","description":"UGxvdA==","start_timestamp":100,"stop_timestamp":200},{"title":"Plain news!","start_timestamp":300,"end_timestamp":400},{"title":"Invalid","start_timestamp":10,"stop_timestamp":1}]}"#
      )
    }
    let guide = try await repo.guide(credentials, id: "1")
    XCTAssertEqual(guide.map(\.title), ["News", "Plain news!"])
    XCTAssertEqual(guide[0].overview, "Plot")
  }
  func testNetworkFailureDoesNotExposeCredentialURL() async {
    let repo = repository { request in
      throw URLError(.cannotConnectToHost, userInfo: [NSURLErrorFailingURLErrorKey: request.url!])
    }
    do {
      try await repo.validate(credentials)
      XCTFail("Network failure accepted")
    } catch {
      XCTAssertFalse(error.localizedDescription.contains("fixture.invalid"))
      XCTAssertFalse(error.localizedDescription.contains(credentials.password))
    }
  }
}
