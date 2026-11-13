import IPTVCore
import XCTest

@testable import IPTV

private actor TestProvider: ProviderRepository {
  func validate(_ credentials: Credentials) async throws {}
  func categories(_ credentials: Credentials, kind: ContentKind) async throws -> [IPTVCore.Category]
  { [] }
  func catalog(_ credentials: Credentials, kind: ContentKind) async throws -> [CatalogItem] {
    [CatalogItem(id: "1", name: kind.label, kind: kind)]
  }
  func details(_ credentials: Credentials, item: CatalogItem) async throws -> ContentDetails {
    ContentDetails()
  }
  func guide(_ credentials: Credentials, id: String) async throws -> [Programme] { [] }
  nonisolated func streamURL(_ credentials: Credentials, item: CatalogItem, transportStream: Bool)
    throws -> URL
  { URL(string: "https://example.com/video.mp4")! }
}
private final class TestVault: CredentialStore {
  var value: Credentials?
  func read() throws -> Credentials? { value }
  func save(_ credentials: Credentials) throws { value = credentials }
  func clear() throws { value = nil }
}
@MainActor final class AppModelTests: XCTestCase {
  func testSessionLibraryPlaybackAndSignOut() async throws {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    defer { try? FileManager.default.removeItem(at: root) }
    let vault = TestVault()
    let library = try LibraryStore(directory: root.appendingPathComponent("library"))
    let catalog = try CatalogStore(url: root.appendingPathComponent("cache.sqlite"))
    let defaults = UserDefaults(suiteName: UUID().uuidString)!
    let model = AppModel(
      provider: TestProvider(), vault: vault, libraryStore: library, catalogStore: catalog,
      defaults: defaults)
    await model.restore()
    XCTAssertNil(model.credentials)
    await model.login(
      host: "https://example.com/path", port: "", scheme: "https", username: "u", password: "p")
    let c = try XCTUnwrap(model.credentials)
    let movie = CatalogItem(id: "1", name: "Movie", kind: .movie)
    model.toggle(movie)
    model.savePosition(movie, position: 30, duration: 100)
    model.rememberSearch("movie")
    model.play(movie)
    XCTAssertEqual(model.playback?.item.key, movie.key)
    XCTAssertEqual(model.library.position(movie), 30)
    await model.signOut()
    XCTAssertNil(model.credentials)
    XCTAssertNil(vault.value)
    XCTAssertTrue(model.library.favorites.isEmpty)
    let stored = try await library.load(scope: c.scope)
    XCTAssertTrue(stored.history.isEmpty)
    let cached = try await catalog.page(scope: c.scope, kind: .movie)
    XCTAssertEqual(cached.total, 0)
  }
  func testRealKeychainRoundTrip() throws {
    let store = KeychainCredentialStore(service: "com.iptv.tests." + UUID().uuidString)
    defer { try? store.clear() }
    let c = Credentials(
      baseURL: URL(string: "https://fixture.invalid/")!, username: "fixture", password: "fixture")
    try store.save(c)
    XCTAssertEqual(try store.read(), c)
    try store.clear()
    XCTAssertNil(try store.read())
  }
  func testInvalidLoginDoesNotSaveAccount() async throws {
    let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    defer { try? FileManager.default.removeItem(at: root) }
    let vault = TestVault()
    let model = try AppModel(
      provider: TestProvider(), vault: vault,
      libraryStore: LibraryStore(directory: root.appendingPathComponent("library")),
      catalogStore: CatalogStore(url: root.appendingPathComponent("cache.sqlite")))
    await model.login(host: "", port: "", scheme: "https", username: "u", password: "p")
    XCTAssertNotNil(model.error)
    XCTAssertNil(vault.value)
    XCTAssertFalse(model.busy)
  }
}
