import Foundation
import Security

public protocol CredentialStore {
  func read() throws -> Credentials?
  func save(_ credentials: Credentials) throws
  func clear() throws
}
public struct KeychainCredentialStore: CredentialStore {
  private let service: String
  public init(service: String = "com.iptv.ios.account") { self.service = service }
  private var query: [String: Any] {
    [
      kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
      kSecAttrAccount as String: "provider",
    ]
  }
  public func read() throws -> Credentials? {
    var q = query
    q[kSecReturnData as String] = true
    q[kSecMatchLimit as String] = kSecMatchLimitOne
    var result: CFTypeRef?
    let status = SecItemCopyMatching(q as CFDictionary, &result)
    if status == errSecItemNotFound { return nil }
    guard status == errSecSuccess, let data = result as? Data else {
      throw IPTVError.message("Saved login could not be unlocked. Please sign in again.")
    }
    return try JSONDecoder().decode(Credentials.self, from: data)
  }
  public func save(_ credentials: Credentials) throws {
    let data = try JSONEncoder().encode(credentials)
    let update =
      [
        kSecValueData as String: data,
        kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
      ] as [String: Any]
    var status = SecItemUpdate(query as CFDictionary, update as CFDictionary)
    if status == errSecItemNotFound {
      var q = query
      for (key, value) in update { q[key] = value }
      status = SecItemAdd(q as CFDictionary, nil)
    }
    guard status == errSecSuccess else {
      throw IPTVError.message("Account could not be saved securely.")
    }
  }
  public func clear() throws {
    let status = SecItemDelete(query as CFDictionary)
    guard status == errSecSuccess || status == errSecItemNotFound else {
      throw IPTVError.message("Saved account could not be cleared.")
    }
  }
}
public struct LibrarySnapshot: Codable, Sendable {
  public var favorites: [CatalogItem] = []
  public var history: [WatchRecord] = []
  public var watched: Set<String> = []
  public var seasons: [String: Int] = [:]
  public var searches: [String] = []
  public var positions: [String: Double] = [:]
  public init() {}
  public mutating func toggle(_ item: CatalogItem) {
    if favorites.contains(where: { $0.key == item.key }) {
      favorites.removeAll { $0.key == item.key }
    } else {
      favorites.append(item)
    }
  }
  public func position(_ item: CatalogItem) -> Double {
    guard item.kind != .live else { return 0 }
    if let position = positions[item.key] { return position }
    guard let record = history.first(where: { $0.item.key == item.key }), !record.completed else {
      return 0
    }
    return record.position
  }
  public mutating func savePosition(
    _ item: CatalogItem, position: Double, duration: Double, completionPercent: Int
  ) {
    let position = position.isFinite ? max(0, position) : 0
    let duration = duration.isFinite ? max(0, duration) : 0
    let completed =
      item.kind != .live && duration > 0
      && position / duration >= Double(min(100, max(80, completionPercent))) / 100
    let record = WatchRecord(
      item: item, position: item.kind == .live ? 0 : position, duration: duration,
      updatedAt: Date(), completed: completed)
    history.removeAll { $0.item.key == item.key }
    history.insert(record, at: 0)
    history = Array(history.prefix(100))
    if completed { watched.insert(item.key) }
    if item.kind != .live { positions[item.key] = completed ? 0 : position }
  }
  public mutating func mark(_ items: [CatalogItem], watched value: Bool) {
    let keys = Set(items.map(\.key))
    if value { watched.formUnion(keys) } else { watched.subtract(keys) }
    for key in keys { positions[key] = 0 }
    for index in history.indices where keys.contains(history[index].item.key) {
      history[index].completed = value
      history[index].position = 0
    }
  }
  public mutating func removeHistory(_ item: CatalogItem) {
    history.removeAll { $0.item.key == item.key }
    positions[item.key] = 0
  }
  public mutating func clearHistory() {
    for record in history { positions[record.item.key] = 0 }
    history = []
  }
  private enum CodingKeys: String, CodingKey {
    case favorites, history, watched, seasons, searches, positions
  }
  public init(from decoder: Decoder) throws {
    let c = try decoder.container(keyedBy: CodingKeys.self)
    favorites = try c.decodeIfPresent([CatalogItem].self, forKey: .favorites) ?? []
    history = try c.decodeIfPresent([WatchRecord].self, forKey: .history) ?? []
    watched = try c.decodeIfPresent(Set<String>.self, forKey: .watched) ?? []
    seasons = try c.decodeIfPresent([String: Int].self, forKey: .seasons) ?? [:]
    searches = try c.decodeIfPresent([String].self, forKey: .searches) ?? []
    positions = try c.decodeIfPresent([String: Double].self, forKey: .positions) ?? [:]
  }
  public mutating func rememberSearch(_ query: String) {
    let value = String(query.trimmingCharacters(in: .whitespacesAndNewlines).prefix(120))
    guard !value.isEmpty else { return }
    searches.removeAll { $0.caseInsensitiveCompare(value) == .orderedSame }
    searches.insert(value, at: 0)
    searches = Array(searches.prefix(10))
  }
}
public actor LibraryStore {
  private let directory: URL
  public init(directory: URL) throws {
    self.directory = directory
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    var url = directory
    var values = URLResourceValues()
    values.isExcludedFromBackup = true
    try url.setResourceValues(values)
  }
  private func file(_ scope: String) throws -> URL {
    guard scope.range(of: "^[a-f0-9]{64}$", options: .regularExpression) != nil else {
      throw IPTVError.message("Invalid account scope.")
    }
    return directory.appendingPathComponent("\(scope).json")
  }
  public func load(scope: String) throws -> LibrarySnapshot {
    let url = try file(scope)
    guard FileManager.default.fileExists(atPath: url.path) else { return LibrarySnapshot() }
    return try JSONDecoder().decode(LibrarySnapshot.self, from: Data(contentsOf: url))
  }
  public func save(_ snapshot: LibrarySnapshot, scope: String) throws {
    let data = try JSONEncoder().encode(snapshot)
    #if os(iOS)
      try data.write(
        to: file(scope), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    #else
      try data.write(to: file(scope), options: .atomic)
    #endif
  }
  public func clear(scope: String) throws {
    let url = try file(scope)
    if FileManager.default.fileExists(atPath: url.path) {
      try FileManager.default.removeItem(at: url)
    }
  }
}
