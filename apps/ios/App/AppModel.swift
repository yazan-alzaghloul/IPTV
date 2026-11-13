import IPTVCore
import SwiftUI

func localized(_ key: String) -> String { NSLocalizedString(key, comment: "") }

enum Section: String, CaseIterable, Identifiable {
  case home = "HOME"
  case live = "LIVE"
  case movies = "MOVIES"
  case series = "SERIES"
  case favorites = "FAVORITES"
  case search = "SEARCH"
  case settings = "SETTINGS"
  var id: String { rawValue }
  var title: String {
    switch self {
    case .home: return "Home"
    case .live: return "Live TV"
    case .movies: return "Movies"
    case .series: return "Series"
    case .favorites: return "Favorites"
    case .search: return "Search"
    case .settings: return "Settings"
    }
  }
  var icon: String {
    switch self {
    case .home: return "house.fill"
    case .live: return "tv.fill"
    case .movies: return "film.fill"
    case .series: return "rectangle.stack.fill"
    case .favorites: return "heart.fill"
    case .search: return "magnifyingglass"
    case .settings: return "gearshape.fill"
    }
  }
  var kind: ContentKind? {
    switch self {
    case .live: return .live
    case .movies: return .movie
    case .series: return .series
    default: return nil
    }
  }
}
struct PlaybackRequest: Identifiable {
  let id = UUID()
  let item: CatalogItem
  let queue: [CatalogItem]
  let restart: Bool
  let credentials: Credentials
}
@MainActor final class AppModel: ObservableObject {
  let provider: any ProviderRepository
  let catalogStore: CatalogStore
  private let vault: any CredentialStore
  private let libraryStore: LibraryStore
  private let defaults: UserDefaults
  @Published private(set) var credentials: Credentials?
  @Published private(set) var restoring = true
  @Published private(set) var busy = false
  @Published var error: String?
  @Published var section: Section = .home
  @Published private(set) var library = LibrarySnapshot()
  @Published private(set) var catalogs: [ContentKind: [CatalogItem]] = [:]
  @Published private(set) var categories: [ContentKind: [IPTVCore.Category]] = [:]
  @Published private(set) var revision = 0
  @Published var playback: PlaybackRequest?
  @Published var appearance: Appearance {
    didSet {
      if let data = try? JSONEncoder().encode(appearance) {
        defaults.set(data, forKey: "appearance")
      }
    }
  }
  @Published var autoplay: Bool { didSet { defaults.set(autoplay, forKey: "autoplay") } }
  @Published var completion: Int { didSet { defaults.set(completion, forKey: "completion") } }
  var browserFilters: [String: CatalogFilter] = [:]
  var browserPages: [String: Int] = [:]
  var browserLists: [String: Bool] = [:]
  var browserKinds: [String: ContentKind] = [:]
  private var sessionID = UUID()
  private var loadTask: Task<Void, Never>?
  private var saveTask: Task<Void, Never>?
  private var loadedAt: [ContentKind: Date] = [:]
  private var detailCache: [String: ContentDetails] = [:]
  private var detailOrder: [String] = []
  private var guideCache: [String: (Date, [Programme])] = [:]

  init(
    provider: any ProviderRepository, vault: any CredentialStore, libraryStore: LibraryStore,
    catalogStore: CatalogStore, defaults: UserDefaults = .standard
  ) {
    self.provider = provider
    self.vault = vault
    self.libraryStore = libraryStore
    self.catalogStore = catalogStore
    self.defaults = defaults
    appearance =
      defaults.data(forKey: "appearance").flatMap { try? Appearance.imported($0) } ?? Appearance()
    autoplay = defaults.bool(forKey: "autoplay")
    completion =
      defaults.object(forKey: "completion") == nil
      ? 95 : min(100, max(80, defaults.integer(forKey: "completion")))
    section = Section(rawValue: appearance.landing) ?? .home
  }
  static func production() throws -> AppModel {
    let root = try FileManager.default.url(
      for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true
    ).appendingPathComponent("IPTV", isDirectory: true)
    return try AppModel(
      provider: XtreamRepository(), vault: KeychainCredentialStore(),
      libraryStore: LibraryStore(directory: root.appendingPathComponent("Library")),
      catalogStore: CatalogStore(url: root.appendingPathComponent("Cache/catalog.sqlite")))
  }
  func restore() async {
    guard restoring else { return }
    defer { restoring = false }
    do {
      if let saved = try vault.read() { try await activate(saved) }
    } catch { self.error = localized("Saved login could not be unlocked. Please sign in again.") }
  }
  func login(host: String, port: String, scheme: String, username: String, password: String) async {
    guard !busy else { return }
    busy = true
    error = nil
    do {
      let c = try Credentials.normalize(
        host: host, port: port, scheme: scheme, username: username, password: password)
      try await provider.validate(c)
      try vault.save(c)
      try await activate(c)
    } catch is CancellationError {} catch { self.error = friendly(error) }
    busy = false
  }
  private func activate(_ c: Credentials) async throws {
    let snapshot = try await libraryStore.load(scope: c.scope)
    sessionID = UUID()
    credentials = c
    library = snapshot
    section = Section(rawValue: appearance.landing) ?? .home
    // Restore disk pages immediately; refresh failures retain cached content.
    for kind in ContentKind.allCases {
      catalogs[kind] = try await catalogStore.page(scope: c.scope, kind: kind, limit: 100).items
      categories[kind] = try await catalogStore.categories(scope: c.scope, kind: kind)
    }
    revision += 1
    refresh()
  }
  func refresh(force: Bool = true) {
    guard let c = credentials else { return }
    loadTask?.cancel()
    let token = sessionID
    let needed = ContentKind.allCases.filter {
      force || loadedAt[$0].map { Date().timeIntervalSince($0) > 300 } != false
    }
    guard !needed.isEmpty else { return }
    busy = true
    error = nil
    loadTask = Task {
      do {
        try await provider.validate(c)
        // Sequential kinds bound connections to two (catalog + categories).
        for kind in needed {
          try Task.checkCancellation()
          do {
            async let groupsResult = optionalCategories(c, kind: kind)
            let items = try await provider.catalog(c, kind: kind)
            let groups = await groupsResult
            try Task.checkCancellation()
            guard sessionID == token else { return }
            try await catalogStore.replace(
              scope: c.scope, kind: kind, items: items, categories: groups)
            guard sessionID == token, !Task.isCancelled else { return }
            catalogs[kind] = items
            categories[kind] = groups
            loadedAt[kind] = Date()
            revision += 1
          } catch is CancellationError { throw CancellationError() } catch {
            if sessionID == token { self.error = friendly(error) }
          }
        }
      } catch is CancellationError { return } catch {
        if sessionID == token { self.error = friendly(error) }
      }
      if sessionID == token { busy = false }
    }
  }
  private func optionalCategories(_ c: Credentials, kind: ContentKind) async -> [IPTVCore.Category]
  { (try? await provider.categories(c, kind: kind)) ?? [] }
  func details(_ item: CatalogItem) async throws -> ContentDetails {
    guard let c = credentials else { throw IPTVError.message("Please sign in again.") }
    if let cached = detailCache[item.key] { return cached }
    let token = sessionID
    let result = try await provider.details(c, item: item)
    try Task.checkCancellation()
    guard token == sessionID else { throw CancellationError() }
    detailCache[item.key] = result
    detailOrder.removeAll { $0 == item.key }
    detailOrder.append(item.key)
    if detailOrder.count > 12 { detailCache.removeValue(forKey: detailOrder.removeFirst()) }
    return result
  }
  func guide(_ item: CatalogItem, force: Bool = false) async throws -> [Programme] {
    guard let c = credentials else { throw IPTVError.message("Please sign in again.") }
    if !force, let (date, programmes) = guideCache[item.key], Date().timeIntervalSince(date) < 120 {
      return programmes
    }
    let token = sessionID
    let result = try await provider.guide(c, id: item.id)
    try Task.checkCancellation()
    guard token == sessionID else { throw CancellationError() }
    guideCache[item.key] = (Date(), result)
    if guideCache.count > 24, let oldest = guideCache.min(by: { $0.value.0 < $1.value.0 })?.key {
      guideCache.removeValue(forKey: oldest)
    }
    return result
  }
  func parent(_ item: CatalogItem) -> CatalogItem {
    guard let id = item.parentSeriesID else { return item }
    if let parent = catalogs[.series]?.first(where: { $0.id == id }) { return parent }
    var result = item
    result.id = id
    result.name = item.parentSeriesName ?? item.name
    result.parentSeriesID = nil
    result.parentSeriesName = nil
    result.season = nil
    result.episode = nil
    return result
  }
  func play(_ item: CatalogItem, details: ContentDetails? = nil, restart: Bool = false) {
    guard let c = credentials else { return }
    var playable = item
    if item.kind == .movie {
      playable.container = details?.container ?? detailCache[item.key]?.container ?? item.container
    }
    let queue =
      item.kind == .live
      ? catalogs[.live] ?? [item]
      : details?.episodes.map(\.item) ?? item.parentSeriesID.flatMap {
        detailCache["SERIES:\($0)"]?.episodes.map(\.item)
      } ?? []
    playback = PlaybackRequest(item: playable, queue: queue, restart: restart, credentials: c)
  }
  func isFavorite(_ item: CatalogItem) -> Bool { library.favorites.contains { $0.key == item.key } }
  func toggle(_ item: CatalogItem) {
    library.toggle(item)
    persist()
  }
  func mark(_ items: [CatalogItem], watched: Bool) {
    library.mark(items, watched: watched)
    persist()
  }
  func rememberSearch(_ query: String) {
    library.rememberSearch(query)
    persist()
  }
  func selectSeason(_ season: Int, seriesID: String) {
    library.seasons[seriesID] = season
    persist()
  }
  func removeHistory(_ item: CatalogItem) {
    library.removeHistory(item)
    persist()
  }
  func clearHistory() {
    library.clearHistory()
    persist()
  }
  func savePosition(_ item: CatalogItem, position: Double, duration: Double) {
    library.savePosition(
      item, position: position, duration: duration, completionPercent: completion)
    persist()
  }
  private func persist() {
    guard let c = credentials else { return }
    let snapshot = library
    let previous = saveTask
    let token = sessionID
    saveTask = Task {
      await previous?.value
      do { try await libraryStore.save(snapshot, scope: c.scope) } catch {
        if token == sessionID {
          self.error = localized("Could not save viewing progress. Check device storage.")
        }
      }
    }
  }
  func signOut() async {
    guard let c = credentials else { return }
    loadTask?.cancel()
    await loadTask?.value
    await saveTask?.value
    do {
      try await catalogStore.clear(scope: c.scope)
      try await libraryStore.clear(scope: c.scope)
      await ArtworkCache.shared.clear()
      try vault.clear()
      sessionID = UUID()
      credentials = nil
      library = LibrarySnapshot()
      catalogs = [:]
      categories = [:]
      detailCache = [:]
      detailOrder = []
      guideCache = [:]
      loadedAt = [:]
      playback = nil
      busy = false
      error = nil
      section = .home
      browserFilters = [:]
      browserPages = [:]
      browserLists = [:]
      browserKinds = [:]
    } catch { self.error = localized("Could not clear saved data. Try signing out again.") }
  }
}
func friendly(_ error: Error) -> String {
  if let error = error as? IPTVError { return localized(error.localizedDescription) }
  return localized("Could not complete this request. Check your connection and try again.")
}
