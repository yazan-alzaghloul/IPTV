import CryptoKit
import Foundation

public enum ContentKind: String, Codable, CaseIterable, Sendable {
  case live = "LIVE"
  case movie = "MOVIE"
  case series = "SERIES"
  public var label: String {
    switch self {
    case .live: return "Live TV"
    case .movie: return "Movies"
    case .series: return "Series"
    }
  }
  public var api: String {
    switch self {
    case .live: return "live"
    case .movie: return "vod"
    case .series: return "series"
    }
  }
  public var route: String { self == .movie ? "movie" : api }
}
public struct Credentials: Codable, Equatable, Sendable, CustomStringConvertible {
  public let baseURL: URL
  public let username: String
  public let password: String
  public var description: String { "Credentials([redacted])" }
  public var scope: String {
    SHA256.hash(
      data: Data([baseURL.absoluteString, username, password].joined(separator: "\u{0}").utf8)
    )
    .map { String(format: "%02x", $0) }.joined()
  }
  public init(baseURL: URL, username: String, password: String) {
    self.baseURL = baseURL
    self.username = username
    self.password = password
  }
  public static func normalize(
    host: String, port: String, scheme: String, username: String, password: String
  ) throws -> Self {
    guard !username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
      !password.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    else { throw IPTVError.message("Enter your username and password.") }
    guard ["https", "http"].contains(scheme) else {
      throw IPTVError.message("Choose HTTP or HTTPS.")
    }
    let raw = host.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !raw.isEmpty,
      var url = URLComponents(string: raw.contains("://") ? raw : "\(scheme)://\(raw)"),
      ["https", "http"].contains(url.scheme?.lowercased() ?? ""), let hostname = url.host,
      !hostname.isEmpty,
      !hostname.contains(where: { $0.isWhitespace }), url.user == nil, url.password == nil,
      url.query == nil, url.fragment == nil,
      !url.path.lowercased().hasSuffix(".php"), !url.path.lowercased().hasSuffix(".m3u"),
      !url.path.lowercased().hasSuffix(".m3u8")
    else {
      throw IPTVError.message(
        "Enter the provider base URL without a playlist, login details, query, or fragment.")
    }
    if !port.trimmingCharacters(in: .whitespaces).isEmpty {
      guard let number = Int(port.trimmingCharacters(in: .whitespaces)),
        (1...65535).contains(number)
      else { throw IPTVError.message("Port must be a number between 1 and 65535.") }
      url.port = number
    }
    if let p = url.port, !(1...65535).contains(p) {
      throw IPTVError.message("Port must be a number between 1 and 65535.")
    }
    if !url.path.hasSuffix("/") { url.path += "/" }
    guard let base = url.url else {
      throw IPTVError.message("Enter a valid HTTP or HTTPS provider URL.")
    }
    return Self(
      baseURL: base, username: username.trimmingCharacters(in: .whitespacesAndNewlines),
      password: password)
  }
}
public enum IPTVError: Error, LocalizedError, Sendable {
  case message(String)
  public var errorDescription: String? {
    switch self {
    case .message(let text): return text
    }
  }
}
public struct Category: Codable, Hashable, Identifiable, Sendable {
  public var id: String
  public var name: String
  public init(id: String, name: String) {
    self.id = id
    self.name = name
  }
}
public struct CatalogItem: Codable, Hashable, Identifiable, Sendable {
  public var id: String
  public var name: String
  public var kind: ContentKind
  public var categoryID = ""
  public var image = ""
  public var container = "mp4"
  public var added: Int64 = 0
  public var rating = ""
  public var year = ""
  public var backdrop = ""
  public var genre = ""
  public var language = ""
  public var releaseDate = ""
  public var overview = ""
  public var parentSeriesID: String?
  public var parentSeriesName: String?
  public var season: Int?
  public var episode: Int?
  public var key: String { "\(kind.rawValue):\(id)" }
  public init(id: String, name: String, kind: ContentKind) {
    self.id = id
    self.name = name
    self.kind = kind
  }
}
public struct Episode: Codable, Hashable, Identifiable, Sendable {
  public var item: CatalogItem
  public var season: Int
  public var number: Int
  public var overview = ""
  public var duration: Double?
  public var id: String { item.key }
  public init(item: CatalogItem, season: Int, number: Int) {
    self.item = item
    self.season = season
    self.number = number
  }
}
public struct ContentDetails: Codable, Sendable {
  public var overview = "", cast = "", director = "", rating = "", backdrop = "", genre = "",
    year = "", duration = ""
  public var language = "", subtitles = "", classification = "", technical = "", poster = "",
    container = "mp4"
  public var trailer: URL?
  public var episodes: [Episode] = []
  public init() {}
}
public struct Programme: Codable, Hashable, Identifiable, Sendable {
  public let title: String
  public let start: Double
  public let end: Double
  public let overview: String
  public var id: String { "\(start):\(end):\(title)" }
  public var progress: Double {
    min(1, max(0, (Date().timeIntervalSince1970 - start) / (end - start)))
  }
  public func isCurrent(at now: Double = Date().timeIntervalSince1970) -> Bool {
    start <= now && now < end
  }
  public init(title: String, start: Double, end: Double, overview: String = "") {
    self.title = title
    self.start = start
    self.end = end
    self.overview = overview
  }
}
public struct WatchRecord: Codable, Sendable {
  public var item: CatalogItem
  public var position: Double
  public var duration: Double
  public var updatedAt: Date
  public var completed: Bool
}
public enum CatalogSort: String, Codable, CaseIterable, Sendable {
  case provider = "Provider order"
  case title = "Title A–Z"
  case added = "Date added"
  case release = "Release date"
  case rating = "Provider rating"
}
public struct CatalogFilter: Equatable, Sendable {
  public var query = "", category = "", year = "", language = "", genre = ""
  public var ratedOnly = false
  public var sort: CatalogSort = .added
  public init() {}
}
public struct CatalogPage: Sendable {
  public var items: [CatalogItem]
  public var total: Int
  public var offset: Int
}
public struct CatalogFacets: Sendable {
  public var years: [String] = [], languages: [String] = [], genres: [String] = []
  public var hasRatings = false
  public init() {}
}
public struct Appearance: Codable, Equatable, Sendable {
  public var theme = "dark", accent = "violet"
  public var fontScale: Double = 1
  public var highContrast = false, motion = false, heroRotation = false
  public var landing = "HOME"
  public var hiddenRows: Set<String> = []
  public init() {}
  public static func imported(_ data: Data) throws -> Self {
    guard data.count <= 32_768 else {
      throw IPTVError.message("This appearance file is too large.")
    }
    let value = try JSONDecoder().decode(Self.self, from: data)
    guard ["dark", "light"].contains(value.theme), ["violet", "cyan"].contains(value.accent),
      value.fontScale.isFinite, (0.85...1.4).contains(value.fontScale),
      ["HOME", "LIVE", "MOVIES", "SERIES"].contains(value.landing), value.hiddenRows.count <= 6
    else {
      throw IPTVError.message("This is not a supported appearance settings file.")
    }
    return value
  }
}
public struct RetryBudget: Sendable {
  public private(set) var attempts = 0
  public init() {}
  public mutating func nextDelay() -> Double? {
    guard attempts < 2 else { return nil }
    defer { attempts += 1 }
    return pow(2, Double(attempts))
  }
  public mutating func reset() { attempts = 0 }
}
public enum MediaRules {
  public static func trailer(_ input: String) -> URL? {
    let value = input.trimmingCharacters(in: .whitespacesAndNewlines)
    if value.range(of: "^[A-Za-z0-9_-]{11}$", options: .regularExpression) != nil {
      return URL(string: "https://www.youtube.com/watch?v=\(value)")
    }
    guard let url = URL(string: value), ["http", "https"].contains(url.scheme?.lowercased() ?? ""),
      url.host != nil, url.user == nil, url.password == nil
    else { return nil }
    return url
  }
  public static func duration(_ input: String) -> Double? {
    let parts = input.split(separator: ":", omittingEmptySubsequences: false).compactMap {
      Double($0)
    }
    guard (2...3).contains(parts.count),
      parts.count == input.split(separator: ":", omittingEmptySubsequences: false).count,
      parts.allSatisfy({ $0.isFinite && $0 >= 0 }), parts.dropFirst().allSatisfy({ $0 < 60 })
    else { return nil }
    let result = min(604800, parts.reduce(0) { $0 * 60 + $1 })
    return result > 0 ? result : nil
  }
  public static func continuation(
    seriesID: String, episodes: [Episode], history: [WatchRecord], watched: Set<String>
  ) -> Episode? {
    let sorted = episodes.sorted { ($0.season, $0.number) < ($1.season, $1.number) }
    guard
      let latest = history.first(where: { record in
        record.item.parentSeriesID == seriesID
          && sorted.contains(where: { $0.item.key == record.item.key })
      }),
      let index = sorted.firstIndex(where: { $0.item.key == latest.item.key })
    else { return sorted.first { !watched.contains($0.item.key) } }
    if latest.completed {
      return sorted.dropFirst(index + 1).first { !watched.contains($0.item.key) }
    }
    return sorted[index]
  }
  public static func recommendations(
    candidates: [CatalogItem], history: [WatchRecord], favorites: [CatalogItem]
  ) -> [CatalogItem] {
    let signals = history.prefix(30).map(\.item) + favorites
    let known = Set(signals.map(\.key))
    let genres = Set(signals.flatMap { genreParts($0.genre) })
    let groups = Set(
      signals.filter { !$0.categoryID.isEmpty }.map { "\($0.kind.rawValue):\($0.categoryID)" })
    var scored: [(item: CatalogItem, score: Int)] = []
    for item in candidates where !known.contains(item.key) {
      let overlap = genreParts(item.genre).filter { genres.contains($0) }.count
      let group = "\(item.kind.rawValue):\(item.categoryID)"
      let score = overlap * 2 + (groups.contains(group) ? 1 : 0)
      if score > 0 { scored.append((item, score)) }
    }
    scored.sort { a, b in a.score == b.score ? a.item.name < b.item.name : a.score > b.score }
    return scored.prefix(12).map { $0.item }
  }
  public static func genreParts(_ genre: String) -> [String] {
    genre.lowercased().components(separatedBy: CharacterSet(charactersIn: ",/|")).map {
      $0.trimmingCharacters(in: .whitespaces)
    }.filter { !$0.isEmpty }
  }
}
