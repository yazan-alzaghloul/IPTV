import XCTest

@testable import IPTVCore

final class PersistenceTests: XCTestCase {
  private func directory() throws -> URL {
    let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
    return url
  }
  func testCatalogPagingFacetsLiteralSearchAndAccountIsolation() async throws {
    let root = try directory()
    defer { try? FileManager.default.removeItem(at: root) }
    let db = try CatalogStore(url: root.appendingPathComponent("catalog.sqlite"))
    let items = (0..<120).map { n in
      var item = CatalogItem(
        id: String(n), name: n == 1 ? "100%_Title" : "Title \(n)", kind: .movie)
      item.added = Int64(n)
      item.genre = "Drama, Family"
      item.year = "2026"
      item.language = "Arabic"
      item.categoryID = "g"
      item.rating = "8"
      return item
    }
    try await db.replace(
      scope: "a", kind: .movie, items: items, categories: [Category(id: "g", name: "Movies")])
    let page = try await db.page(scope: "a", kind: .movie, offset: 48)
    XCTAssertEqual(page.total, 120)
    XCTAssertEqual(page.items.count, 48)
    XCTAssertEqual(page.items.first?.id, "71")
    var filter = CatalogFilter()
    filter.query = "%_"
    let matches = try await db.page(scope: "a", kind: .movie, filter: filter)
    XCTAssertEqual(matches.items.map(\.id), ["1"])
    let isolated = try await db.page(scope: "other", kind: .movie)
    XCTAssertEqual(isolated.total, 0)
    let facets = try await db.facets(scope: "a", kind: .movie)
    XCTAssertEqual(facets.years, ["2026"])
    XCTAssertTrue(facets.hasRatings)
    XCTAssertEqual(facets.genres, ["drama", "family"])
    let found = try await db.find(scope: "a", kind: .movie, id: "1")
    XCTAssertEqual(found?.name, "100%_Title")
    try await db.clear(scope: "a")
    let cleared = try await db.page(scope: "a", kind: .movie)
    XCTAssertEqual(cleared.total, 0)
  }
  func testLibrarySurvivesReopenAndSignOutRemovesData() async throws {
    let root = try directory()
    defer { try? FileManager.default.removeItem(at: root) }
    let store = try LibraryStore(directory: root)
    let scope = String(repeating: "a", count: 64)
    var snapshot = LibrarySnapshot()
    snapshot.rememberSearch("test")
    snapshot.toggle(CatalogItem(id: "1", name: "Movie", kind: .movie))
    try await store.save(snapshot, scope: scope)
    let other = try LibraryStore(directory: root)
    let loaded = try await other.load(scope: scope)
    XCTAssertEqual(loaded.favorites.count, 1)
    let empty = try await other.load(scope: String(repeating: "b", count: 64))
    XCTAssertTrue(empty.favorites.isEmpty)
    try await store.clear(scope: scope)
    let cleared = try await store.load(scope: scope)
    XCTAssertTrue(cleared.favorites.isEmpty)
    do {
      _ = try await store.load(scope: "../unsafe")
      XCTFail("Traversal accepted")
    } catch {}
  }
}
