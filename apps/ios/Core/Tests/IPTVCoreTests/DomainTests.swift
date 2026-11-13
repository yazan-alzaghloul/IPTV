import XCTest

@testable import IPTVCore

final class DomainTests: XCTestCase {
  func item(_ id: String = "1", _ kind: ContentKind = .movie) -> CatalogItem {
    CatalogItem(id: id, name: "Title \(id)", kind: kind)
  }
  func testNormalizePreservesPathSchemeAndPortOverride() throws {
    let c = try Credentials.normalize(
      host: "http://example.com/service", port: "8443", scheme: "https", username: " user ",
      password: "pass")
    XCTAssertEqual(c.baseURL.absoluteString, "http://example.com:8443/service/")
    XCTAssertEqual(c.username, "user")
    XCTAssertFalse(c.description.contains("pass"))
  }
  func testRejectsInvalidInputs() {
    for host in [
      "", "ftp://example.com", "https://u:p@example.com", "https://example.com?a=1",
      "https://example.com/#x", "https://example.com/player_api.php",
      "https://example.com/list.m3u",
    ] {
      XCTAssertThrowsError(
        try Credentials.normalize(
          host: host, port: "", scheme: "https", username: "u", password: "p"), host)
    }
    XCTAssertThrowsError(
      try Credentials.normalize(
        host: "example.com", port: "65536", scheme: "https", username: "u", password: "p"))
  }
  func testStreamSegmentsAndContainers() throws {
    let c = Credentials(
      baseURL: URL(string: "https://example.com/path/")!, username: "u/a", password: "p?#")
    let url = try XtreamRepository().streamURL(c, item: item())
    XCTAssertEqual(url.absoluteString, "https://example.com/path/movie/u%2Fa/p%3F%23/1.mp4")
    XCTAssertTrue(
      try XtreamRepository().streamURL(c, item: item("2", .live), transportStream: true).path
        .hasSuffix("2.ts"))
    var unsafe = item()
    unsafe.container = "../../file"
    XCTAssertTrue(try XtreamRepository().streamURL(c, item: unsafe).path.hasSuffix("1.mp4"))
  }
  func testCompletionAndLiveResume() {
    var library = LibrarySnapshot()
    let movie = item()
    library.savePosition(movie, position: 94, duration: 100, completionPercent: 95)
    XCTAssertEqual(library.position(movie), 94)
    library.savePosition(movie, position: 95, duration: 100, completionPercent: 95)
    XCTAssertEqual(library.position(movie), 0)
    XCTAssertTrue(library.watched.contains(movie.key))
    let live = item("2", .live)
    library.savePosition(live, position: 40, duration: 100, completionPercent: 95)
    XCTAssertEqual(library.position(live), 0)
  }
  func testHistorySearchBoundsAndWatchedReset() {
    var library = LibrarySnapshot()
    for i in 0..<110 {
      library.savePosition(item(String(i)), position: 3, duration: 100, completionPercent: 95)
      library.rememberSearch("search \(i)")
    }
    XCTAssertEqual(library.history.count, 100)
    XCTAssertEqual(library.searches.count, 10)
    library.rememberSearch("SEARCH 109")
    XCTAssertEqual(library.searches.count, 10)
    let movie = item("109")
    library.mark([movie], watched: true)
    XCTAssertEqual(library.position(movie), 0)
    library.mark([movie], watched: false)
    XCTAssertFalse(library.watched.contains(movie.key))
    XCTAssertEqual(library.position(movie), 0)
    library.toggle(movie)
    library.toggle(movie)
    XCTAssertTrue(library.favorites.isEmpty)
  }
  func testSeriesContinuationUsesExactParentAndNextUnwatched() {
    var a = item("e1", .series)
    a.parentSeriesID = "s1"
    var b = item("e2", .series)
    b.parentSeriesID = "s1"
    let episodes = [Episode(item: b, season: 2, number: 1), Episode(item: a, season: 1, number: 1)]
    var library = LibrarySnapshot()
    library.savePosition(a, position: 40, duration: 100, completionPercent: 95)
    XCTAssertEqual(
      MediaRules.continuation(
        seriesID: "s1", episodes: episodes, history: library.history, watched: [])?.item.id, "e1")
    library.savePosition(a, position: 100, duration: 100, completionPercent: 95)
    XCTAssertEqual(
      MediaRules.continuation(
        seriesID: "s1", episodes: episodes, history: library.history, watched: library.watched)?
        .item.id, "e2")
    XCTAssertNil(
      MediaRules.continuation(
        seriesID: "s1", episodes: episodes, history: library.history, watched: [a.key, b.key]))
  }
  func testRetryBudgetDoesNotResetOnSuccess() {
    var retry = RetryBudget()
    XCTAssertEqual(retry.nextDelay(), 1)
    XCTAssertEqual(retry.nextDelay(), 2)
    XCTAssertNil(retry.nextDelay())
    retry.reset()
    XCTAssertEqual(retry.nextDelay(), 1)
  }
  func testResumeSurvivesHistoryEvictionAndClearResetsRecentProgress() {
    var library = LibrarySnapshot()
    let old = item("old")
    library.savePosition(old, position: 32, duration: 100, completionPercent: 95)
    for index in 0..<101 {
      library.savePosition(item(String(index)), position: 10, duration: 100, completionPercent: 95)
    }
    XCTAssertFalse(library.history.contains { $0.item.key == old.key })
    XCTAssertEqual(library.position(old), 32)
    let recent = item("100")
    library.clearHistory()
    XCTAssertEqual(library.position(recent), 0)
    library.removeHistory(old)
    XCTAssertEqual(library.position(old), 0)
  }
  func testMetadataAndTrailerSafety() {
    XCTAssertEqual(MediaRules.duration("01:02:03"), 3723)
    XCTAssertNil(MediaRules.duration("01:99"))
    XCTAssertNil(MediaRules.duration("garbage"))
    XCTAssertNotNil(MediaRules.trailer("dQw4w9WgXcQ"))
    XCTAssertNil(MediaRules.trailer("javascript:alert(1)"))
    XCTAssertNil(MediaRules.trailer("https://u:p@example.com"))
  }
  func testAppearanceValidationAndCrossPlatformSchema() throws {
    let a = Appearance()
    XCTAssertEqual(try Appearance.imported(JSONEncoder().encode(a)), a)
    var bad = a
    bad.fontScale = 5
    XCTAssertThrowsError(try Appearance.imported(JSONEncoder().encode(bad)))
    XCTAssertThrowsError(try Appearance.imported(Data(repeating: 1, count: 32769)))
  }
  func testRecommendationsRequireActualSignalsAndExcludeKnown() {
    var a = item()
    a.genre = "Drama"
    var b = item("2")
    b.genre = "drama"
    XCTAssertTrue(
      MediaRules.recommendations(candidates: [a, b], history: [], favorites: []).isEmpty)
    XCTAssertEqual(
      MediaRules.recommendations(candidates: [a, b], history: [], favorites: [a]).map(\.id), ["2"])
  }
}
